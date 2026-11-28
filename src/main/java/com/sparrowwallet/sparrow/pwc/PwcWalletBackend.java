package com.sparrowwallet.sparrow.pwc;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.crypto.Bip322;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.*;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.psbt.PSBTInput;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.net.LegacyUtxoChecker;
import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.NodeEntry;
import com.sparrowwallet.sparrow.wallet.WalletForm;
import org.bitcoinpurity.pwc.provider.*;
import org.bitcoinpurity.pwc.provider.ScriptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * The open wallets of the application, as the PWC Provider sees them. Methods read only the published {@link PwcView}
 * and never wait for the user or the JavaFX thread (REQ-017).
 */
public class PwcWalletBackend implements WalletBackend {
    private static final Logger log = LoggerFactory.getLogger(PwcWalletBackend.class);

    static final String DEFAULT_ADDRESS_LABEL = "PWC";
    /** How long a decrypted wallet or a device signature kept for an approved prompt may wait for its backend call. */
    public static final Duration APPROVAL_LIFETIME = Duration.ofMinutes(2);
    /** How long a replay clearance from a signing prompt covers the broadcast of that transaction. */
    public static final Duration REPLAY_CLEARANCE_LIFETIME = Duration.ofMinutes(30);
    static final Duration BROADCAST_TIMEOUT = Duration.ofSeconds(30);

    private static final List<String> READS = List.of("account.getBalance", "address.getReceiveAddress", "address.create", "address.list",
            "utxo.list", "transaction.list", "transaction.get");
    private static final List<String> CREATE = List.of("psbt.create", "psbt.releaseReservation");

    private enum Signer { SOFTWARE, DEVICE, NONE }

    private record AccountEntry(String accountId, String walletId, PwcView.Form form, Signer signer) {
        Wallet wallet() {
            return form.wallet();
        }

        boolean segwit() {
            return wallet().getScriptType() == com.sparrowwallet.drongo.protocol.ScriptType.P2WPKH;
        }

        List<String> methods() {
            List<String> methods = new ArrayList<>(READS);
            if(segwit()) {
                methods.addAll(CREATE);
                if(signer != Signer.NONE) {
                    methods.add("psbt.sign");
                }
            }
            if(signer != Signer.NONE || segwit()) {
                methods.add("transaction.broadcast");
            }
            if(signer == Signer.SOFTWARE) {
                methods.add("message.sign");
            }
            return methods;
        }
    }

    private record WalletEntry(String walletId, Wallet master, WalletState state, List<AccountEntry> accounts) {}

    private record Entries(PwcView view, List<WalletEntry> wallets) {}

    /** What an approved prompt left for its backend call: a decrypted wallet or a device-signed PSBT, for one subject. */
    private record Approval(String subject, Instant expires, Wallet decrypted, PSBT deviceSigned) {
        void clear() {
            if(decrypted != null) {
                decrypted.clearPrivate();
            }
        }
    }

