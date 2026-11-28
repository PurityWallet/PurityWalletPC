package com.sparrowwallet.sparrow.pwc;

import javafx.scene.control.Label;
import org.bitcoinpurity.pwc.provider.PairingRequest;

import java.util.function.BooleanSupplier;

/** PWC-UX-001 §7: pairing a LOCAL application. */
class PwcPairingDialog extends PwcPromptDialog {
    PwcPairingDialog(PairingRequest request, BooleanSupplier isWithdrawn) {
        super("Pair Application", "An application on this computer wants to pair with PurityWallet", isWithdrawn);
        addRequester(request.claimedName(), request.peer());
        if(request.claimedDescription() != null) {
            addDetail("Description by app", request.claimedDescription());
        }
        note("Check that the application shows exactly this code:");
        String code = request.pairingCode();
        Label codeLabel = new Label(code.length() == 6 ? code.substring(0, 3) + " " + code.substring(3) : code);
        codeLabel.setStyle("-fx-font-size: 32px; -fx-font-family: monospace; -fx-font-weight: bold");
        add(codeLabel);
        note("Pairing shares no wallet data. The application must ask separately for access and pair again when PWC restarts.");
    }
}
