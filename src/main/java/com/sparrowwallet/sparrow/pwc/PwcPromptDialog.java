package com.sparrowwallet.sparrow.pwc;

import com.sparrowwallet.sparrow.AppServices;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.bitcoinpurity.pwc.provider.PeerInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * A PWC approval prompt (PWC-UX-001): approval is never the default button, every warning needs its own acknowledgement,
 * closing the window rejects, and the dialog closes by itself when the Provider withdraws the request.
 */
abstract class PwcPromptDialog extends Dialog<Boolean> {
    static final ButtonType APPROVE = new ButtonType("Approve", ButtonBar.ButtonData.OK_DONE);
    static final ButtonType REJECT = new ButtonType("Reject", ButtonBar.ButtonData.CANCEL_CLOSE);

    private final VBox content = new VBox(12);
    private final GridPane details = new GridPane();
    private final List<CheckBox> acknowledgements = new ArrayList<>();
    private final BooleanProperty acknowledged = new SimpleBooleanProperty(true);
    protected final BooleanProperty valid = new SimpleBooleanProperty(true);
    private final Timeline withdrawalCheck;
    private boolean withdrawn;

    PwcPromptDialog(String title, String header, BooleanSupplier isWithdrawn) {
        DialogPane pane = getDialogPane();
        pane.getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
        AppServices.setStageIcon(pane.getScene().getWindow());
        setTitle(title);
        pane.setHeaderText(header);
        pane.getButtonTypes().addAll(REJECT, APPROVE);

        details.setHgap(12);
        details.setVgap(6);
        content.getChildren().add(details);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(420);
        pane.setContent(scroll);
        pane.setPrefWidth(620);

        Button approve = (Button)pane.lookupButton(APPROVE);
        approve.setDefaultButton(false);
        approve.disableProperty().bind(valid.not().or(acknowledged.not()));
        Button reject = (Button)pane.lookupButton(REJECT);
        reject.setDefaultButton(false);
        setResultConverter(APPROVE::equals);

        withdrawalCheck = new Timeline(new KeyFrame(Duration.millis(500), event -> {
            if(isWithdrawn.getAsBoolean()) {
                withdrawn = true;
                setResult(Boolean.FALSE);
                close();
            }
        }));
        withdrawalCheck.setCycleCount(Timeline.INDEFINITE);
        addEventHandler(DialogEvent.DIALOG_SHOWN, event -> {
            withdrawalCheck.play();
            Platform.runLater(reject::requestFocus);
        });
        addEventHandler(DialogEvent.DIALOG_HIDDEN, event -> withdrawalCheck.stop());
        AppServices.moveToActiveWindowScreen(this);
    }

    boolean isWithdrawn() {
        return withdrawn;
    }

    Button approveButton() {
        return (Button)getDialogPane().lookupButton(APPROVE);
    }

    void addDetail(String label, String value) {
        Label text = new Label(value);
        text.setWrapText(true);
        addDetail(label, text);
    }

    void addDetail(String label, Node value) {
        int row = details.getRowCount();
        Label name = new Label(label);
        name.setMinWidth(150);
        details.add(name, 0, row);
        details.add(value, 1, row);
    }

    /** LOCAL requesters (PWC-UX-001 §2): the application as the operating system reports it, then the name it gave itself. */
    void addRequester(String claimedName, PeerInfo peer) {
        addDetail("Application", PwcText.application(peer));
        addDetail("Name given by app", "\"" + claimedName + "\" (not verified)");
    }

    void addNetwork(String network) {
        Label label = new Label(network);
        if(!"mainnet".equals(network)) {
            label.setStyle("-fx-font-weight: bold");
        }
        addDetail("Network", label);
    }

    void add(Node node) {
        content.getChildren().add(node);
    }

    Label note(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        add(label);
        return label;
    }

    void addWarning(String text) {
        CheckBox acknowledgement = new CheckBox(text);
        acknowledgement.setWrapText(true);
        acknowledgement.setStyle("-fx-text-fill: -fx-accent; -fx-font-weight: bold");
        acknowledgements.add(acknowledgement);
        acknowledged.set(false);
        acknowledgement.selectedProperty().addListener((observable, oldValue, newValue) -> acknowledged.set(acknowledgements.stream().allMatch(CheckBox::isSelected)));
        add(acknowledgement);
    }
}
