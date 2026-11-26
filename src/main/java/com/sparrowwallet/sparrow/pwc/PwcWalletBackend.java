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
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.wallet.NodeEntry;
import com.sparrowwallet.sparrow.wallet.WalletForm;
import org.bitcoinpurity.pwc.provider.*;
import org.bitcoinpurity.pwc.provider.ScriptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** The open wallets of the application, as the PWC Provider sees them. */
public class PwcWalletBackend implements WalletBackend {
    private static final Logger log = LoggerFactory.getLogger(PwcWalletBackend.class);

    static final String DEFAULT_ADDRESS_LABEL = "PWC";

    private static final List<String> READS = List.of("account.getBalance", "address.getReceiveAddress", "address.create", "address.list",
            "utxo.list", "transaction.list", "transaction.get");
    private static final List<String> CREATE = List.of("psbt.create", "psbt.releaseReservation");

    private enum Signer { SOFTWARE, DEVICE, NONE }

    private record AccountEntry(String accountId, String walletId, WalletForm form, Signer signer) {
        Wallet wallet() {
            return form.getWallet();
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

    private final PwcHost host;
    private final PwcIds ids;
    private final Map<String, Wallet> unlocked = new ConcurrentHashMap<>();

    public PwcWalletBackend(PwcHost host, PwcIds ids) {
        this.host = host;
        this.ids = ids;
    }

    public PwcIds getIds() {
        return ids;
    }

    /** Hands over a decrypted copy of an encrypted account for the one signature the user has just approved. */
    public void unlocked(String accountId, Wallet decrypted) {
        Wallet previous = unlocked.put(accountId, decrypted);
        if(previous != null) {
            previous.clearPrivate();
        }
    }

    public void discardUnlocked(String accountId) {
        Wallet previous = unlocked.remove(accountId);
        if(previous != null) {
            previous.clearPrivate();
        }
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

    /** Called on the wallet thread. */
    private List<WalletEntry> snapshot() {
        Map<Wallet, List<WalletForm>> byMaster = new LinkedHashMap<>();
        for(WalletForm form : host.openWalletForms()) {
            byMaster.computeIfAbsent(form.getMasterWallet(), master -> new ArrayList<>()).add(form);
        }

        List<WalletEntry> entries = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for(Map.Entry<Wallet, List<WalletForm>> entry : byMaster.entrySet()) {
            Wallet master = entry.getKey();
            String walletId;
            List<AccountEntry> accounts = new ArrayList<>();
            try {
                walletId = ids.walletId(master);
                for(WalletForm form : entry.getValue()) {
                    if(isSupported(form.getWallet())) {
                        String accountId = ids.accountId(form.getWallet());
                        if(seen.add(accountId)) {
                            accounts.add(new AccountEntry(accountId, walletId, form, signer(form.getWallet())));
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
            boolean locked = entry.getValue().stream().anyMatch(WalletForm::isLocked);
            WalletState state = locked ? WalletState.LOCKED : (host.isSyncing(master) ? WalletState.SYNCING : WalletState.READY);
            entries.add(new WalletEntry(walletId, master, state, accounts));
        }
        return entries;
    }

    private interface AccountAction<T> {
        T apply(AccountEntry account, WalletEntry wallet) throws Exception;
    }

    /** Runs the action on the wallet thread for a readable account. */
    private <T> T withAccount(String accountId, AccountAction<T> action) throws PwcException {
        try {
            return host.onWalletThread(() -> {
                for(WalletEntry wallet : snapshot()) {
                    for(AccountEntry account : wallet.accounts()) {
                        if(account.accountId().equals(accountId)) {
                            if(wallet.state() == WalletState.LOCKED) {
                                throw new PwcException(ErrorCode.WALLET_LOCKED, "The wallet is locked");
                            }
                            return action.apply(account, wallet);
                        }
                    }
                }
                throw new PwcException(ErrorCode.ACCOUNT_NOT_FOUND, "No such account");
            });
        } catch(PwcException e) {
            throw e;
        } catch(Exception e) {
            log.error("PWC backend error", e);
            throw new PwcException(ErrorCode.INTERNAL_ERROR, "The wallet could not complete the request");
        }
    }

    private <T> T withAccount(String accountId, AccountAction<T> action, T otherwise) {
        try {
            return withAccount(accountId, action);
        } catch(PwcException e) {
            return otherwise;
        }
    }

    /** The open wallet form of an account; called on the wallet thread. */
    WalletForm walletForm(String accountId) {
        return snapshot().stream().flatMap(wallet -> wallet.accounts().stream()).filter(account -> account.accountId().equals(accountId))
                .map(AccountEntry::form).findFirst().orElse(null);
    }

    @Override
    public List<WalletInfo> wallets() {
        try {
            return host.onWalletThread(() -> snapshot().stream().map(wallet -> new WalletInfo(wallet.walletId(), wallet.master().getName(), network(),
                    kind(wallet.master()), wallet.state(), wallet.state() == WalletState.LOCKED ? List.of() : wallet.accounts().stream().map(AccountEntry::accountId).toList())).toList());
        } catch(Exception e) {
            log.error("PWC backend error", e);
            return List.of();
        }
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
        try {
            return host.onWalletThread(() -> {
                for(WalletEntry wallet : snapshot()) {
                    for(AccountEntry account : wallet.accounts()) {
                        if(account.accountId().equals(accountId)) {
                            return Optional.of(new AccountInfo(accountId, wallet.walletId(), account.wallet().getDisplayName(), network(),
                                    account.signer() != Signer.NONE, account.segwit() ? ScriptType.P2WPKH : ScriptType.P2PKH, account.methods()));
                        }
                    }
                }
                return Optional.<AccountInfo>empty();
            });
        } catch(Exception e) {
            log.error("PWC backend error", e);
            return Optional.empty();
        }
    }

    private Integer tip(Wallet wallet) {
        Integer height = host.currentBlockHeight();
        return height != null ? height : wallet.getStoredBlockHeight();
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

    private static List<UtxoRestriction> restrictions(Wallet wallet, BlockTransactionHashIndex reference, Set<OutPoint> reserved) {
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
        if(reference.getHeight() <= 0 && !Config.get().isIncludeMempoolOutputs()) {
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
        return withAccount(accountId, (account, wallet) -> {
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
                    List<UtxoRestriction> restrictions = restrictions(accountWallet, reference, reserved);
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
            return new Balance(accountId, network(), components, updatedAt(account, wallet), syncStatus(wallet));
        });
    }

    private SyncStatus syncStatus(WalletEntry wallet) {
        SyncState state = !host.isConnected() ? SyncState.OFFLINE : (wallet.state() == WalletState.SYNCING ? SyncState.SYNCING : SyncState.SYNCED);
        return new SyncStatus(state, tip(wallet.master()));
    }

    private Instant updatedAt(AccountEntry account, WalletEntry wallet) {
        if(syncStatus(wallet).state() == SyncState.SYNCED) {
            return Instant.now();
        }
        Instant syncedAt = host.syncedAt(wallet.master());
        return syncedAt != null ? syncedAt : Instant.ofEpochMilli(account.form().getWalletFile().lastModified());
    }

    @Override
    public List<Utxo> utxos(String accountId, Set<OutPoint> reserved) throws PwcException {
        return withAccount(accountId, (account, wallet) -> {
            Wallet accountWallet = account.wallet();
            Integer tip = tip(accountWallet);
            List<Utxo> utxos = new ArrayList<>();
            for(Map.Entry<BlockTransactionHashIndex, WalletNode> entry : accountWallet.getWalletUtxos().entrySet()) {
                BlockTransactionHashIndex reference = entry.getKey();
                WalletNode node = entry.getValue();
                List<UtxoRestriction> restrictions = restrictions(accountWallet, reference, reserved);
                int confirmations = confirmations(reference, tip);
                utxos.add(new Utxo(reference.getHashAsString(), (int)reference.getIndex(), accountId, network(), reference.getValue(), confirmations,
                        confirmations > 0 ? reference.getHeight() : null, node.getAddress().toString(), node.getOutputScript().getProgram(),
                        restrictions.isEmpty(), restrictions, node.getKeyPurpose() == KeyPurpose.CHANGE));
            }
            return utxos;
        });
    }

    @Override
    public List<TransactionRecord> transactions(String accountId) throws PwcException {
        return withAccount(accountId, (account, wallet) -> {
            Wallet accountWallet = account.wallet();
            Integer tip = tip(accountWallet);
            Set<BlockTransactionHashIndex> txos = accountWallet.getWalletTxos().keySet();
            List<BlockTransaction> blockTransactions = new ArrayList<>(accountWallet.getWalletTransactions().values());
            blockTransactions.sort(Comparator.comparingInt((BlockTransaction tx) -> tx.getHeight() > 0 ? tx.getHeight() : Integer.MAX_VALUE).reversed());
            List<TransactionRecord> records = new ArrayList<>();
            for(BlockTransaction blockTransaction : blockTransactions) {
                long net = 0;
                for(BlockTransactionHashIndex reference : txos) {
                    if(reference.getHash().equals(blockTransaction.getHash())) {
                        net += reference.getValue();
                    }
                    if(reference.getSpentBy() != null && reference.getSpentBy().getHash().equals(blockTransaction.getHash())) {
                        net -= reference.getValue();
                    }
                }
                int confirmations = confirmations(blockTransaction, tip);
                boolean confirmed = confirmations > 0;
                records.add(new TransactionRecord(accountId, network(), blockTransaction.getHashAsString(),
                        confirmed ? TransactionStatus.CONFIRMED : TransactionStatus.UNCONFIRMED, confirmations, confirmed ? blockTransaction.getHeight() : null,
                        null, confirmed && blockTransaction.getDate() != null ? blockTransaction.getDate().toInstant() : null, net, blockTransaction.getFee(), null));
            }
            return records;
        });
    }

    private static AddressRecord record(String accountId, WalletNode node) {
        return new AddressRecord(accountId, network(), node.getAddress().toString(), node.getKeyPurpose() == KeyPurpose.CHANGE ? AddressRole.CHANGE : AddressRole.RECEIVE,
                node.isUsed() ? AddressState.USED : AddressState.UNUSED, node.getLabel());
    }

    @Override
    public AddressRecord receiveAddress(String accountId) throws PwcException {
        return withAccount(accountId, (account, wallet) -> record(accountId, account.form().getFreshNodeEntry(KeyPurpose.RECEIVE, null).getNode()));
    }

    @Override
    public AddressRecord createAddress(String accountId, String label) throws PwcException {
        return withAccount(accountId, (account, wallet) -> {
            NodeEntry entry = account.form().getFreshNodeEntry(KeyPurpose.RECEIVE, null);
            entry.labelProperty().set(label == null || label.isBlank() ? DEFAULT_ADDRESS_LABEL : label);
            account.form().ensureSufficientGapLimit(entry);
            return record(accountId, entry.getNode());
        });
    }

    @Override
    public List<AddressRecord> addresses(String accountId) {
        return withAccount(accountId, (account, wallet) -> {
            List<AddressRecord> records = new ArrayList<>();
            for(KeyPurpose purpose : List.of(KeyPurpose.RECEIVE, KeyPurpose.CHANGE)) {
                for(WalletNode node : account.wallet().getNode(purpose).getChildren()) {
                    records.add(record(accountId, node));
                }
            }
            return records;
        }, List.of());
    }

    @Override
    public boolean ownsScript(String accountId, byte[] scriptPubKey) {
        return withAccount(accountId, (account, wallet) -> account.wallet().getWalletOutputScripts().keySet().stream()
                .anyMatch(script -> Arrays.equals(script.getProgram(), scriptPubKey)), false);
    }

    private static Optional<BlockTransactionHashIndex> find(Wallet wallet, OutPoint outPoint) {
        return wallet.getWalletTxos().keySet().stream()
                .filter(reference -> reference.getHashAsString().equals(outPoint.txid()) && reference.getIndex() == outPoint.vout()).findFirst();
    }

    @Override
    public boolean isUnspent(String accountId, OutPoint outPoint) {
        return withAccount(accountId, (account, wallet) -> find(account.wallet(), outPoint).filter(PwcWalletBackend::isEffective).isPresent(), false);
    }

    @Override
    public Optional<TxOut> ownedOutput(String accountId, OutPoint outPoint) {
        return withAccount(accountId, (account, wallet) -> {
            Map<BlockTransactionHashIndex, WalletNode> txos = account.wallet().getWalletTxos();
            return find(account.wallet(), outPoint).map(reference -> new TxOut(reference.getValue(), txos.get(reference).getOutputScript().getProgram()));
        }, Optional.empty());
    }

    @Override
    public OptionalLong feeEstimate(String network, long targetBlocks) {
        Map<Integer, Double> rates = host.targetBlockFeeRates();
        if(!network().equals(network) || !host.isConnected() || rates == null || rates.isEmpty()) {
            return OptionalLong.empty();
        }
        TreeMap<Integer, Double> sorted = new TreeMap<>(rates);
        Map.Entry<Integer, Double> entry = sorted.floorEntry((int)Math.min(targetBlocks, Integer.MAX_VALUE));
        double rate = (entry != null ? entry : sorted.firstEntry()).getValue();
        return OptionalLong.of(Math.round(rate * 1000));
    }

    @Override
    public CreatedPsbt createPsbt(String accountId, CreateRequest request, Set<OutPoint> excluded) throws PwcException {
        return withAccount(accountId, (account, wallet) -> {
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
            Map<Integer, Double> rates = host.targetBlockFeeRates();
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

            Config config = Config.get();
            TransactionParameters parameters = new TransactionParameters(selectors, filters, payments, List.of(), Set.of(), feeRate, longTermFeeRate,
                    host.minimumRelayFeeRate(), null, host.currentBlockHeight(), config.isGroupByAddress(), config.isIncludeMempoolOutputs(), request.replaceable());
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
        });
    }

    private static PSBT parse(byte[] psbt) throws PwcException {
        try {
            return new PSBT(psbt);
        } catch(Exception e) {
            throw new PwcException(ErrorCode.PSBT_VALIDATION_FAILED, "The PSBT cannot be parsed");
        }
    }

    /** A wallet copy with usable private keys: the decrypted copy handed over for this request, or a copy of an unencrypted wallet. */
    private Wallet signingCopy(String accountId) throws PwcException {
        Wallet decrypted = unlocked.remove(accountId);
        if(decrypted != null) {
            return decrypted;
        }
        Wallet copy = withAccount(accountId, (account, wallet) -> account.wallet().copy());
        if(copy.isEncrypted()) {
            throw new PwcException(ErrorCode.SIGNER_UNAVAILABLE, "The wallet is encrypted and was not unlocked for this request");
        }
        return copy;
    }

    @Override
    public SignedPsbt signPsbt(String accountId, byte[] psbtBytes, List<Integer> inputs) throws PwcException {
        PSBT psbt = parse(psbtBytes);
        Signer signer = withAccount(accountId, (account, wallet) -> account.signer());
        List<Integer> signed = new ArrayList<>();
        if(signer == Signer.DEVICE) {
            Wallet deviceWallet = withAccount(accountId, (account, wallet) -> account.wallet());
            PSBT deviceSigned = host.signWithDevice(deviceWallet, parse(psbt.serialize()));
            if(deviceSigned == null) {
                throw new PwcException(ErrorCode.USER_REJECTED, "The signature was cancelled on the device");
            }
            try {
                psbt.verifyCombinedSignatures(deviceSigned);
            } catch(Exception e) {
                throw new PwcException(ErrorCode.PSBT_VALIDATION_FAILED, "The device returned invalid signatures");
            }
            for(int index : inputs) {
                Map<ECKey, TransactionSignature> signatures = deviceSigned.getPsbtInputs().get(index).getPartialSignatures();
                if(!signatures.isEmpty()) {
                    psbt.getPsbtInputs().get(index).getPartialSignatures().putAll(signatures);
                    signed.add(index);
                }
            }
        } else if(signer == Signer.SOFTWARE) {
            Wallet copy = signingCopy(accountId);
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
    }

    @Override
    public byte[] signMessage(String accountId, byte[] scriptPubKey, String message, MessageFormat format) throws PwcException {
        Address address = new Script(scriptPubKey).getToAddress();
        WalletNode node = address == null ? null : withAccount(accountId, (account, wallet) -> account.wallet().getWalletAddresses().get(address));
        if(node == null) {
            throw new PwcException(ErrorCode.INVALID_ARGUMENT, "The address does not belong to this account");
        }
        Wallet copy = signingCopy(accountId);
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
    }

    @Override
    public String broadcast(String network, byte[] transactionBytes) throws PwcException {
        if(!network().equals(network)) {
            throw new PwcException(ErrorCode.NETWORK_MISMATCH, "This wallet runs on " + network());
        }
        Transaction transaction;
        try {
            transaction = new Transaction(transactionBytes);
        } catch(Exception e) {
            throw new PwcException(ErrorCode.INVALID_ARGUMENT, "The transaction cannot be parsed");
        }
        if(!host.isConnected()) {
            throw new PwcException(ErrorCode.NETWORK_UNAVAILABLE, "The wallet is not connected to a server");
        }
        if(!host.acceptReplayRisk(transaction)) {
            throw new PwcException(ErrorCode.USER_REJECTED, "The replay risk was not accepted");
        }
        try {
            return host.broadcast(transaction).toString();
        } catch(Exception e) {
            log.warn("PWC broadcast refused", e);
            throw new PwcException(ErrorCode.TRANSACTION_REJECTED, "The server refused the transaction");
        }
    }
}
