package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.net.LegacyUtxoChecker;
import com.sparrowwallet.sparrow.net.LegacyUtxoChecker.Status;
import com.sparrowwallet.sparrow.net.LegacyUtxoChecker.Utxo;
import javafx.concurrent.Service;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;

import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.concurrent.CompletableFuture;

public class LegacyReplayGuard {
    public static final ButtonType ACCEPT = new ButtonType("I understand the replay risk — Continue", ButtonBar.ButtonData.OTHER);
    private boolean checking;

    public CompletableFuture<Boolean> check(List<Utxo> inputs, BooleanSupplier isCurrent, Node button, Runnable approved) {
        if(checking || !isCurrent.getAsBoolean()) {
            return CompletableFuture.completedFuture(false);
        }
        if(Network.get() != Network.MAINNET || inputs.isEmpty()) {
            approved.run();
            return CompletableFuture.completedFuture(true);
        }
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        checking = true;
        boolean disabled = button.isDisable();
        button.setDisable(true);
        Service<Map<Utxo, Status>> service = createService(inputs);
        service.setOnSucceeded(event -> finish(LegacyUtxoChecker.summarize(inputs, service.getValue()), isCurrent, button, disabled, approved, result));
        service.setOnFailed(event -> finish(Status.UNKNOWN, isCurrent, button, disabled, approved, result));
        service.setOnCancelled(event -> {
            checking = false;
            button.setDisable(disabled);
            result.complete(false);
        });
        service.start();
        return result;
    }

    protected Service<Map<Utxo, Status>> createService(List<Utxo> inputs) {
        return new LegacyUtxoChecker.CheckService(inputs);
    }

    private void finish(Status status, BooleanSupplier isCurrent, Node button, boolean disabled, Runnable approved, CompletableFuture<Boolean> result) {
        boolean continued = false;
        try {
            if(isCurrent.getAsBoolean() && (status == Status.ABSENT || acknowledge(status)) && isCurrent.getAsBoolean()) {
                button.setDisable(disabled);
                continued = true;
                approved.run();
            }
        } finally {
            checking = false;
            // The continuation may have disabled its button for signing or broadcasting.
            if(button.isDisable() && !continued) {
                button.setDisable(disabled);
            }
            result.complete(continued);
        }
    }

    private boolean acknowledge(Status status) {
        return AppServices.showWarningDialog("Legacy-chain replay risk", status == Status.SHARED ? LegacyReplayWarning.SHARED_WARNING : LegacyReplayWarning.UNKNOWN_WARNING,
                ButtonType.CANCEL, ACCEPT).filter(ACCEPT::equals).isPresent();
    }
}
