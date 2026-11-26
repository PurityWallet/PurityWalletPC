package com.sparrowwallet.sparrow.pwc;

import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.wallet.WalletForm;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;

class FakeHost implements PwcHost {
    final List<WalletForm> forms = new ArrayList<>();
    final Set<Wallet> syncing = new HashSet<>();
    Integer height = 1000;
    boolean connected = true;
    Map<Integer, Double> feeRates = new TreeMap<>(Map.of(1, 12.0, 3, 5.0, 6, 2.5));
    BiFunction<Wallet, PSBT, PSBT> device = (wallet, psbt) -> null;
    boolean acceptReplay = true;
    final List<Transaction> replayChecked = new ArrayList<>();
    final List<Transaction> broadcasts = new ArrayList<>();
    Exception broadcastError;

    @Override
    public <T> T onWalletThread(Callable<T> action) throws Exception {
        return action.call();
    }

    @Override
    public List<WalletForm> openWalletForms() {
        return forms;
    }

    @Override
    public boolean isSyncing(Wallet masterWallet) {
        return syncing.contains(masterWallet);
    }

    @Override
    public Instant syncedAt(Wallet masterWallet) {
        return null;
    }

    @Override
    public Integer currentBlockHeight() {
        return height;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public Map<Integer, Double> targetBlockFeeRates() {
        return feeRates;
    }

    @Override
    public double minimumRelayFeeRate() {
        return 1.0;
    }

    @Override
    public PSBT signWithDevice(Wallet wallet, PSBT psbt) {
        return device.apply(wallet, psbt);
    }

    @Override
    public boolean acceptReplayRisk(Transaction transaction) {
        replayChecked.add(transaction);
        return acceptReplay;
    }

    @Override
    public Sha256Hash broadcast(Transaction transaction) throws Exception {
        if(broadcastError != null) {
            throw broadcastError;
        }
        broadcasts.add(transaction);
        return transaction.getTxId();
    }
}
