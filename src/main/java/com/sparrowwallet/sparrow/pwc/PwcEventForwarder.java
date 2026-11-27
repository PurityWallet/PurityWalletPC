package com.sparrowwallet.sparrow.pwc;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.wallet.WalletForm;
import org.bitcoinpurity.pwc.provider.BackendEvent;
import org.bitcoinpurity.pwc.provider.TransactionStatus;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Reports wallet changes to the Provider. Subscribers run on the JavaFX thread and only hand events on. */
public class PwcEventForwarder {
    private final PwcIds ids;
    private final Supplier<List<WalletForm>> openWalletForms;
    private final Consumer<BackendEvent> notifier;

    public PwcEventForwarder(PwcIds ids, Supplier<List<WalletForm>> openWalletForms, Consumer<BackendEvent> notifier) {
        this.ids = ids;
        this.openWalletForms = openWalletForms;
        this.notifier = notifier;
    }

    private static Wallet master(Wallet wallet) {
        return wallet.isMasterWallet() ? wallet : wallet.getMasterWallet();
    }

    private Optional<String> walletId(Wallet wallet) {
        Wallet master = master(wallet);
        boolean exposed = openWalletForms.get().stream().anyMatch(form -> form.getMasterWallet() == master && PwcWalletBackend.isSupported(form.getWallet()));
        return exposed ? Optional.of(ids.walletId(master)) : Optional.empty();
    }

    private Optional<String> accountId(Wallet wallet) {
        boolean exposed = PwcWalletBackend.isSupported(wallet) && openWalletForms.get().stream().anyMatch(form -> form.getWallet() == wallet);
        return exposed ? Optional.of(ids.accountId(wallet)) : Optional.empty();
    }

    @Subscribe
    public void walletLock(WalletLockEvent event) {
        walletId(event.getWallet()).ifPresent(id -> notifier.accept(new BackendEvent.WalletLocked(id)));
    }

    @Subscribe
    public void walletUnlock(WalletUnlockEvent event) {
        walletId(event.getWallet()).ifPresent(id -> notifier.accept(new BackendEvent.WalletUnlocked(id)));
    }

    @Subscribe
    public void walletHistoryStatus(WalletHistoryStatusEvent event) {
        walletId(event.getWallet()).ifPresent(id -> notifier.accept(new BackendEvent.WalletSyncChanged(id)));
    }

    @Subscribe
    public void connection(ConnectionEvent event) {
        allWalletsSyncChanged();
    }

    @Subscribe
    public void disconnection(DisconnectionEvent event) {
        allWalletsSyncChanged();
    }

    private void allWalletsSyncChanged() {
        Set<String> walletIds = new LinkedHashSet<>();
        for(WalletForm form : openWalletForms.get()) {
            walletId(form.getWallet()).ifPresent(walletIds::add);
        }
        walletIds.forEach(id -> notifier.accept(new BackendEvent.WalletSyncChanged(id)));
    }

    @Subscribe
    public void walletHistoryChanged(WalletHistoryChangedEvent event) {
        accountId(event.getWallet()).ifPresent(id -> notifier.accept(new BackendEvent.BalanceChanged(id)));
    }

    @Subscribe
    public void walletUtxoStatusChanged(WalletUtxoStatusChangedEvent event) {
        accountId(event.getWallet()).ifPresent(id -> notifier.accept(new BackendEvent.BalanceChanged(id)));
    }

    @Subscribe
    public void newWalletTransactions(NewWalletTransactionsEvent event) {
        accountId(event.getWallet()).ifPresent(id -> {
            for(BlockTransaction transaction : event.getBlockTransactions()) {
                TransactionStatus status = transaction.getHeight() > 0 ? TransactionStatus.CONFIRMED : TransactionStatus.UNCONFIRMED;
                notifier.accept(new BackendEvent.TransactionChanged(id, transaction.getHashAsString(), status));
            }
        });
    }
}
