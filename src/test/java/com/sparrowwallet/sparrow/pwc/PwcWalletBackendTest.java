package com.sparrowwallet.sparrow.pwc;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.crypto.Argon2KeyDeriver;
import com.sparrowwallet.drongo.crypto.Bip322;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.policy.Policy;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionWitness;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.wallet.WalletForm;
import org.bitcoinpurity.pwc.provider.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class PwcWalletBackendTest {
    @TempDir
    File dir;

    private FakeHost host;
    private PwcIds ids;
    private PwcWalletBackend backend;
    private TestClock clock;
    private final Random random = new Random(7);

    static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-11-27T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static final Duration BROADCAST_TIMEOUT = Duration.ofMillis(300);

    @BeforeEach
    void setUp() throws Exception {
        Network.set(Network.REGTEST);
        host = new FakeHost();
        ids = PwcIds.load(new File(dir, "pwc"));
        clock = new TestClock();
        backend = new PwcWalletBackend(host, ids, clock, BROADCAST_TIMEOUT);
    }

    /** Runs the call on another thread and fails if it takes longer than {@code limit}. */
    private static <T> T within(Duration limit, Callable<T> call) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            return executor.submit(call).get(limit.toMillis(), TimeUnit.MILLISECONDS);
        } catch(ExecutionException e) {
            throw e.getCause() instanceof Exception cause ? cause : e;
        } catch(TimeoutException e) {
            throw new AssertionError("The backend call took longer than " + limit, e);
        } finally {
            executor.shutdownNow();
        }
    }

    private WalletForm open(Wallet wallet) {
        wallet.setStoredBlockHeight(1000);
        WalletForm form = new WalletForm(new Storage(new File(dir, wallet.getName() + "-" + random.nextInt())), wallet);
        host.forms.add(form);
        return form;
    }

    private WalletNode node(Wallet wallet, KeyPurpose purpose, int index) {
        wallet.getNode(purpose).fillToIndex(wallet, index);
        return wallet.getNode(purpose).getChildren().stream().filter(n -> n.getIndex() == index).findFirst().orElseThrow();
    }

    private Script foreignScript() {
        return PwcTestWallets.software("Other", ScriptType.P2WPKH, PwcTestWallets.OTHER_SEED).getFreshNode(KeyPurpose.RECEIVE).getOutputScript();
    }

    private BlockTransactionHashIndex receive(Wallet wallet, WalletNode node, long value, int height, boolean coinbase) {
        Transaction tx = new Transaction();
        byte[] previous = new byte[32];
        random.nextBytes(previous);
        if(coinbase) {
            tx.addInput(Sha256Hash.ZERO_HASH, 0xffffffffL, new Script(new byte[] {0x02, 0x01, 0x01}));
        } else {
            tx.addInput(Sha256Hash.wrap(previous), 0, new Script(new byte[0]));
        }
        tx.addOutput(value, node.getOutputScript());
        Date date = height > 0 ? new Date(1_700_000_000_000L + height) : null;
        BlockTransactionHashIndex ref = new BlockTransactionHashIndex(tx.getTxId(), height, date, null, 0, value);
        node.getTransactionOutputs().add(ref);
        wallet.updateTransactions(Map.of(tx.getTxId(), new BlockTransaction(tx.getTxId(), height, date, null, tx)));
        return ref;
    }

    /** A pending transaction from the account: spends {@code input}, pays {@code external} away and returns {@code change}. */
    private BlockTransactionHashIndex spend(Wallet wallet, BlockTransactionHashIndex input, long external, WalletNode changeNode, long change) {
        Transaction tx = new Transaction();
        tx.addInput(input.getHash(), input.getIndex(), new Script(new byte[0]));
        tx.addOutput(external, foreignScript());
        tx.addOutput(change, changeNode.getOutputScript());
        long fee = input.getValue() - external - change;
        input.setSpentBy(new BlockTransactionHashIndex(tx.getTxId(), 0, null, fee, 0, input.getValue()));
        BlockTransactionHashIndex changeRef = new BlockTransactionHashIndex(tx.getTxId(), 0, null, fee, 1, change);
        changeNode.getTransactionOutputs().add(changeRef);
        wallet.updateTransactions(Map.of(tx.getTxId(), new BlockTransaction(tx.getTxId(), 0, null, fee, tx)));
        return changeRef;
    }

    private String accountId(Wallet wallet) {
        return ids.accountId(wallet);
    }

    private static ErrorCode code(Executable executable) {
        return assertThrows(PwcException.class, executable::run).code();
    }

    private interface Executable {
        void run() throws Throwable;
    }

    @Test
    void listsOpenSingleSigWalletsWithTheirAccountsAndKinds() {
        Wallet software = PwcTestWallets.software("Software", ScriptType.P2WPKH);
        Wallet legacy = PwcTestWallets.software("Legacy", ScriptType.P2PKH, PwcTestWallets.OTHER_SEED);
        Wallet watch = PwcTestWallets.watchOnly(PwcTestWallets.software("Watched", ScriptType.P2WPKH, PwcTestWallets.OTHER_SEED));
        Wallet device = PwcTestWallets.hardware(PwcTestWallets.software("Device", ScriptType.P2WPKH, PwcTestWallets.THIRD_SEED));
        Wallet nested = PwcTestWallets.software("Nested", ScriptType.P2SH_P2WPKH);
        for(Wallet wallet : List.of(software, legacy, watch, device, nested)) {
            open(wallet);
        }

        List<WalletInfo> wallets = backend.wallets();
        assertEquals(4, wallets.size(), "a P2SH-P2WPKH account cannot be represented by the SDK");
        Map<String, WalletInfo> byName = new HashMap<>();
        wallets.forEach(w -> byName.put(w.displayName(), w));
        assertEquals(WalletKind.SOFTWARE, byName.get("Software").kind());
        assertEquals(WalletKind.SOFTWARE, byName.get("Legacy").kind());
        assertEquals(WalletKind.WATCH_ONLY, byName.get("Watched watch").kind());
        assertEquals(WalletKind.HARDWARE, byName.get("Device device").kind());
        for(WalletInfo info : wallets) {
            assertEquals("regtest", info.network());
            assertEquals(WalletState.READY, info.state());
            assertEquals(1, info.accountIds().size());
        }
        assertEquals(ids.walletId(software), byName.get("Software").walletId());
        assertEquals(List.of(accountId(software)), byName.get("Software").accountIds());
    }

    @Test
    void accountMethodsFollowTheSigningPath() {
        Wallet software = PwcTestWallets.software("Software", ScriptType.P2WPKH);
        Wallet legacy = PwcTestWallets.software("Legacy", ScriptType.P2PKH);
        Wallet watch = PwcTestWallets.watchOnly(PwcTestWallets.software("Watch", ScriptType.P2WPKH, PwcTestWallets.OTHER_SEED));
        Wallet device = PwcTestWallets.hardware(PwcTestWallets.software("Device", ScriptType.P2WPKH, PwcTestWallets.THIRD_SEED));
        Wallet legacyWatch = PwcTestWallets.watchOnly(PwcTestWallets.software("LegacyWatch", ScriptType.P2PKH, PwcTestWallets.OTHER_SEED));
        List.of(software, legacy, watch, device, legacyWatch).forEach(this::open);

        AccountInfo softwareInfo = backend.account(accountId(software)).orElseThrow();
        assertTrue(softwareInfo.canSign());
        assertEquals(org.bitcoinpurity.pwc.provider.ScriptType.P2WPKH, softwareInfo.scriptType());
        assertTrue(softwareInfo.methods().containsAll(List.of("psbt.create", "psbt.sign", "message.sign", "transaction.broadcast")));

        AccountInfo legacyInfo = backend.account(accountId(legacy)).orElseThrow();
        assertTrue(legacyInfo.canSign());
        assertTrue(legacyInfo.methods().contains("message.sign"));
        assertFalse(legacyInfo.methods().contains("psbt.sign"));
        assertFalse(legacyInfo.methods().contains("psbt.create"));

        AccountInfo deviceInfo = backend.account(accountId(device)).orElseThrow();
        assertTrue(deviceInfo.canSign());
        assertTrue(deviceInfo.methods().contains("psbt.sign"));
        assertFalse(deviceInfo.methods().contains("message.sign"));

        for(Wallet readOnly : List.of(watch, legacyWatch)) {
            AccountInfo info = backend.account(accountId(readOnly)).orElseThrow();
            assertFalse(info.canSign());
            assertFalse(info.methods().contains("psbt.sign"));
            assertFalse(info.methods().contains("message.sign"));
        }
        assertTrue(backend.account(accountId(watch)).orElseThrow().methods().contains("psbt.create"));
        assertTrue(backend.account("0".repeat(32)).isEmpty());
    }

    @Test
    void childAccountsBelongToTheirMasterWallet() {
        Wallet master = PwcTestWallets.software("Master", ScriptType.P2WPKH);
        Wallet child = master.addChildWallet(StandardAccount.ACCOUNT_1);
        open(master);
        open(child);

        List<WalletInfo> wallets = backend.wallets();
        assertEquals(1, wallets.size());
        assertEquals(List.of(accountId(master), accountId(child)), wallets.getFirst().accountIds());
        assertEquals(ids.walletId(master), backend.account(accountId(child)).orElseThrow().walletId());
    }

    @Test
    void lockedAndSyncingWalletsReportTheirState() {
        Wallet locked = PwcTestWallets.software("Locked", ScriptType.P2WPKH);
        Wallet syncing = PwcTestWallets.software("Syncing", ScriptType.P2WPKH, PwcTestWallets.OTHER_SEED);
        open(locked).setLocked(true);
        open(syncing);
        host.syncing.add(syncing);

        Map<String, WalletInfo> byName = new HashMap<>();
        backend.wallets().forEach(w -> byName.put(w.displayName(), w));
        assertEquals(WalletState.LOCKED, byName.get("Locked").state());
        assertEquals(List.of(), byName.get("Locked").accountIds());
        assertEquals(WalletState.SYNCING, byName.get("Syncing").state());
        assertEquals(ErrorCode.WALLET_LOCKED, code(() -> backend.balance(accountId(locked), Set.of())));
    }

    @Test
    void balanceSplitsConfirmedAndPendingValue() throws Exception {
        Wallet wallet = PwcTestWallets.software("Main", ScriptType.P2WPKH);
        open(wallet);
        BlockTransactionHashIndex spent = receive(wallet, node(wallet, KeyPurpose.RECEIVE, 0), 400_000, 900, false);
        receive(wallet, node(wallet, KeyPurpose.RECEIVE, 1), 600_000, 900, false);
        spend(wallet, spent, 300_000, node(wallet, KeyPurpose.CHANGE, 0), 99_000);

        BalanceComponents balance = backend.balance(accountId(wallet), Set.of()).components();
        assertEquals(1_000_000, balance.confirmedSats());
        assertEquals(99_000, balance.pendingSelfSats());
        assertEquals(0, balance.pendingExternalSats());
        assertEquals(400_000, balance.pendingOutgoingSats());
        assertEquals(-301_000, balance.unconfirmedDeltaSats());

        receive(wallet, node(wallet, KeyPurpose.RECEIVE, 2), 50_000, 0, false);
        balance = backend.balance(accountId(wallet), Set.of()).components();
        assertEquals(50_000, balance.pendingExternalSats());
        assertEquals(-251_000, balance.unconfirmedDeltaSats());
        assertEquals(749_000, balance.spendableSats() + balance.lockedSats() + balance.immatureSats());
    }

    @Test
    void frozenReservedAndImmatureOutputsAreNotSpendable() throws Exception {
        Wallet wallet = PwcTestWallets.software("Main", ScriptType.P2WPKH);
        open(wallet);
        BlockTransactionHashIndex frozen = receive(wallet, node(wallet, KeyPurpose.RECEIVE, 0), 100_000, 900, false);
        frozen.setStatus(Status.FROZEN);
        BlockTransactionHashIndex reserved = receive(wallet, node(wallet, KeyPurpose.RECEIVE, 1), 200_000, 900, false);
        receive(wallet, node(wallet, KeyPurpose.RECEIVE, 2), 300_000, 990, true);
        receive(wallet, node(wallet, KeyPurpose.RECEIVE, 3), 400_000, 900, false);
        OutPoint reservedOutPoint = new OutPoint(reserved.getHashAsString(), (int)reserved.getIndex());

        BalanceComponents balance = backend.balance(accountId(wallet), Set.of(reservedOutPoint)).components();
        assertEquals(1_000_000, balance.confirmedSats());
        assertEquals(400_000, balance.spendableSats());
        assertEquals(300_000, balance.lockedSats());
        assertEquals(300_000, balance.immatureSats());

        Map<Long, Utxo> utxos = new HashMap<>();
        backend.utxos(accountId(wallet), Set.of(reservedOutPoint)).forEach(u -> utxos.put(u.valueSats(), u));
        assertEquals(List.of(UtxoRestriction.FROZEN), utxos.get(100_000L).restrictions());
        assertEquals(List.of(UtxoRestriction.LOCKED), utxos.get(200_000L).restrictions());
        assertEquals(List.of(UtxoRestriction.IMMATURE), utxos.get(300_000L).restrictions());
        Utxo spendable = utxos.get(400_000L);
        assertTrue(spendable.spendable());
        assertEquals(List.of(), spendable.restrictions());
        assertEquals(101, spendable.confirmations());
        assertEquals(900, spendable.blockHeight());
        assertEquals(node(wallet, KeyPurpose.RECEIVE, 3).getAddress().toString(), spendable.address());
        assertFalse(spendable.isChange());
        assertTrue(backend.isUnspent(accountId(wallet), reservedOutPoint));
        assertEquals(200_000, backend.ownedOutput(accountId(wallet), reservedOutPoint).orElseThrow().valueSats());
    }

    @Test
    void balanceOfAnUnsyncedWalletIsIncompleteAndOfflineIsMarked() throws Exception {
        Wallet wallet = PwcTestWallets.software("Main", ScriptType.P2WPKH);
        open(wallet);
        host.connected = false;
        assertEquals(SyncState.OFFLINE, backend.balance(accountId(wallet), Set.of()).sync().state());
        wallet.setStoredBlockHeight(null);
        assertEquals(ErrorCode.SYNC_INCOMPLETE, code(() -> backend.balance(accountId(wallet), Set.of())));
    }

    @Test
    void transactionsAreNewestFirstWithAccountRelativeChange() throws Exception {
        Wallet wallet = PwcTestWallets.software("Main", ScriptType.P2WPKH);
        open(wallet);
        BlockTransactionHashIndex funding = receive(wallet, node(wallet, KeyPurpose.RECEIVE, 0), 400_000, 900, false);
        BlockTransactionHashIndex change = spend(wallet, funding, 300_000, node(wallet, KeyPurpose.CHANGE, 0), 99_000);

        List<TransactionRecord> records = backend.transactions(accountId(wallet));
        assertEquals(2, records.size());
        TransactionRecord pending = records.getFirst();
        assertEquals(change.getHashAsString(), pending.txid());
        assertEquals(TransactionStatus.UNCONFIRMED, pending.status());
        assertEquals(0, pending.confirmations());
        assertNull(pending.blockHeight());
        assertEquals(-301_000, pending.netChangeSats());
        assertEquals(1_000L, pending.feeSats());
        TransactionRecord confirmed = records.get(1);
        assertEquals(TransactionStatus.CONFIRMED, confirmed.status());
        assertEquals(101, confirmed.confirmations());
        assertEquals(900, confirmed.blockHeight());
        assertEquals(400_000, confirmed.netChangeSats());
    }

    @Test
    void createdAddressesAreLabelledAndNotHandedOutAgain() throws Exception {
        Wallet wallet = PwcTestWallets.software("Main", ScriptType.P2WPKH);
        open(wallet);
        AddressRecord current = backend.receiveAddress(accountId(wallet));
        assertEquals(current.address(), backend.receiveAddress(accountId(wallet)).address());
        assertEquals(AddressRole.RECEIVE, current.role());
        assertEquals(AddressState.UNUSED, current.state());

        AddressRecord created = backend.createAddress(accountId(wallet), "Invoice 7");
        assertEquals(current.address(), created.address());
        assertEquals("Invoice 7", created.label());
        assertNull(wallet.getWalletAddresses().get(Address.fromString(created.address())).getLabel(), "the label waits for the wallet thread");
        host.runWalletThread();
        assertEquals("Invoice 7", wallet.getWalletAddresses().get(Address.fromString(created.address())).getLabel());
        AddressRecord next = backend.createAddress(accountId(wallet), null);
        assertNotEquals(created.address(), next.address());
        assertEquals(PwcWalletBackend.DEFAULT_ADDRESS_LABEL, next.label());
        host.runWalletThread();
        assertTrue(backend.addresses(accountId(wallet)).stream().anyMatch(a -> a.address().equals(created.address()) && "Invoice 7".equals(a.label())));
        assertTrue(backend.ownsScript(accountId(wallet), Address.fromString(created.address()).getOutputScript().getProgram()));
        assertFalse(backend.ownsScript(accountId(wallet), foreignScript().getProgram()));
    }

    @Test
    void feeEstimatesComeOnlyFromTheConnectedNode() {
        assertEquals(12_000, backend.feeEstimate("regtest", 1).orElseThrow());
        assertEquals(5_000, backend.feeEstimate("regtest", 4).orElseThrow());
        assertEquals(2_500, backend.feeEstimate("regtest", 100).orElseThrow());
        assertTrue(backend.feeEstimate("mainnet", 1).isEmpty());
        host.connected = false;
        assertTrue(backend.feeEstimate("regtest", 1).isEmpty());
        host.connected = true;
        host.feeRates = null;
        assertTrue(backend.feeEstimate("regtest", 1).isEmpty());
    }

    @Test
    void createsAnUnsignedPsbtFromEligibleCoins() throws Exception {
        Wallet wallet = PwcTestWallets.software("Main", ScriptType.P2WPKH);
        open(wallet);
        BlockTransactionHashIndex small = receive(wallet, node(wallet, KeyPurpose.RECEIVE, 0), 100_000, 900, false);
        BlockTransactionHashIndex large = receive(wallet, node(wallet, KeyPurpose.RECEIVE, 1), 900_000, 900, false);
        OutPoint smallOutPoint = new OutPoint(small.getHashAsString(), 0);
        OutPoint largeOutPoint = new OutPoint(large.getHashAsString(), 0);
        TxOut payment = new TxOut(250_000, foreignScript().getProgram());

        CreatedPsbt created = backend.createPsbt(accountId(wallet), new CreateRequest(List.of(payment), 2_000, null, true), Set.of(smallOutPoint));
        assertEquals(List.of(largeOutPoint), created.inputs());
        PSBT psbt = new PSBT(created.psbt());
        assertFalse(psbt.isSigned());
        assertNotNull(psbt.getPsbtInputs().getFirst().getWitnessUtxo());
        assertTrue(psbt.getTransaction().getOutputs().stream().anyMatch(o -> o.getValue() == 250_000 && Arrays.equals(o.getScriptBytes(), payment.scriptPubKey())));
        assertTrue(psbt.getVersion() == null || psbt.getVersion() == 0, "the SDK signs PSBT version 0 only");
        assertEquals(1, created.changeOutputIndexes().size());
        Script change = psbt.getTransaction().getOutputs().get(created.changeOutputIndexes().getFirst()).getScript();
        assertEquals(KeyPurpose.CHANGE, wallet.getWalletOutputScripts().get(change).getKeyPurpose());
        assertEquals(created.inputs().getFirst().txid(), psbt.getTransaction().getInputs().getFirst().getOutpoint().getHash().toString());
        assertTrue(created.fee() > 0 && created.vsize() > 0);

        CreatedPsbt selected = backend.createPsbt(accountId(wallet), new CreateRequest(List.of(new TxOut(50_000, payment.scriptPubKey())), 2_000, List.of(smallOutPoint), true), Set.of());
        assertEquals(List.of(smallOutPoint), selected.inputs());

        assertEquals(ErrorCode.INSUFFICIENT_FUNDS, code(() -> backend.createPsbt(accountId(wallet),
                new CreateRequest(List.of(new TxOut(5_000_000, payment.scriptPubKey())), 2_000, null, true), Set.of())));
    }

    private PSBT twoInputPsbt(Wallet wallet) throws Exception {
        receive(wallet, node(wallet, KeyPurpose.RECEIVE, 0), 100_000, 900, false);
        receive(wallet, node(wallet, KeyPurpose.RECEIVE, 1), 100_000, 900, false);
        CreatedPsbt created = backend.createPsbt(accountId(wallet), new CreateRequest(List.of(new TxOut(150_000, foreignScript().getProgram())), 2_000, null, true), Set.of());
        return new PSBT(created.psbt());
    }

    @Test
    void signsOnlyTheRequestedInputs() throws Exception {
        Wallet wallet = PwcTestWallets.software("Main", ScriptType.P2WPKH);
        open(wallet);
        PSBT psbt = twoInputPsbt(wallet);
        assertEquals(2, psbt.getPsbtInputs().size());

        SignedPsbt signed = backend.signPsbt(accountId(wallet), psbt.serialize(), List.of(1));
        assertEquals(List.of(1), signed.signedInputs());
        PSBT result = new PSBT(signed.psbt());
        assertTrue(result.getPsbtInputs().get(0).getPartialSignatures().isEmpty());
        assertEquals(1, result.getPsbtInputs().get(1).getPartialSignatures().size());
        assertTrue(wallet.getKeystores().getFirst().hasPrivateKey(), "the open wallet keeps its keys");
    }

    @Test
    void encryptedWalletsSignOnlyWithTheCopyUnlockedForTheRequest() throws Exception {
        Wallet wallet = PwcTestWallets.software("Main", ScriptType.P2WPKH);
        open(wallet);
        PSBT psbt = twoInputPsbt(wallet);
        wallet.encrypt(new Argon2KeyDeriver().deriveKey("pass"));

        assertEquals(ErrorCode.SIGNER_UNAVAILABLE, code(() -> backend.signPsbt(accountId(wallet), psbt.serialize(), List.of(0, 1))));

        String subject = PwcWalletBackend.psbtSubject(psbt.serialize(), List.of(0, 1));
        Wallet decrypted = decrypted(wallet);
        backend.unlocked(accountId(wallet), subject, decrypted);
        assertEquals(List.of(0, 1), backend.signPsbt(accountId(wallet), psbt.serialize(), List.of(0, 1)).signedInputs());
        assertTrue(isCleared(decrypted), "the decrypted copy is cleared after one signature");
        assertEquals(ErrorCode.SIGNER_UNAVAILABLE, code(() -> backend.signPsbt(accountId(wallet), psbt.serialize(), List.of(0, 1))));

        Wallet other = decrypted(wallet);
        backend.unlocked(accountId(wallet), subject, other);
        assertEquals(ErrorCode.SIGNER_UNAVAILABLE, code(() -> backend.signPsbt(accountId(wallet), psbt.serialize(), List.of(0))), "other inputs than those approved");
        assertTrue(isCleared(other), "a copy unlocked for another request is cleared, not used");
        assertEquals(ErrorCode.SIGNER_UNAVAILABLE, code(() -> backend.signPsbt(accountId(wallet), psbt.serialize(), List.of(0, 1))), "and not kept");

        Wallet late = decrypted(wallet);
        backend.unlocked(accountId(wallet), subject, late);
        clock.advance(PwcWalletBackend.APPROVAL_LIFETIME.plusSeconds(1));
        assertEquals(ErrorCode.SIGNER_UNAVAILABLE, code(() -> backend.signPsbt(accountId(wallet), psbt.serialize(), List.of(0, 1))), "an expired unlock");
        assertTrue(isCleared(late));

        Wallet replaced = decrypted(wallet);
        backend.unlocked(accountId(wallet), subject, replaced);
        backend.unlocked(accountId(wallet), subject, decrypted(wallet));
        assertTrue(isCleared(replaced), "a replaced copy is cleared");
        backend.discardApproval(accountId(wallet));
        assertEquals(ErrorCode.SIGNER_UNAVAILABLE, code(() -> backend.signPsbt(accountId(wallet), psbt.serialize(), List.of(0, 1))));
    }

    @Test
    void unusedApprovalsAreClearedAtExpiryAndOnClose() {
        Wallet expired = PwcTestWallets.software("Expired", ScriptType.P2WPKH);
        backend.unlocked("expired", "message", expired);
        clock.advance(PwcWalletBackend.APPROVAL_LIFETIME);
        backend.expireApprovals();
        assertTrue(isCleared(expired));

        Wallet pending = PwcTestWallets.software("Pending", ScriptType.P2WPKH);
        backend.unlocked("pending", "message", pending);
        backend.close();
        assertTrue(isCleared(pending));

        Wallet late = PwcTestWallets.software("Late", ScriptType.P2WPKH);
        backend.unlocked("late", "message", late);
        assertTrue(isCleared(late));
    }

    private static Wallet decrypted(Wallet encrypted) {
        Wallet copy = encrypted.copy();
        copy.decrypt("pass");
        return copy;
    }

    private static boolean isCleared(Wallet wallet) {
        return wallet.getKeystores().getFirst().getSeed().getMnemonicCode().isEmpty();
    }

    @Test
    void encryptedWalletsSignOnlyTheMessageUnlockedForTheRequest() throws Exception {
        Wallet wallet = PwcTestWallets.software("Main", ScriptType.P2WPKH);
        open(wallet);
        Address address = node(wallet, KeyPurpose.RECEIVE, 0).getAddress();
        wallet.encrypt(new Argon2KeyDeriver().deriveKey("pass"));
        byte[] script = address.getOutputScript().getProgram();

        backend.unlocked(accountId(wallet), PwcWalletBackend.messageSubject(address.toString(), "Approved", MessageFormat.BIP322_SIMPLE), decrypted(wallet));
        assertEquals(ErrorCode.SIGNER_UNAVAILABLE, code(() -> backend.signMessage(accountId(wallet), script, "Something else", MessageFormat.BIP322_SIMPLE)));

        backend.unlocked(accountId(wallet), PwcWalletBackend.messageSubject(address.toString(), "Approved", MessageFormat.BIP322_SIMPLE), decrypted(wallet));
        byte[] signature = backend.signMessage(accountId(wallet), script, "Approved", MessageFormat.BIP322_SIMPLE);
        assertTrue(Bip322.verifyMessageBip322(ScriptType.P2WPKH, address, "Approved", Base64.getEncoder().encodeToString(signature)));
    }

    private static PSBT deviceSign(Wallet software, PSBT request) throws Exception {
        PSBT copy = new PSBT(request.serialize());
        software.copy().sign(copy);
        return copy;
    }

    @Test
    void hardwareSignaturesComeOnlyFromTheDeviceApprovalForThatPsbt() throws Exception {
        Wallet software = PwcTestWallets.software("Main", ScriptType.P2WPKH);
        Wallet device = PwcTestWallets.hardware(software);
        open(device);
        PSBT psbt = twoInputPsbt(device);
        String accountId = accountId(device);

        assertEquals(ErrorCode.SIGNER_UNAVAILABLE, code(() -> backend.signPsbt(accountId, psbt.serialize(), List.of(0))), "no device signature was taken for this request");

        backend.deviceSigned(accountId, PwcWalletBackend.psbtSubject(psbt.serialize(), List.of(0)), deviceSign(software, psbt));
        SignedPsbt signed = within(Duration.ofSeconds(2), () -> backend.signPsbt(accountId, psbt.serialize(), List.of(0)));
        PSBT result = new PSBT(signed.psbt());
        assertEquals(List.of(0), signed.signedInputs());
        assertEquals(1, result.getPsbtInputs().get(0).getPartialSignatures().size());
        assertTrue(result.getPsbtInputs().get(1).getPartialSignatures().isEmpty());
        assertEquals(ErrorCode.SIGNER_UNAVAILABLE, code(() -> backend.signPsbt(accountId, psbt.serialize(), List.of(0))), "a device signature serves one request");

        PSBT other = new PSBT(backend.createPsbt(accountId, new CreateRequest(List.of(new TxOut(120_000, foreignScript().getProgram())), 3_000, null, true), Set.of()).psbt());
        assertFalse(Arrays.equals(psbt.serialize(), other.serialize()));
        backend.deviceSigned(accountId, PwcWalletBackend.psbtSubject(psbt.serialize(), List.of(0)), deviceSign(software, psbt));
        assertEquals(ErrorCode.SIGNER_UNAVAILABLE, code(() -> backend.signPsbt(accountId, other.serialize(), List.of(0))), "another PSBT than the one approved");

        backend.deviceSigned(accountId, PwcWalletBackend.psbtSubject(psbt.serialize(), List.of(0)), deviceSign(software, psbt));
        clock.advance(PwcWalletBackend.APPROVAL_LIFETIME.plusSeconds(1));
        assertEquals(ErrorCode.SIGNER_UNAVAILABLE, code(() -> backend.signPsbt(accountId, psbt.serialize(), List.of(0))), "an expired device signature");

        PSBT wrongSignatures = new PSBT(psbt.serialize());
        wrongSignatures.getPsbtInputs().get(0).getPartialSignatures().putAll(deviceSign(software, other).getPsbtInputs().get(0).getPartialSignatures());
        backend.deviceSigned(accountId, PwcWalletBackend.psbtSubject(psbt.serialize(), List.of(0)), wrongSignatures);
        assertEquals(ErrorCode.PSBT_VALIDATION_FAILED, code(() -> backend.signPsbt(accountId, psbt.serialize(), List.of(0))), "signatures that do not verify for this PSBT");
    }

    @Test
    void signsExactMessagesInTheAccountFormat() throws Exception {
        Wallet segwit = PwcTestWallets.software("Segwit", ScriptType.P2WPKH);
        Wallet legacy = PwcTestWallets.software("Legacy", ScriptType.P2PKH);
        open(segwit);
        open(legacy);
        String message = "  Prove control\nof this address  ";

        Address segwitAddress = node(segwit, KeyPurpose.RECEIVE, 0).getAddress();
        byte[] bip322 = backend.signMessage(accountId(segwit), segwitAddress.getOutputScript().getProgram(), message, MessageFormat.BIP322_SIMPLE);
        assertTrue(Bip322.verifyMessageBip322(ScriptType.P2WPKH, segwitAddress, message, Base64.getEncoder().encodeToString(bip322)));

        Address legacyAddress = node(legacy, KeyPurpose.RECEIVE, 0).getAddress();
        byte[] compact = backend.signMessage(accountId(legacy), legacyAddress.getOutputScript().getProgram(), message, MessageFormat.LEGACY);
        assertEquals(65, compact.length);
        ECKey signer = ECKey.signedMessageToKey(message, Base64.getEncoder().encodeToString(compact), false);
        assertEquals(legacyAddress, ScriptType.P2PKH.getAddress(PolicyType.SINGLE_HD, signer));

        assertEquals(ErrorCode.INVALID_ARGUMENT, code(() -> backend.signMessage(accountId(segwit), foreignScript().getProgram(), message, MessageFormat.BIP322_SIMPLE)));
    }

    private Transaction transaction(int seed) {
        return transaction(seed, 1_000);
    }

    private Transaction transaction(int seed, long value) {
        Transaction tx = new Transaction();
        tx.addInput(Sha256Hash.wrap(String.format("%02x", seed).repeat(32)), 0, new Script(new byte[0]));
        tx.addOutput(value, foreignScript());
        return tx;
    }

    /** The transaction as a Client returns it after finalizing: the same inputs and outputs with a witness. */
    private static Transaction finalized(Transaction unsigned) {
        Transaction tx = new Transaction(unsigned.bitcoinSerialize());
        tx.getInputs().getFirst().setWitness(new TransactionWitness(tx, List.of(new byte[71], new byte[33])));
        return tx;
    }

    @Test
    void broadcastReturnsTheTxidInDisplayOrderAndReportsFailures() throws Exception {
        Transaction tx = transaction(0xab);
        byte[] raw = tx.bitcoinSerialize();
        byte[] internal = Sha256Hash.hashTwice(raw);
        StringBuilder display = new StringBuilder();
        for(int i = internal.length - 1; i >= 0; i--) {
            display.append(String.format("%02x", internal[i]));
        }

        assertEquals(display.toString(), within(Duration.ofSeconds(2), () -> backend.broadcast("regtest", raw)));
        assertEquals(1, host.broadcasts.size());

        host.broadcaster = t -> {
            throw new PwcException(ErrorCode.TRANSACTION_REJECTED, "min relay fee not met");
        };
        assertEquals(ErrorCode.TRANSACTION_REJECTED, code(() -> backend.broadcast("regtest", raw)));
        host.broadcaster = t -> {
            throw new PwcException(ErrorCode.NETWORK_UNAVAILABLE, "The server cannot be reached");
        };
        assertEquals(ErrorCode.NETWORK_UNAVAILABLE, code(() -> backend.broadcast("regtest", raw)));
        host.broadcaster = t -> Sha256Hash.wrap("cd".repeat(32));
        assertEquals(ErrorCode.TRANSACTION_REJECTED, code(() -> backend.broadcast("regtest", raw)), "the server named another transaction");
        host.broadcaster = t -> null;
        assertEquals(ErrorCode.TRANSACTION_REJECTED, code(() -> backend.broadcast("regtest", raw)));
        host.broadcaster = t -> {
            throw new IllegalStateException("Service must only be used from the FX Application Thread");
        };
        assertEquals(ErrorCode.INTERNAL_ERROR, code(() -> backend.broadcast("regtest", raw)));

        host.broadcaster = null;
        host.connected = false;
        assertEquals(ErrorCode.NETWORK_UNAVAILABLE, code(() -> backend.broadcast("regtest", raw)));
        assertEquals(ErrorCode.NETWORK_MISMATCH, code(() -> backend.broadcast("mainnet", raw)));
        assertEquals(ErrorCode.INVALID_ARGUMENT, code(() -> backend.broadcast("regtest", new byte[] {1, 2, 3})));
        assertEquals(1, host.broadcasts.size());
    }

    @Test
    void aBroadcastThatDoesNotAnswerInTimeIsNetworkUnavailable() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        host.broadcaster = t -> {
            try {
                release.await();
            } catch(InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return t.getTxId();
        };
        try {
            long start = System.nanoTime();
            assertEquals(ErrorCode.NETWORK_UNAVAILABLE, code(() -> within(Duration.ofSeconds(2), () -> backend.broadcast("regtest", transaction(1).bitcoinSerialize()))));
            assertTrue(Duration.ofNanos(System.nanoTime() - start).compareTo(BROADCAST_TIMEOUT) >= 0);
        } finally {
            release.countDown();
        }
    }

    @Test
    void mainnetAndSignetBroadcastOnlyTransactionsClearedInASigningPrompt() throws Exception {
        for(Network network : List.of(Network.SIGNET, Network.MAINNET)) {
            Network.set(network);
            backend = new PwcWalletBackend(host, ids, clock, BROADCAST_TIMEOUT);
            host.broadcasts.clear();
            Transaction unsigned = transaction(0x11);
            byte[] raw = finalized(unsigned).bitcoinSerialize();

            assertEquals(ErrorCode.POLICY_VIOLATION, code(() -> within(Duration.ofSeconds(2), () -> backend.broadcast(network.getName(), raw))),
                    "a transaction whose replay risk was never cleared in this wallet");
            assertTrue(host.broadcasts.isEmpty());
            assertTrue(host.walletThread.isEmpty(), "no dialog is opened");

            backend.replayCleared(unsigned);
            host.broadcaster = t -> {
                throw new PwcException(ErrorCode.NETWORK_UNAVAILABLE, "The server cannot be reached");
            };
            assertEquals(ErrorCode.NETWORK_UNAVAILABLE, code(() -> backend.broadcast(network.getName(), raw)));
            host.broadcaster = null;
            assertEquals(unsigned.getTxId().toString(), backend.broadcast(network.getName(), raw), "a failed broadcast keeps the clearance");
            assertEquals(1, host.broadcasts.size());
            assertEquals(ErrorCode.POLICY_VIOLATION, code(() -> backend.broadcast(network.getName(), raw)), "the clearance serves one broadcast");

            Transaction otherOutputs = transaction(0x11, 900);
            backend.replayCleared(unsigned);
            assertEquals(ErrorCode.POLICY_VIOLATION, code(() -> backend.broadcast(network.getName(), finalized(otherOutputs).bitcoinSerialize())), "another spend of the same inputs");

            clock.advance(PwcWalletBackend.REPLAY_CLEARANCE_LIFETIME.plusSeconds(1));
            assertEquals(ErrorCode.POLICY_VIOLATION, code(() -> backend.broadcast(network.getName(), raw)), "an expired clearance");
            assertEquals(1, host.broadcasts.size());
        }
    }

    @Test
    void everyBackendMethodAnswersWhileTheWalletThreadIsBlocked() throws Exception {
        Wallet wallet = PwcTestWallets.software("Main", ScriptType.P2WPKH);
        open(wallet);
        PSBT psbt = twoInputPsbt(wallet);
        String id = accountId(wallet);
        Address address = node(wallet, KeyPurpose.RECEIVE, 0).getAddress();
        BlockTransactionHashIndex utxo = wallet.getWalletUtxos().keySet().iterator().next();
        OutPoint outPoint = new OutPoint(utxo.getHashAsString(), (int)utxo.getIndex());
        Duration limit = Duration.ofSeconds(2);

        assertEquals(1, within(limit, backend::wallets).size());
        assertTrue(within(limit, () -> backend.account(id)).isPresent());
        assertEquals(200_000, within(limit, () -> backend.balance(id, Set.of())).components().confirmedSats());
        assertNotNull(within(limit, () -> backend.receiveAddress(id)));
        assertNotNull(within(limit, () -> backend.createAddress(id, "Blocked")));
        assertFalse(within(limit, () -> backend.addresses(id)).isEmpty());
        assertEquals(2, within(limit, () -> backend.utxos(id, Set.of())).size());
        assertEquals(2, within(limit, () -> backend.transactions(id)).size());
        assertTrue(within(limit, () -> backend.ownsScript(id, address.getOutputScript().getProgram())));
        assertTrue(within(limit, () -> backend.isUnspent(id, outPoint)));
        assertTrue(within(limit, () -> backend.ownedOutput(id, outPoint)).isPresent());
        assertTrue(within(limit, () -> backend.feeEstimate("regtest", 2)).isPresent());
        assertNotNull(within(limit, () -> backend.createPsbt(id, new CreateRequest(List.of(new TxOut(10_000, foreignScript().getProgram())), 2_000, null, true), Set.of())));
        assertEquals(List.of(0, 1), within(limit, () -> backend.signPsbt(id, psbt.serialize(), List.of(0, 1))).signedInputs());
        assertNotNull(within(limit, () -> backend.signMessage(id, address.getOutputScript().getProgram(), "Blocked", MessageFormat.BIP322_SIMPLE)));
        assertNotNull(within(limit, () -> backend.broadcast("regtest", transaction(2).bitcoinSerialize())));
        assertEquals(1, host.walletThread.size(), "only the new address label was handed to the wallet thread, without waiting for it");
    }

    @Test
    void theBackendReadsThePublishedViewNotTheLiveWallets() throws Exception {
        Wallet wallet = PwcTestWallets.software("Main", ScriptType.P2WPKH);
        open(wallet);
        BlockTransactionHashIndex first = receive(wallet, node(wallet, KeyPurpose.RECEIVE, 0), 100_000, 900, false);
        first.setStatus(Status.FROZEN);
        host.pinned = host.capture();

        receive(wallet, node(wallet, KeyPurpose.RECEIVE, 1), 300_000, 900, false);
        first.setStatus(null);
        BalanceComponents pinned = backend.balance(accountId(wallet), Set.of()).components();
        assertEquals(100_000, pinned.confirmedSats(), "a change after publication is not seen");
        assertEquals(100_000, pinned.lockedSats(), "the view keeps the frozen status");

        host.pinned = null;
        BalanceComponents current = backend.balance(accountId(wallet), Set.of()).components();
        assertEquals(400_000, current.confirmedSats());
        assertEquals(400_000, current.spendableSats());
    }

    @Test
    void aStaleViewNeverHandsOutAnAddressTwice() throws Exception {
        Wallet wallet = PwcTestWallets.software("Main", ScriptType.P2WPKH);
        open(wallet);
        host.pinned = host.capture();
        String id = accountId(wallet);

        AddressRecord first = backend.createAddress(id, "One");
        AddressRecord second = backend.createAddress(id, "Two");
        assertNotEquals(first.address(), second.address());
        AddressRecord current = backend.receiveAddress(id);
        assertNotEquals(first.address(), current.address());
        assertNotEquals(second.address(), current.address());

        host.runWalletThread();
        host.pinned = null;
        AddressRecord third = backend.createAddress(id, "Three");
        assertFalse(List.of(first.address(), second.address()).contains(third.address()));
        assertEquals("Two", wallet.getWalletAddresses().get(Address.fromString(second.address())).getLabel());
    }

    @Test
    void noOtherExceptionThanPwcExceptionReachesTheSdk() throws Exception {
        Wallet wallet = PwcTestWallets.software("Main", ScriptType.P2WPKH);
        open(wallet);
        String id = accountId(wallet);
        OutPoint outPoint = new OutPoint("ab".repeat(32), 0);
        byte[] script = foreignScript().getProgram();
        byte[] psbt = twoInputPsbt(wallet).serialize();
        byte[] raw = transaction(3).bitcoinSerialize();

        for(Throwable failure : List.of(new IllegalStateException("Service must only be used from the FX Application Thread"), new NoClassDefFoundError("javafx/application/Platform"))) {
            host.viewFailure = failure;
            List<Executable> declaring = List.of(() -> backend.balance(id, Set.of()), () -> backend.receiveAddress(id), () -> backend.createAddress(id, null),
                    () -> backend.utxos(id, Set.of()), () -> backend.transactions(id),
                    () -> backend.createPsbt(id, new CreateRequest(List.of(new TxOut(10_000, script)), 2_000, null, true), Set.of()),
                    () -> backend.signPsbt(id, psbt, List.of(0)), () -> backend.signMessage(id, script, "m", MessageFormat.BIP322_SIMPLE),
                    () -> backend.broadcast("regtest", raw));
            for(Executable executable : declaring) {
                assertEquals(ErrorCode.INTERNAL_ERROR, code(executable));
            }
            assertEquals(List.of(), backend.wallets());
            assertTrue(backend.account(id).isEmpty());
            assertEquals(List.of(), backend.addresses(id));
            assertFalse(backend.ownsScript(id, script));
            assertFalse(backend.isUnspent(id, outPoint));
            assertTrue(backend.ownedOutput(id, outPoint).isEmpty());
            assertTrue(backend.feeEstimate("regtest", 1).isEmpty());
        }
    }

    @Test
    void closedWalletsAreNotFound() {
        Wallet wallet = PwcTestWallets.software("Main", ScriptType.P2WPKH);
        open(wallet);
        String id = accountId(wallet);
        host.forms.clear();
        assertTrue(backend.account(id).isEmpty());
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND, code(() -> backend.balance(id, Set.of())));
        assertEquals(List.of(), backend.addresses(id));
    }
}
