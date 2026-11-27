package com.sparrowwallet.sparrow.pwc;

import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;
import org.bitcoinpurity.pwc.provider.OutputSummary;
import org.bitcoinpurity.pwc.provider.PsbtSignRequest;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/** PWC-UX-001 §4: signing a transaction. Everything shown is computed by the Provider from the PSBT itself. */
class PwcPsbtDialog extends PwcPromptDialog {
    PwcPsbtDialog(PsbtSignRequest request, String accountName, long highFeeRate, BooleanSupplier isWithdrawn) {
        super("Sign Transaction", "An application asks you to sign a transaction", isWithdrawn);
        addRequester(request.requester().claimedName(), request.requester().peer());
        addNetwork(request.network());
        addDetail("From account", accountName);

        List<OutputSummary> external = request.outputs().stream().filter(output -> !output.toThisAccount()).toList();
        List<OutputSummary> change = request.outputs().stream().filter(OutputSummary::toThisAccount).toList();
        VBox payments = new VBox(6);
        for(OutputSummary output : external) {
            payments.getChildren().add(output(output));
        }
        if(external.isEmpty()) {
            payments.getChildren().add(new Label("Nothing leaves the account except the fee"));
        }
        addDetail("Sends", payments);
        addDetail("Fee", PwcText.sats(request.feeSats()) + " at " + PwcText.feeRate(request.feeRateSatPerKvb()));
        long total = external.stream().mapToLong(OutputSummary::amountSats).sum() + request.feeSats();
        Label totalLabel = new Label(PwcText.sats(total));
        totalLabel.setStyle("-fx-font-weight: bold");
        addDetail("Total leaving account", totalLabel);

        if(!change.isEmpty()) {
            VBox changeOutputs = new VBox(6);
            change.forEach(output -> changeOutputs.getChildren().add(output(output)));
            TitledPane changePane = new TitledPane("Change returning to this account (" + change.size() + ")", changeOutputs);
            changePane.setExpanded(false);
            add(changePane);
        }
        if(request.inputsToSign().size() != request.inputCount()) {
            note("This approval signs inputs " + request.inputsToSign().stream().map(index -> "#" + (index + 1)).collect(Collectors.joining(", "))
                    + " of " + request.inputCount() + ".");
        }
        if(request.feeRateSatPerKvb() > highFeeRate) {
            addWarning("The fee is unusually high: " + PwcText.feeRate(request.feeRateSatPerKvb()) + ", " + PwcText.sats(request.feeSats()) + " in total.");
        }
        if(!"mainnet".equals(request.network())) {
            addWarning("This is a test network. Coins here have no value.");
        }
        note("Signing does not send the transaction. The application decides whether to broadcast it.");
    }

    private static Label output(OutputSummary output) {
        String destination = output.address() == null ? "Script " + output.scriptPubKey() : PwcText.address(output.address());
        Label label = new Label(destination + "\n" + PwcText.sats(output.amountSats()));
        label.setStyle("-fx-font-family: monospace");
        label.setWrapText(true);
        return label;
    }
}