    private final PwcHost host;
    private final PwcIds ids;
    private final Clock clock;
    private final Duration broadcastTimeout;
    private final Map<String, Approval> approvals = new HashMap<>();
    private volatile boolean closed;
    private final Map<String, Instant> replayClearances = new ConcurrentHashMap<>();
    private final Map<String, Set<Integer>> handedOut = new ConcurrentHashMap<>();
    private final ExecutorService broadcaster = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "pwc-broadcast");
        thread.setDaemon(true);
        return thread;
    });
    private volatile WeakReference<Entries> entries = new WeakReference<>(null);

    public PwcWalletBackend(PwcHost host, PwcIds ids) {
        this(host, ids, Clock.systemUTC(), BROADCAST_TIMEOUT);
    }

    PwcWalletBackend(PwcHost host, PwcIds ids, Clock clock, Duration broadcastTimeout) {
        this.host = host;
        this.ids = ids;
        this.clock = clock;
        this.broadcastTimeout = broadcastTimeout;
    }

    public PwcIds getIds() {
        return ids;
    }

    /** The subject of a PSBT approval: the exact PSBT the SDK will pass to {@link #signPsbt} and its inputs to sign. */
    public static String psbtSubject(byte[] psbt, List<Integer> inputs) {
        return "psbt:" + Utils.bytesToHex(Sha256Hash.hash(psbt)) + ":" + inputs;
    }

    public static String messageSubject(String address, String message, MessageFormat format) {
        return "message:" + format + ":" + address + ":" + Utils.bytesToHex(Sha256Hash.hash(message.getBytes(StandardCharsets.UTF_8)));
    }

    private synchronized void keep(String accountId, Approval approval) {
        if(closed) {
            approval.clear();
            return;
        }
        Approval previous = approvals.put(accountId, approval);
        if(previous != null) {
            previous.clear();
        }
    }

    /** Hands over a decrypted copy of an encrypted account for the one signature the user has just approved. */
    public void unlocked(String accountId, String subject, Wallet decrypted) {
        keep(accountId, new Approval(subject, clock.instant().plus(APPROVAL_LIFETIME), decrypted, null));
    }

    /** Hands over the PSBT a device signed in the prompt flow for the one signature the user has just approved. */
    public void deviceSigned(String accountId, String subject, PSBT signed) {
        keep(accountId, new Approval(subject, clock.instant().plus(APPROVAL_LIFETIME), null, signed));
    }

    public synchronized void discardApproval(String accountId) {
        Approval previous = approvals.remove(accountId);
        if(previous != null) {
            previous.clear();
        }
    }

    synchronized void expireApprovals() {
        Instant now = clock.instant();
        approvals.values().removeIf(approval -> {
            if(now.isBefore(approval.expires())) {
                return false;
            }
            approval.clear();
            return true;
        });
    }

    boolean isClosed() {
        return closed;
    }

    synchronized void close() {
        closed = true;
        approvals.values().forEach(Approval::clear);
        approvals.clear();
        replayClearances.clear();
        entries.clear();
        broadcaster.shutdownNow();
    }

    /** Takes the account's approval once; one for another subject, or expired, is cleared and refused. */
    private synchronized Approval take(String accountId, String subject) throws PwcException {
        Approval approval = approvals.remove(accountId);
        if(approval == null) {
            return null;
        }
        if(!approval.subject().equals(subject) || !clock.instant().isBefore(approval.expires())) {
            approval.clear();
            throw new PwcException(ErrorCode.SIGNER_UNAVAILABLE, "The approval given in the wallet does not cover this request");
        }
        return approval;
    }

    /** The parts of a transaction that its signatures fix: version, lock time, inputs with sequences and outputs. */
    static String replayKey(Transaction transaction) {
        StringBuilder key = new StringBuilder().append(transaction.getVersion()).append('/').append(transaction.getLocktime());
        for(TransactionInput input : transaction.getInputs()) {
            key.append("/i:").append(input.getOutpoint().getHash()).append(':').append(input.getOutpoint().getIndex()).append(':').append(input.getSequenceNumber());
        }
        for(TransactionOutput output : transaction.getOutputs()) {
            key.append("/o:").append(output.getValue()).append(':').append(Utils.bytesToHex(output.getScriptBytes()));
        }
        return key.toString();
    }

    /** Records that the signing prompt found no shared input or that the user acknowledged the replay risk of this transaction (REQ-011). */
    public synchronized void replayCleared(Transaction transaction) {
        if(closed) {
            return;
        }
        Instant now = clock.instant();
        replayClearances.values().removeIf(expires -> now.isAfter(expires));
        replayClearances.put(replayKey(transaction), now.plus(REPLAY_CLEARANCE_LIFETIME));
    }

    private boolean isReplayCleared(String key) {
        Instant expires = replayClearances.get(key);
        return expires != null && !clock.instant().isAfter(expires);
    }

    static String network() {
        return Network.get().getName();
    }

    static boolean isSupported(Wallet wallet) {
        com.sparrowwallet.drongo.protocol.ScriptType scriptType = wallet.getScriptType();
        return wallet.getPolicyType() == PolicyType.SINGLE_HD && wallet.getKeystores().size() == 1 && !wallet.isNested()
                && (scriptType == com.sparrowwallet.drongo.protocol.ScriptType.P2WPKH || scriptType == com.sparrowwallet.drongo.protocol.ScriptType.P2PKH);
    }

    private static Signer signer(Wallet wallet) {
        Keystore keystore = wallet.getKeystores().getFirst();
        if(keystore.hasPrivateKey()) {
            return Signer.SOFTWARE;
        }
        if(keystore.getSource() == KeystoreSource.HW_USB && wallet.getScriptType() == com.sparrowwallet.drongo.protocol.ScriptType.P2WPKH) {
            return Signer.DEVICE;
        }
        return Signer.NONE;
    }

    private static List<WalletEntry> describe(PwcView view, PwcIds ids) {
        Map<Wallet, List<PwcView.Form>> byMaster = new IdentityHashMap<>();
        List<Wallet> masters = new ArrayList<>();
        for(PwcView.Form form : view.forms()) {
            byMaster.computeIfAbsent(form.master(), master -> {
                masters.add(master);
                return new ArrayList<>();
            }).add(form);
        }

        List<WalletEntry> entries = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for(Wallet master : masters) {
            List<PwcView.Form> forms = byMaster.get(master);
            String walletId;
            List<AccountEntry> accounts = new ArrayList<>();
            try {
                walletId = ids.walletId(master);
                for(PwcView.Form form : forms) {
                    if(isSupported(form.wallet())) {
                        String accountId = ids.accountId(form.wallet());
                        if(seen.add(accountId)) {
                            accounts.add(new AccountEntry(accountId, walletId, form, signer(form.wallet())));
                        }
                    }
                }
            } catch(RuntimeException e) {
                log.debug("Wallet " + master.getName() + " cannot be described for PWC", e);
                continue;
            }
            if(accounts.isEmpty()) {
                continue;
            }
            boolean locked = forms.stream().anyMatch(PwcView.Form::locked);
            boolean syncing = forms.stream().anyMatch(PwcView.Form::syncing);
            WalletState state = locked ? WalletState.LOCKED : (syncing ? WalletState.SYNCING : WalletState.READY);
            entries.add(new WalletEntry(walletId, master, state, accounts));
        }
        return List.copyOf(entries);
    }

    /** The entries of the current view, derived once per published view. */
    private Entries entries() {
        PwcView view = closed ? PwcView.EMPTY : host.view();
        Entries current = entries.get();
        if(current == null || current.view() != view) {
            current = new Entries(view, describe(view, ids));
            entries = new WeakReference<>(current);
        }
        return current;
    }

    private interface Call<T> {
        T call() throws Exception;
    }

    /** No exception other than {@link PwcException} reaches the SDK. */
    private static <T> T guard(String method, Call<T> call) throws PwcException {
        try {
            return call.call();
        } catch(PwcException e) {
            throw e;
        } catch(Throwable e) {
            log.error("PWC backend error in " + method, e);
            throw new PwcException(ErrorCode.INTERNAL_ERROR, "The wallet could not complete the request");
        }
    }

    /** For the methods that declare no error: nothing is returned when the request cannot be answered. */
    private static <T> T quietly(String method, Call<T> call, T otherwise) {
        try {
            return call.call();
        } catch(PwcException e) {
            return otherwise;
        } catch(Throwable e) {
            log.error("PWC backend error in " + method, e);
            return otherwise;
        }
    }

    private interface AccountAction<T> {
        T apply(AccountEntry account, WalletEntry wallet, PwcView view) throws Exception;
    }

    private <T> T withAccount(String accountId, AccountAction<T> action) throws Exception {
        Entries current = entries();
        for(WalletEntry wallet : current.wallets()) {
            for(AccountEntry account : wallet.accounts()) {
                if(account.accountId().equals(accountId)) {
                    if(wallet.state() == WalletState.LOCKED) {
                        throw new PwcException(ErrorCode.WALLET_LOCKED, "The wallet is locked");
                    }
                    return action.apply(account, wallet, current.view());
                }
            }
        }
        throw new PwcException(ErrorCode.ACCOUNT_NOT_FOUND, "No such account");
    }

    private Optional<AccountEntry> find(String accountId) {
        return entries().wallets().stream().flatMap(wallet -> wallet.accounts().stream()).filter(account -> account.accountId().equals(accountId)).findFirst();
    }

    /** The open wallet form of an account; JavaFX thread only. */
    WalletForm walletForm(String accountId) {
        WalletForm form = find(accountId).map(account -> account.form().live()).orElse(null);
        return form != null && host.openWalletForms().contains(form) ? form : null;
    }

    /** Whether the account signs with a USB device, through the device dialog in the prompt flow. */
    boolean signsWithDevice(String accountId) {
        return find(accountId).map(account -> account.signer() == Signer.DEVICE).orElse(false);
    }

    @Override
    public List<WalletInfo> wallets() {
        return quietly("wallets", () -> entries().wallets().stream().map(wallet -> new WalletInfo(wallet.walletId(), wallet.master().getName(), network(),
                kind(wallet.master()), wallet.state(), wallet.state() == WalletState.LOCKED ? List.of() : wallet.accounts().stream().map(AccountEntry::accountId).toList())).toList(),
                List.of());
    }

    private static WalletKind kind(Wallet master) {
        if(!isSupported(master)) {
            return WalletKind.OTHER;
        }
        return switch(signer(master)) {
            case SOFTWARE -> WalletKind.SOFTWARE;
            case DEVICE -> WalletKind.HARDWARE;
            case NONE -> master.getKeystores().getFirst().getSource() == KeystoreSource.SW_WATCH ? WalletKind.WATCH_ONLY : WalletKind.OTHER;
        };
    }

    @Override
    public Optional<AccountInfo> account(String accountId) {
        return quietly("account", () -> find(accountId).map(account -> new AccountInfo(accountId, account.walletId(), account.wallet().getDisplayName(), network(),
                account.signer() != Signer.NONE, account.segwit() ? ScriptType.P2WPKH : ScriptType.P2PKH, account.methods())), Optional.empty());
    }

    private static Integer tip(PwcView view, Wallet wallet) {
        return view.height() != null ? view.height() : wallet.getStoredBlockHeight();
    }

    private static int confirmations(BlockTransactionHash reference, Integer tip) {
        if(reference.getHeight() <= 0) {
            return 0;
        }
        return tip == null ? 1 : Math.max(1, tip - reference.getHeight() + 1);
    }

    private static OutPoint outPoint(BlockTransactionHashIndex reference) {
        return new OutPoint(reference.getHashAsString(), (int)reference.getIndex());
    }

    /** An output the account still holds after the pending projection. */
    private static boolean isEffective(BlockTransactionHashIndex reference) {
        return reference.getSpentBy() == null;
    }

    private static List<UtxoRestriction> restrictions(PwcView view, Wallet wallet, BlockTransactionHashIndex reference, Set<OutPoint> reserved) {
        List<UtxoRestriction> restrictions = new ArrayList<>();
        if(!new CoinbaseTxoFilter(wallet).isEligible(reference)) {
            restrictions.add(UtxoRestriction.IMMATURE);
        }
        if(reference.getStatus() == Status.FROZEN) {
            restrictions.add(UtxoRestriction.FROZEN);
        }
        if(reserved.contains(outPoint(reference))) {
            restrictions.add(UtxoRestriction.LOCKED);
        }
        if(reference.getHeight() <= 0 && !view.includeMempoolOutputs()) {
            restrictions.add(UtxoRestriction.UNCONFIRMED);
        }
        return restrictions;
    }

    private static boolean isSelfFunded(Wallet wallet, Sha256Hash txid, Map<String, BlockTransactionHashIndex> owned, Set<Sha256Hash> visited) {
        BlockTransaction blockTransaction = wallet.getWalletTransaction(txid);
        if(blockTransaction == null || blockTransaction.getTransaction() == null || !visited.add(txid)) {
            return false;
        }
        for(TransactionInput input : blockTransaction.getTransaction().getInputs()) {
            BlockTransactionHashIndex parent = owned.get(input.getOutpoint().getHash() + ":" + input.getOutpoint().getIndex());
            if(parent == null || (parent.getHeight() <= 0 && !isSelfFunded(wallet, parent.getHash(), owned, visited))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Balance balance(String accountId, Set<OutPoint> reserved) throws PwcException {
        return guard("balance", () -> withAccount(accountId, (account, wallet, view) -> {
            Wallet accountWallet = account.wallet();
            if(accountWallet.getStoredBlockHeight() == null) {
                throw new PwcException(ErrorCode.SYNC_INCOMPLETE, "The wallet has not synchronized yet");
            }
            Map<BlockTransactionHashIndex, WalletNode> txos = accountWallet.getWalletTxos();
            Map<String, BlockTransactionHashIndex> owned = new HashMap<>();
            txos.keySet().forEach(reference -> owned.put(reference.getHash() + ":" + reference.getIndex(), reference));

            long confirmed = 0, pendingSelf = 0, pendingExternal = 0, pendingOutgoing = 0, spendable = 0, locked = 0, immature = 0;
            for(BlockTransactionHashIndex reference : txos.keySet()) {
                BlockTransactionHashIndex spentBy = reference.getSpentBy();
                if(reference.getHeight() > 0) {
                    if(spentBy == null || spentBy.getHeight() <= 0) {
                        confirmed += reference.getValue();
                        if(spentBy != null) {
                            pendingOutgoing += reference.getValue();
                        }
                    }
                } else if(spentBy == null) {
                    if(isSelfFunded(accountWallet, reference.getHash(), owned, new HashSet<>())) {
                        pendingSelf += reference.getValue();
                    } else {
                        pendingExternal += reference.getValue();
                    }
                }

                if(isEffective(reference)) {
                    List<UtxoRestriction> restrictions = restrictions(view, accountWallet, reference, reserved);
                    if(restrictions.contains(UtxoRestriction.IMMATURE)) {
                        immature += reference.getValue();
                    } else if(restrictions.contains(UtxoRestriction.FROZEN) || restrictions.contains(UtxoRestriction.LOCKED)) {
                        locked += reference.getValue();
                    } else if(restrictions.isEmpty()) {
                        spendable += reference.getValue();
                    }
                }
            }

            BalanceComponents components = new BalanceComponents(confirmed, pendingSelf, pendingExternal, pendingOutgoing,
                    pendingSelf + pendingExternal - pendingOutgoing, spendable, locked, immature);
            return new Balance(accountId, network(), components, updatedAt(view, account, wallet), syncStatus(view, wallet));
        }));
    }

    private static SyncStatus syncStatus(PwcView view, WalletEntry wallet) {
        SyncState state = !view.connected() ? SyncState.OFFLINE : (wallet.state() == WalletState.SYNCING ? SyncState.SYNCING : SyncState.SYNCED);
        return new SyncStatus(state, tip(view, wallet.master()));
    }

    private Instant updatedAt(PwcView view, AccountEntry account, WalletEntry wallet) {
        if(syncStatus(view, wallet).state() == SyncState.SYNCED) {
            return clock.instant();
        }
        Instant syncedAt = account.form().syncedAt();
        return syncedAt != null ? syncedAt : Instant.ofEpochMilli(account.form().walletFileModified());
    }

    @Override
    public List<Utxo> utxos(String accountId, Set<OutPoint> reserved) throws PwcException {
        return guard("utxos", () -> withAccount(accountId, (account, wallet, view) -> {
            Wallet accountWallet = account.wallet();
            Integer tip = tip(view, accountWallet);
            List<Utxo> utxos = new ArrayList<>();
            for(Map.Entry<BlockTransactionHashIndex, WalletNode> entry : accountWallet.getWalletUtxos().entrySet()) {
                BlockTransactionHashIndex reference = entry.getKey();
                WalletNode node = entry.getValue();
                List<UtxoRestriction> restrictions = restrictions(view, accountWallet, reference, reserved);
                int confirmations = confirmations(reference, tip);
                utxos.add(new Utxo(reference.getHashAsString(), (int)reference.getIndex(), accountId, network(), reference.getValue(), confirmations,
                        confirmations > 0 ? reference.getHeight() : null, node.getAddress().toString(), node.getOutputScript().getProgram(),
                        restrictions.isEmpty(), restrictions, node.getKeyPurpose() == KeyPurpose.CHANGE));
            }
            return utxos;
        }));
    }

    @Override
    public List<TransactionRecord> transactions(String accountId) throws PwcException {
        return guard("transactions", () -> withAccount(accountId, (account, wallet, view) -> {
            Wallet accountWallet = account.wallet();
            Integer tip = tip(view, accountWallet);
            Map<Sha256Hash, Long> netByTxid = new HashMap<>();
            for(BlockTransactionHashIndex reference : accountWallet.getWalletTxos().keySet()) {
                netByTxid.merge(reference.getHash(), reference.getValue(), Long::sum);
                if(reference.getSpentBy() != null) {
                    netByTxid.merge(reference.getSpentBy().getHash(), -reference.getValue(), Long::sum);
                }
            }
            List<BlockTransaction> blockTransactions = new ArrayList<>(accountWallet.getWalletTransactions().values());
            blockTransactions.sort(Comparator.comparingInt((BlockTransaction tx) -> tx.getHeight() > 0 ? tx.getHeight() : Integer.MAX_VALUE).reversed());
            List<TransactionRecord> records = new ArrayList<>();
            for(BlockTransaction blockTransaction : blockTransactions) {
                long net = netByTxid.getOrDefault(blockTransaction.getHash(), 0L);
                int confirmations = confirmations(blockTransaction, tip);
                boolean confirmed = confirmations > 0;
                records.add(new TransactionRecord(accountId, network(), blockTransaction.getHashAsString(),
                        confirmed ? TransactionStatus.CONFIRMED : TransactionStatus.UNCONFIRMED, confirmations, confirmed ? blockTransaction.getHeight() : null,
                        null, confirmed && blockTransaction.getDate() != null ? blockTransaction.getDate().toInstant() : null, net, blockTransaction.getFee(), null));
            }
            return records;
        }));
    }

    private static AddressRecord record(String accountId, WalletNode node) {
        return new AddressRecord(accountId, network(), node.getAddress().toString(), node.getKeyPurpose() == KeyPurpose.CHANGE ? AddressRole.CHANGE : AddressRole.RECEIVE,
                node.isUsed() ? AddressState.USED : AddressState.UNUSED, node.getLabel());
    }

    /** The first unused receive address without a label that this backend has not handed out in this session. */
    private WalletNode freshReceiveNode(AccountEntry account) {
        Wallet wallet = account.wallet();
        Set<Integer> given = handedOut.getOrDefault(account.accountId(), Set.of());
        WalletNode node = wallet.getFreshNode(KeyPurpose.RECEIVE);
        while((node.getLabel() != null && !node.getLabel().isEmpty()) || given.contains(node.getIndex())) {
            node = wallet.getFreshNode(KeyPurpose.RECEIVE, node);
        }
        return node;
    }

    @Override
    public AddressRecord receiveAddress(String accountId) throws PwcException {
        return guard("receiveAddress", () -> withAccount(accountId, (account, wallet, view) -> record(accountId, freshReceiveNode(account))));
    }

    @Override
    public AddressRecord createAddress(String accountId, String label) throws PwcException {
        return guard("createAddress", () -> withAccount(accountId, (account, wallet, view) -> {
            WalletNode node = freshReceiveNode(account);
            String applied = label == null || label.isBlank() ? DEFAULT_ADDRESS_LABEL : label;
            handedOut.computeIfAbsent(accountId, id -> ConcurrentHashMap.newKeySet()).add(node.getIndex());
            node.setLabel(applied);
            WalletForm live = account.form().live();
            int index = node.getIndex();
            host.onWalletThread(() -> label(live, index, applied));
            return record(accountId, node);
        }));
    }

    /** Labels the receive address and extends the gap limit to it in the open wallet; JavaFX thread. */
    private static void label(WalletForm form, int index, String label) {
        Wallet wallet = form.getWallet();
        WalletNode purpose = wallet.getNode(KeyPurpose.RECEIVE);
        purpose.fillToIndex(wallet, index);
        WalletNode node = purpose.getChildren().stream().filter(child -> child.getIndex() == index).findFirst().orElseThrow();
        NodeEntry root = form.getNodeEntry(KeyPurpose.RECEIVE);
        NodeEntry entry = null;
        for(Entry child : root.getChildren()) {
            if(child instanceof NodeEntry nodeEntry && nodeEntry.getNode().equals(node)) {
                entry = nodeEntry;
            }
        }
        if(entry == null) {
            entry = new NodeEntry(wallet, node);
            root.getChildren().add(entry);
        }
        if(entry.getLabel() == null || entry.getLabel().isEmpty()) {
            entry.labelProperty().set(label);
        }
        form.ensureSufficientGapLimit(entry);
    }

    @Override
    public List<AddressRecord> addresses(String accountId) {
        return quietly("addresses", () -> withAccount(accountId, (account, wallet, view) -> {
            List<AddressRecord> records = new ArrayList<>();
            for(KeyPurpose purpose : List.of(KeyPurpose.RECEIVE, KeyPurpose.CHANGE)) {
                for(WalletNode node : account.wallet().getNode(purpose).getChildren()) {
                    records.add(record(accountId, node));
                }
            }
            return records;
        }), List.of());
    }

    @Override
    public boolean ownsScript(String accountId, byte[] scriptPubKey) {
        return quietly("ownsScript", () -> withAccount(accountId, (account, wallet, view) -> account.wallet().getWalletOutputScripts().keySet().stream()
                .anyMatch(script -> Arrays.equals(script.getProgram(), scriptPubKey))), false);
    }

    private static Optional<BlockTransactionHashIndex> find(Wallet wallet, OutPoint outPoint) {
        return wallet.getWalletTxos().keySet().stream()
                .filter(reference -> reference.getHashAsString().equals(outPoint.txid()) && reference.getIndex() == outPoint.vout()).findFirst();
    }

    @Override
    public boolean isUnspent(String accountId, OutPoint outPoint) {
        return quietly("isUnspent", () -> withAccount(accountId, (account, wallet, view) -> find(account.wallet(), outPoint).filter(PwcWalletBackend::isEffective).isPresent()), false);
    }

    @Override
    public Optional<TxOut> ownedOutput(String accountId, OutPoint outPoint) {
        return quietly("ownedOutput", () -> withAccount(accountId, (account, wallet, view) -> {
            Map<BlockTransactionHashIndex, WalletNode> txos = account.wallet().getWalletTxos();
            return find(account.wallet(), outPoint).map(reference -> new TxOut(reference.getValue(), txos.get(reference).getOutputScript().getProgram()));
        }), Optional.empty());
    }

    @Override
    public OptionalLong feeEstimate(String network, long targetBlocks) {
        return quietly("feeEstimate", () -> {
            PwcView view = host.view();
            Map<Integer, Double> rates = view.feeRates();
            if(!network().equals(network) || !view.connected() || rates == null || rates.isEmpty()) {
                return OptionalLong.empty();
            }
            TreeMap<Integer, Double> sorted = new TreeMap<>(rates);
            Map.Entry<Integer, Double> entry = sorted.floorEntry((int)Math.min(targetBlocks, Integer.MAX_VALUE));
            double rate = (entry != null ? entry : sorted.firstEntry()).getValue();
            return OptionalLong.of(Math.round(rate * 1000));
        }, OptionalLong.empty());
    }

    @Override
    public CreatedPsbt createPsbt(String accountId, CreateRequest request, Set<OutPoint> excluded) throws PwcException {
        return guard("createPsbt", () -> withAccount(accountId, (account, wallet, view) -> {
            Wallet accountWallet = account.wallet();
            List<Payment> payments = new ArrayList<>();
            for(TxOut output : request.outputs()) {
                Address address = new Script(output.scriptPubKey()).getToAddress();
                if(address == null) {
                    throw new PwcException(ErrorCode.INVALID_ARGUMENT, "An output script has no address");
                }
                payments.add(new Payment(address, null, output.valueSats(), false));
            }

            double feeRate = request.feeRate() / 1000.0;
            Map<Integer, Double> rates = view.feeRates();
            double longTermFeeRate = Math.max(Math.min(feeRate, rates == null || rates.isEmpty() ? feeRate : Collections.min(rates.values())), Transaction.DUST_RELAY_TX_FEE);

            List<TxoFilter> filters = List.of(new SpentTxoFilter(), new FrozenTxoFilter(), new CoinbaseTxoFilter(accountWallet),
                    reference -> !excluded.contains(outPoint(reference)));
            List<UtxoSelector> selectors;
            if(request.selected() != null) {
                List<BlockTransactionHashIndex> selected = new ArrayList<>();
                for(OutPoint outPoint : request.selected()) {
                    selected.add(find(accountWallet, outPoint).filter(PwcWalletBackend::isEffective)
                            .orElseThrow(() -> new PwcException(ErrorCode.INVALID_ARGUMENT, "A selected output is not spendable by this account")));
                }
                selectors = List.of(new PresetUtxoSelector(selected, false, true));
            } else {
                long noInputsFee = accountWallet.getNoInputsFee(payments, feeRate);
                long costOfChange = accountWallet.getCostOfChange(feeRate, longTermFeeRate);
                selectors = List.of(new BnBUtxoSelector(noInputsFee, costOfChange), new KnapsackUtxoSelector(noInputsFee));
            }

            TransactionParameters parameters = new TransactionParameters(selectors, filters, payments, List.of(), Set.of(), feeRate, longTermFeeRate,
                    view.minimumRelayFeeRate(), null, view.height(), view.groupByAddress(), view.includeMempoolOutputs(), request.replaceable());
            WalletTransaction walletTransaction;
            try {
                walletTransaction = accountWallet.createWalletTransaction(parameters);
            } catch(InsufficientFundsException e) {
                throw new PwcException(ErrorCode.INSUFFICIENT_FUNDS, "Insufficient spendable funds for these outputs and fee rate");
            }

            //The SDK signs PSBT version 0 only; the PSBT shuffles the outputs, so positions are read from its own transaction
            PSBT psbt = new PSBT(walletTransaction, 0, true);
            Set<Script> changeScripts = new HashSet<>();
            walletTransaction.getChangeMap().keySet().forEach(node -> changeScripts.add(node.getOutputScript()));
            List<TransactionOutput> outputs = psbt.getTransaction().getOutputs();
            List<Integer> changeIndexes = new ArrayList<>();
            for(int i = 0; i < outputs.size(); i++) {
                if(changeScripts.contains(outputs.get(i).getScript())) {
                    changeIndexes.add(i);
                }
            }
            List<OutPoint> inputs = psbt.getTransaction().getInputs().stream()
                    .map(input -> new OutPoint(input.getOutpoint().getHash().toString(), (int)input.getOutpoint().getIndex())).toList();
            return new CreatedPsbt(psbt.serialize(), walletTransaction.getFee(), (long)Math.ceil(walletTransaction.getVirtualSize()), changeIndexes, inputs);
        }));
    }

    private static PSBT parse(byte[] psbt) throws PwcException {
        try {
            return new PSBT(psbt);
        } catch(Exception e) {
            throw new PwcException(ErrorCode.PSBT_VALIDATION_FAILED, "The PSBT cannot be parsed");
        }
    }

    /**
     * A wallet copy with usable private keys: the decrypted copy unlocked for this request, or a copy of an unencrypted wallet.
     * An approval left for the account is always taken, so that it serves at most one call.
     */
    private Wallet signingCopy(AccountEntry account, String subject) throws PwcException {
        Approval approval = take(account.accountId(), subject);
        if(approval != null && approval.decrypted() != null) {
            return approval.decrypted();
        }
        Wallet copy = account.wallet().copy();
        if(copy.isEncrypted()) {
            throw new PwcException(ErrorCode.SIGNER_UNAVAILABLE, "The wallet is encrypted and was not unlocked for this request");
        }
        return copy;
    }

    @Override
    public SignedPsbt signPsbt(String accountId, byte[] psbtBytes, List<Integer> inputs) throws PwcException {
        return guard("signPsbt", () -> {
            PSBT psbt = parse(psbtBytes);
            AccountEntry account = withAccount(accountId, (entry, wallet, view) -> entry);
            String subject = psbtSubject(psbtBytes, inputs);
            List<Integer> signed = new ArrayList<>();
            if(account.signer() == Signer.DEVICE) {
                Approval approval = take(accountId, subject);
                if(approval == null || approval.deviceSigned() == null) {
                    if(approval != null) {
                        approval.clear();
                    }
                    throw new PwcException(ErrorCode.SIGNER_UNAVAILABLE, "The device did not sign this request in the wallet");
                }
                try {
                    psbt.verifyCombinedSignatures(approval.deviceSigned());
                } catch(Exception e) {
                    throw new PwcException(ErrorCode.PSBT_VALIDATION_FAILED, "The device returned invalid signatures");
                }
                for(int index : inputs) {
                    Map<ECKey, TransactionSignature> signatures = approval.deviceSigned().getPsbtInputs().get(index).getPartialSignatures();
                    if(!signatures.isEmpty()) {
                        psbt.getPsbtInputs().get(index).getPartialSignatures().putAll(signatures);
                        signed.add(index);
                    }
                }
            } else if(account.signer() == Signer.SOFTWARE) {
                Wallet copy = signingCopy(account, subject);
                try {
                    Map<PSBTInput, WalletNode> signingNodes = copy.getSigningNodes(psbt);
                    Map<PSBTInput, WalletNode> requested = new LinkedHashMap<>();
                    for(int index : inputs) {
                        PSBTInput input = psbt.getPsbtInputs().get(index);
                        if(signingNodes.containsKey(input)) {
                            requested.put(input, signingNodes.get(input));
                        }
                    }
                    copy.sign(requested);
                    for(int index : inputs) {
                        if(requested.containsKey(psbt.getPsbtInputs().get(index)) && !psbt.getPsbtInputs().get(index).getPartialSignatures().isEmpty()) {
                            signed.add(index);
                        }
                    }
                } catch(MnemonicException e) {
                    throw new PwcException(ErrorCode.SIGNER_UNAVAILABLE, "The wallet keys are not available");
                } finally {
                    copy.clearPrivate();
                }
            } else {
                throw new PwcException(ErrorCode.UNSUPPORTED_OPERATION, "This account cannot sign");
            }
            return new SignedPsbt(psbt.serialize(), signed);
        });
    }

    @Override
    public byte[] signMessage(String accountId, byte[] scriptPubKey, String message, MessageFormat format) throws PwcException {
        return guard("signMessage", () -> {
            Address address = new Script(scriptPubKey).getToAddress();
            AccountEntry account = withAccount(accountId, (entry, wallet, view) -> entry);
            WalletNode node = address == null ? null : account.wallet().getWalletAddresses().get(address);
            if(node == null) {
                throw new PwcException(ErrorCode.INVALID_ARGUMENT, "The address does not belong to this account");
            }
            Wallet copy = signingCopy(account, messageSubject(address.toString(), message, format));
            try {
                ECKey key = copy.getKeystores().getFirst().getKey(node);
                String signature = switch(format) {
                    case BIP322_SIMPLE -> Bip322.signMessageBip322(com.sparrowwallet.drongo.protocol.ScriptType.P2WPKH, message, key).substring(Bip322.SIMPLE_PREFIX.length());
                    case LEGACY -> key.signMessage(message, com.sparrowwallet.drongo.protocol.ScriptType.P2PKH);
                    case BIP322_FULL -> throw new PwcException(ErrorCode.UNSUPPORTED_OPERATION, "BIP-322 full signatures are not supported");
                };
                return Base64.getDecoder().decode(signature);
            } catch(MnemonicException e) {
                throw new PwcException(ErrorCode.SIGNER_UNAVAILABLE, "The wallet keys are not available");
            } finally {
                copy.clearPrivate();
            }
        });
    }

    /** Sends through the host on a separate thread and waits at most {@code broadcastTimeout}. */
    private Sha256Hash send(Transaction transaction) throws PwcException {
        Future<Sha256Hash> sent = broadcaster.submit(() -> host.broadcast(transaction));
        try {
            return sent.get(broadcastTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch(TimeoutException e) {
            sent.cancel(true);
            throw new PwcException(ErrorCode.NETWORK_UNAVAILABLE, "The server did not answer in time; the transaction may still arrive");
        } catch(InterruptedException e) {
            sent.cancel(true);
            Thread.currentThread().interrupt();
            throw new PwcException(ErrorCode.NETWORK_UNAVAILABLE, "The broadcast was interrupted");
        } catch(ExecutionException e) {
            if(e.getCause() instanceof PwcException failure) {
                throw failure;
            }
            log.error("PWC broadcast error", e.getCause());
            throw new PwcException(ErrorCode.INTERNAL_ERROR, "The wallet could not complete the request");
        }
    }

    @Override
    public String broadcast(String network, byte[] transactionBytes) throws PwcException {
        return guard("broadcast", () -> {
            if(!network().equals(network)) {
                throw new PwcException(ErrorCode.NETWORK_MISMATCH, "This wallet runs on " + network());
            }
            Transaction transaction;
            try {
                transaction = new Transaction(transactionBytes);
            } catch(Exception e) {
                throw new PwcException(ErrorCode.INVALID_ARGUMENT, "The transaction cannot be parsed");
            }
            if(closed || !host.view().connected()) {
                throw new PwcException(ErrorCode.NETWORK_UNAVAILABLE, "The wallet is not connected to a server");
            }
            String key = replayKey(transaction);
            if(LegacyUtxoChecker.isSupported(Network.get()) && !isReplayCleared(key)) {
                throw new PwcException(ErrorCode.POLICY_VIOLATION,
                        "The legacy-chain replay risk of this transaction was not cleared in a PWC signing prompt; broadcast it from PurityWallet");
            }
            Sha256Hash txid = send(transaction);
            if(!transaction.getTxId().equals(txid)) {
                throw new PwcException(ErrorCode.TRANSACTION_REJECTED, "The server did not accept this transaction");
            }
            replayClearances.remove(key);
            return transaction.getTxId().toString();
        });
    }
}
