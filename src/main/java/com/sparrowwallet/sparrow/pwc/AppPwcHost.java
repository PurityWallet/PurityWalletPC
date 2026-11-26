package com.sparrowwallet.sparrow.pwc;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.control.DeviceSignDialog;
import com.sparrowwallet.sparrow.control.LegacyReplayGuard;
import com.sparrowwallet.sparrow.event.WalletHistoryStatusEvent;
import com.sparrowwallet.sparrow.net.ElectrumServer;
import com.sparrowwallet.sparrow.net.LegacyUtxoChecker;
import com.sparrowwallet.sparrow.wallet.WalletForm;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.stage.Window;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** The running desktop application as seen by the PWC backend. */
public class AppPwcHost implements PwcHost {
    private static final long WALLET_THREAD_TIMEOUT_SECONDS = 30;

    private final Set<Wallet> syncing = new HashSet<>();
    private final Map<Wallet, Instant> synced = new HashMap<>();

    @Override
    public <T> T onWalletThread(Callable<T> action) throws Exception {
        if(Platform.isFxApplicationThread()) {
            return action.call();
        }
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        try {
            //A bounded wait, since the JavaFX thread stops running tasks when the application quits
            return task.get(WALLET_THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch(ExecutionException e) {
            throw e.getCause() instanceof Exception cause ? cause : e;
        }
    }

    @Override
    public List<WalletForm> openWalletForms() {
        return AppServices.get().getOpenWalletForms();
    }

    private static Wallet master(Wallet wallet) {
        return wallet.isMasterWallet() ? wallet : wallet.getMasterWallet();
    }

    @Subscribe
    public void walletHistoryStatus(WalletHistoryStatusEvent event) {
        Wallet master = master(event.getWallet());
        if(event.isLoading()) {
            syncing.add(master);
        } else {
            syncing.remove(master);
            if(event.getErrorMessage() == null) {
                synced.put(master, Instant.now());
            }
        }
    }

    @Override
    public boolean isSyncing(Wallet masterWallet) {
        return syncing.contains(masterWallet);
    }

    @Override
    public Instant syncedAt(Wallet masterWallet) {
        return synced.get(masterWallet);
    }

    @Override
    public Integer currentBlockHeight() {
        return AppServices.getCurrentBlockHeight();
    }

    @Override
    public boolean isConnected() {
        return AppServices.isConnected();
    }

    @Override
    public Map<Integer, Double> targetBlockFeeRates() {
        return AppServices.getTargetBlockFeeRates();
    }

    @Override
    public double minimumRelayFeeRate() {
        return AppServices.getMinimumRelayFeeRate();
    }

    private static <T> T waitFor(CompletableFuture<T> future) throws Exception {
        try {
            return future.get();
        } catch(ExecutionException e) {
            throw e.getCause() instanceof Exception cause ? cause : e;
        }
    }

    @Override
    public PSBT signWithDevice(Wallet wallet, PSBT psbt) {
        CompletableFuture<PSBT> signed = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                List<String> fingerprints = wallet.getKeystores().stream().map(Keystore::getKeyDerivation).map(derivation -> derivation.getMasterFingerprint()).toList();
                DeviceSignDialog dialog = new DeviceSignDialog(wallet, fingerprints, psbt);
                Window owner = AppServices.getActiveWindow();
                if(owner != null) {
                    dialog.initOwner(owner);
                }
                signed.complete(dialog.showAndWait().orElse(null));
            } catch(Throwable e) {
                signed.completeExceptionally(e);
            }
        });
        try {
            return waitFor(signed);
        } catch(Exception e) {
            throw new IllegalStateException("The device signature failed", e);
        }
    }

    @Override
    public boolean acceptReplayRisk(Transaction transaction) {
        CompletableFuture<Boolean> accepted = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                Map<Sha256Hash, BlockTransaction> previous = new HashMap<>();
                openWalletForms().forEach(form -> previous.putAll(form.getWallet().getWalletTransactions()));
                List<LegacyUtxoChecker.Utxo> inputs = LegacyUtxoChecker.inputs(transaction, null, null, previous);
                new LegacyReplayGuard().check(inputs, () -> true, new Button(), () -> {}).whenComplete((continued, error) -> accepted.complete(error == null && continued));
            } catch(Throwable e) {
                accepted.complete(false);
            }
        });
        try {
            return waitFor(accepted);
        } catch(Exception e) {
            return false;
        }
    }

    @Override
    public Sha256Hash broadcast(Transaction transaction) throws Exception {
        return new ElectrumServer().broadcastTransaction(transaction, null);
    }
}
