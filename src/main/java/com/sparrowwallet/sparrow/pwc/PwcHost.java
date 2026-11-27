package com.sparrowwallet.sparrow.pwc;

import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.sparrow.wallet.WalletForm;
import org.bitcoinpurity.pwc.provider.PwcException;

import java.util.List;

/** What the PWC backend needs from the running application. */
public interface PwcHost {
    /** The latest published view of the open wallets and the connection; any thread, never waits. */
    PwcView view();

    /** The wallet forms of open wallet tabs; JavaFX thread only. */
    List<WalletForm> openWalletForms();

    /** Hands the action to the thread where wallet models may be changed, without waiting for it. */
    void onWalletThread(Runnable action);

    /** Sends the transaction through the current connection; may block on the network, so the backend bounds the wait. */
    Sha256Hash broadcast(Transaction transaction) throws PwcException;
}
