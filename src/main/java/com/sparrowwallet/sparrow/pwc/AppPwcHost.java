package com.sparrowwallet.sparrow.pwc;

import com.github.arteam.simplejsonrpc.client.exception.JsonRpcException;
import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.event.WalletHistoryStatusEvent;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.net.ElectrumServer;
import com.sparrowwallet.sparrow.net.ServerException;
import com.sparrowwallet.sparrow.wallet.WalletForm;
import javafx.application.Platform;
import org.bitcoinpurity.pwc.provider.ErrorCode;
import org.bitcoinpurity.pwc.provider.PwcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** The running desktop application as seen by the PWC backend: a view republished on the JavaFX thread after every application event. */
public class AppPwcHost implements PwcHost {
    private static final Logger log = LoggerFactory.getLogger(AppPwcHost.class);

    private final Set<Wallet> syncing = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private final Map<Wallet, Instant> synced = Collections.synchronizedMap(new WeakHashMap<>());
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private volatile PwcView view = PwcView.EMPTY;
    private volatile boolean active;

    private static Wallet master(Wallet wallet) {
        return wallet.isMasterWallet() ? wallet : wallet.getMasterWallet();
    }

    public void setActive(boolean active) {
        this.active = active;
        if(active) {
            publishSoon();
        } else {
            syncing.clear();
            synced.clear();
            view = PwcView.EMPTY;
        }
    }

    @Subscribe
    public void walletHistoryStatus(WalletHistoryStatusEvent event) {
        if(!active) {
            return;
        }
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

    /** Any event may change what PWC reports; bursts of events are coalesced into one capture. */
    @Subscribe
    public void anyEvent(Object event) {
        publishSoon();
    }

    public void publishSoon() {
        if(active && scheduled.compareAndSet(false, true)) {
            Platform.runLater(() -> {
                scheduled.set(false);
                if(active) {
                    publish();
                }
            });
        }
    }

    /** Called on the JavaFX thread. */
    void publish() {
        Config config = Config.get();
        List<WalletForm> forms = openWalletForms();
        List<Wallet> openMasters = forms.stream().map(WalletForm::getMasterWallet).toList();
        syncing.retainAll(openMasters);
        synced.keySet().retainAll(openMasters);
        view = PwcView.capture(forms, syncing::contains, synced::get, connected(), AppServices.getCurrentBlockHeight(),
                AppServices.getTargetBlockFeeRates(), AppServices.getMinimumRelayFeeRate(), config.isIncludeMempoolOutputs(), config.isGroupByAddress());
    }

    @Override
    public PwcView view() {
        return view;
    }

    @Override
    public List<WalletForm> openWalletForms() {
        return AppServices.get().getOpenWalletForms();
    }

    /** Called on the JavaFX thread; the connection service may only be read there. */
    protected boolean connected() {
        return AppServices.isConnected();
    }

    @Override
    public void onWalletThread(Runnable action) {
        Platform.runLater(action);
    }

    protected Sha256Hash send(Transaction transaction) throws Exception {
        return new ElectrumServer().broadcastTransaction(transaction, null, false);
    }

    @Override
    public Sha256Hash broadcast(Transaction transaction) throws PwcException {
        try {
            return send(transaction);
        } catch(Exception e) {
            log.warn("PWC broadcast failed", e);
            throw broadcastFailure(e);
        }
    }

    /** An error answer from the server, or a txid it reports for another transaction, is a refusal; anything else means the server could not be used. */
    static PwcException broadcastFailure(Exception failure) {
        for(Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if(cause instanceof JsonRpcException) {
                return new PwcException(ErrorCode.TRANSACTION_REJECTED, "The server refused the transaction");
            }
        }
        if(failure instanceof ServerException && failure.getCause() == null) {
            return new PwcException(ErrorCode.TRANSACTION_REJECTED, "The server refused the transaction");
        }
        return new PwcException(ErrorCode.NETWORK_UNAVAILABLE, "The server could not be reached");
    }
}
