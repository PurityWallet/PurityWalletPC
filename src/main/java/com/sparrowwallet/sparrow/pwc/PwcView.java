package com.sparrowwallet.sparrow.pwc;

import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.wallet.WalletForm;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The open wallets and the connection at one instant, captured on the JavaFX thread and then only read.
 * The backend reads the wallet copies; {@link Form#live()} may only be used on the JavaFX thread.
 */
public record PwcView(List<Form> forms, boolean connected, Integer height, Map<Integer, Double> feeRates, double minimumRelayFeeRate,
                      boolean includeMempoolOutputs, boolean groupByAddress) {
    public record Form(WalletForm live, Wallet wallet, boolean locked, boolean syncing, Instant syncedAt, long walletFileModified) {
        Wallet master() {
            return wallet.isMasterWallet() ? wallet : wallet.getMasterWallet();
        }
    }

    public static final PwcView EMPTY = new PwcView(List.of(), false, null, null, 0, false, false);

    public PwcView {
        forms = List.copyOf(forms);
        feeRates = feeRates == null ? null : Collections.unmodifiableMap(new TreeMap<>(feeRates));
    }

    public static PwcView capture(List<WalletForm> forms, Predicate<Wallet> syncing, Function<Wallet, Instant> syncedAt, boolean connected, Integer height,
                                  Map<Integer, Double> feeRates, double minimumRelayFeeRate, boolean includeMempoolOutputs, boolean groupByAddress) {
        //One copy per master wallet, so that a child account and its master come from the same instant
        Map<Wallet, Wallet> masterCopies = new IdentityHashMap<>();
        List<Form> captured = new ArrayList<>();
        for(WalletForm form : forms) {
            Wallet live = form.getWallet();
            Wallet liveMaster = form.getMasterWallet();
            Wallet masterCopy = masterCopies.computeIfAbsent(liveMaster, PwcView::copy);
            Wallet copy = live == liveMaster ? masterCopy : null;
            for(int i = 0; copy == null && i < liveMaster.getChildWallets().size(); i++) {
                if(liveMaster.getChildWallets().get(i) == live) {
                    copy = masterCopy.getChildWallets().get(i);
                }
            }
            if(copy == null) {
                continue;
            }
            captured.add(new Form(form, copy, form.isLocked(), syncing.test(liveMaster), syncedAt.apply(liveMaster), form.getWalletFile().lastModified()));
        }
        return new PwcView(captured, connected, height, feeRates, minimumRelayFeeRate, includeMempoolOutputs, groupByAddress);
    }

    /** {@link Wallet#copy()} drops the status of outputs, which records frozen coins. */
    private static Wallet copy(Wallet master) {
        Wallet copy = master.copy();
        restoreStatus(master, copy);
        for(int i = 0; i < master.getChildWallets().size(); i++) {
            restoreStatus(master.getChildWallets().get(i), copy.getChildWallets().get(i));
        }
        return copy;
    }

    private static void restoreStatus(Wallet wallet, Wallet copy) {
        Map<String, BlockTransactionHashIndex> copied = new HashMap<>();
        copy.getWalletTxos().keySet().forEach(reference -> copied.put(reference.getHash() + ":" + reference.getIndex(), reference));
        for(BlockTransactionHashIndex reference : wallet.getWalletTxos().keySet()) {
            BlockTransactionHashIndex target = copied.get(reference.getHash() + ":" + reference.getIndex());
            if(target != null && reference.getStatus() != null) {
                target.setStatus(reference.getStatus());
            }
        }
    }
}
