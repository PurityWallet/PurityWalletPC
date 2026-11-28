package com.sparrowwallet.sparrow.pwc;

import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.wallet.WalletForm;
import org.bitcoinpurity.pwc.provider.PwcException;

import java.util.*;

/** A host whose view is captured afresh on each call, unless one is pinned. Wallet-thread actions queue until {@link #runWalletThread()}. */
class FakeHost implements PwcHost {
    interface Broadcaster {
        Sha256Hash broadcast(Transaction transaction) throws PwcException;
    }

    final List<WalletForm> forms = new ArrayList<>();
    final Set<Wallet> syncing = new HashSet<>();
    Integer height = 1000;
    boolean connected = true;
    Map<Integer, Double> feeRates = new TreeMap<>(Map.of(1, 12.0, 3, 5.0, 6, 2.5));
    PwcView pinned;
    Throwable viewFailure;
    final Deque<Runnable> walletThread = new ArrayDeque<>();
    final List<Transaction> broadcasts = new ArrayList<>();
    Broadcaster broadcaster;

    PwcView capture() {
        return PwcView.capture(forms, syncing::contains, wallet -> null, connected, height, feeRates, 1.0,
                Config.get().isIncludeMempoolOutputs(), Config.get().isGroupByAddress());
    }

    @Override
    public PwcView view() {
        if(viewFailure instanceof RuntimeException e) {
            throw e;
        }
        if(viewFailure instanceof Error e) {
            throw e;
        }
        return pinned != null ? pinned : capture();
    }

    @Override
    public List<WalletForm> openWalletForms() {
        return forms;
    }

    @Override
    public void onWalletThread(Runnable action) {
        walletThread.add(action);
    }

    void runWalletThread() {
        while(!walletThread.isEmpty()) {
            walletThread.poll().run();
        }
    }

    @Override
    public Sha256Hash broadcast(Transaction transaction) throws PwcException {
        if(broadcaster != null) {
            return broadcaster.broadcast(transaction);
        }
        broadcasts.add(transaction);
        return transaction.getTxId();
    }
}
