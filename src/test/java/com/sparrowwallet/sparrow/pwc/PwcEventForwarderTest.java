package com.sparrowwallet.sparrow.pwc;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.policy.Policy;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.StandardAccount;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.wallet.TransactionEntry;
import com.sparrowwallet.sparrow.wallet.WalletForm;
import org.bitcoinpurity.pwc.provider.BackendEvent;
import org.bitcoinpurity.pwc.provider.TransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PwcEventForwarderTest {
    @TempDir
    File dir;

    private final List<WalletForm> forms = new ArrayList<>();
    private final List<BackendEvent> sent = new ArrayList<>();
    private PwcIds ids;
    private PwcEventForwarder forwarder;
    private Wallet master;
    private Wallet child;

    @BeforeEach
    void setUp() throws Exception {
        Network.set(Network.REGTEST);
        ids = PwcIds.load(new File(dir, "pwc"));
        forwarder = new PwcEventForwarder(ids, () -> forms, sent::add);
        master = PwcTestWallets.software("Master", ScriptType.P2WPKH);
        master.setStoredBlockHeight(1000);
        child = master.addChildWallet(StandardAccount.ACCOUNT_1);
        forms.add(new WalletForm(new Storage(new File(dir, "master")), master));
        forms.add(new WalletForm(new Storage(new File(dir, "master")), child));
    }

    @Test
    void lockAndSyncEventsNameTheWallet() {
        String walletId = ids.walletId(master);
        forwarder.walletLock(new WalletLockEvent(master));
        forwarder.walletUnlock(new WalletUnlockEvent(master));
        forwarder.walletHistoryStatus(new WalletHistoryStartedEvent(child, null));
        forwarder.walletHistoryStatus(new WalletHistoryFinishedEvent(child));
        assertEquals(List.of(new BackendEvent.WalletLocked(walletId), new BackendEvent.WalletUnlocked(walletId),
                new BackendEvent.WalletSyncChanged(walletId), new BackendEvent.WalletSyncChanged(walletId)), sent);
    }

    @Test
    void connectionChangesReachEveryOpenWalletOnce() {
        forwarder.connection(null);
        forwarder.disconnection(new DisconnectionEvent());
        String walletId = ids.walletId(master);
        assertEquals(List.of(new BackendEvent.WalletSyncChanged(walletId), new BackendEvent.WalletSyncChanged(walletId)), sent);
    }

    @Test
    void historyAndCoinChangesNameTheAccount() {
        forwarder.walletHistoryChanged(new WalletHistoryChangedEvent(child, null, List.of(), List.of()));
        forwarder.walletUtxoStatusChanged(new WalletUtxoStatusChangedEvent(master, List.of()));
        assertEquals(List.of(new BackendEvent.BalanceChanged(ids.accountId(child)), new BackendEvent.BalanceChanged(ids.accountId(master))), sent);
    }

    @Test
    void newTransactionsCarryTheirStatus() {
        Sha256Hash pending = Sha256Hash.wrap("11".repeat(32));
        Sha256Hash mined = Sha256Hash.wrap("22".repeat(32));
        List<TransactionEntry> entries = List.of(
                new TransactionEntry(master, new BlockTransaction(pending, 0, null, null, null), Map.of(), Map.of()),
                new TransactionEntry(master, new BlockTransaction(mined, 900, new Date(), null, null), Map.of(), Map.of()));
        forwarder.newWalletTransactions(new NewWalletTransactionsEvent(master, entries));
        String accountId = ids.accountId(master);
        assertEquals(Set.of(new BackendEvent.TransactionChanged(accountId, pending.toString(), TransactionStatus.UNCONFIRMED),
                new BackendEvent.TransactionChanged(accountId, mined.toString(), TransactionStatus.CONFIRMED)), new HashSet<>(sent));
    }

    @Test
    void walletsWithoutPwcAccountsAreSilent() {
        Wallet nested = PwcTestWallets.software("Nested", ScriptType.P2SH_P2WPKH);
        forms.add(new WalletForm(new Storage(new File(dir, "nested")), nested));
        forwarder.walletLock(new WalletLockEvent(nested));
        forwarder.walletHistoryChanged(new WalletHistoryChangedEvent(nested, null, List.of(), List.of()));
        forwarder.walletLock(new WalletLockEvent(PwcTestWallets.software("Closed", ScriptType.P2WPKH, PwcTestWallets.OTHER_SEED)));
        assertEquals(List.of(), sent);
    }
}
