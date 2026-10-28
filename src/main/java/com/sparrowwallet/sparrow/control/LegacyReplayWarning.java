package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.sparrow.net.LegacyUtxoChecker.Status;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;

public class LegacyReplayWarning extends Label {
    public static final String SHARED_WARNING = "All selected inputs also exist unspent on the legacy Bitcoin chain. This transaction may be replayed there and spend your legacy-chain coins. Continue only if you understand and accept this replay risk.";
    public static final String UNKNOWN_WARNING = "The legacy-chain check could not be completed. Replay risk is unknown. This transaction may also spend your coins on the legacy Bitcoin chain. Continue only if you understand and accept this risk.";

    public LegacyReplayWarning() {
        getStyleClass().add("legacy-replay-warning");
        setWrapText(true);
        setMaxWidth(Double.MAX_VALUE);
        setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        managedProperty().bind(visibleProperty());
        setStatus(Status.ABSENT);
    }

    public void setStatus(Status status) {
        setText(switch(status) {
            case SHARED -> "Warning: replay risk. " + SHARED_WARNING;
            case UNKNOWN -> "Warning: " + UNKNOWN_WARNING;
            case CHECKING -> "Checking selected inputs on the legacy Bitcoin chain...";
            case ABSENT -> "";
        });
        setTooltip(getText().isEmpty() ? null : new Tooltip(getText()));
        setVisible(Network.get() == Network.MAINNET && status != Status.ABSENT);
    }
}
