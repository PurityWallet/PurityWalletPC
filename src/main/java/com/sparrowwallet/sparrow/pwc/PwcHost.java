package com.sparrowwallet.sparrow.pwc;

import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.wallet.WalletForm;
import org.bitcoinpurity.pwc.provider.PwcException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/** What the PWC backend needs from the running application. */
public interface PwcHost {
    /** Runs the action where wallet models may be read and changed, and waits for its result. */
    <T> T onWalletThread(Callable<T> action) throws Exception;

    /** The wallet forms of open wallet tabs; called on the wallet thread. */
    List<WalletForm> openWalletForms();

    /** Whether the wallet's history is loading; called on the wallet thread. */
    boolean isSyncing(Wallet masterWallet);

    /** When the wallet's history last finished loading in this session, or null. */
    Instant syncedAt(Wallet masterWallet);

    Integer currentBlockHeight();

    boolean isConnected();

    /** The connected node's fee estimates in sat/vB by target block count, or null. */
    Map<Integer, Double> targetBlockFeeRates();

    double minimumRelayFeeRate();

    /** Signs with a connected USB device after the user confirms on it; null if the user cancels. */
    PSBT signWithDevice(Wallet wallet, PSBT psbt) throws PwcException;

    /** Runs the legacy-chain replay check (REQ-011) for the inputs and asks the user to accept a shared or unknown result. */
    boolean acceptReplayRisk(Transaction transaction);

    Sha256Hash broadcast(Transaction transaction) throws Exception;
}
