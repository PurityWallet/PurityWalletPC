package com.sparrowwallet.sparrow.pwc;

import javafx.scene.control.TextArea;
import org.bitcoinpurity.pwc.provider.MessageFormat;
import org.bitcoinpurity.pwc.provider.MessageSignRequest;

import java.util.List;
import java.util.function.BooleanSupplier;

/** PWC-UX-001 §5: signing a message. */
class PwcMessageDialog extends PwcPromptDialog {
    PwcMessageDialog(MessageSignRequest request, String accountName, BooleanSupplier isWithdrawn) {
        super("Sign Message", "An application asks you to sign a message", isWithdrawn);
        addRequester(request.requester().claimedName(), request.requester().peer());
        addNetwork(request.network());
        addDetail("Account", accountName);
        addDetail("Address", request.address());
        addDetail("Format", request.format() == MessageFormat.LEGACY ? "Legacy (Bitcoin Signed Message)" : "BIP-322 simple");
        note("Exact message:");
        TextArea message = new TextArea(request.message());
        message.setEditable(false);
        message.setWrapText(true);
        message.setPrefRowCount(6);
        message.setStyle("-fx-font-family: monospace");
        add(message);
        List<String> hidden = PwcText.hiddenCharacters(request.message());
        if(!hidden.isEmpty()) {
            addWarning("The message contains invisible or easily confused characters: " + String.join(", ", hidden) + ".");
        }
        if(!"mainnet".equals(request.network())) {
            addWarning("This is a test network. Coins here have no value.");
        }
        note("Signing a message does not move funds, but it may be used to prove that you control this address.");
    }
}
