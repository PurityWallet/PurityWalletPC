package com.sparrowwallet.sparrow.pwc;

import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.bitcoinpurity.pwc.provider.GrantChoice;
import org.bitcoinpurity.pwc.provider.GrantConstraints;
import org.bitcoinpurity.pwc.provider.PermissionRequest;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.function.BooleanSupplier;

/** PWC-UX-001 §3: granting access. The user can narrow the request but never widen it. */
class PwcPermissionDialog extends PwcPromptDialog {
    /** Core §6 scopes with the suggested wording and sensitivity of PWC-UX-001 §3. */
    static final Map<String, String> SCOPES = new LinkedHashMap<>();

    static {
        SCOPES.put("wallets:read", "See the names of the selected wallets and accounts (low)");
        SCOPES.put("balances:read", "See balances of the selected accounts (medium)");
        SCOPES.put("addresses:read", "See existing receive addresses (medium)");
        SCOPES.put("addresses:create", "Create new receive addresses (low)");
        SCOPES.put("transactions:read", "See transaction history (high)");
        SCOPES.put("utxos:read", "See individual coins and their details (high)");
        SCOPES.put("psbt:create", "Prepare unsigned transactions; this reveals the coins used (high)");
        SCOPES.put("psbt:sign", "Ask you to sign transactions; you approve each one (highest)");
        SCOPES.put("transactions:broadcast", "Send signed transactions to the network (medium)");
        SCOPES.put("messages:sign", "Ask you to sign messages; you approve each one (high)");
        SCOPES.put("events:read", "Be notified of changes to what it can already see (low)");
    }

    record Account(String accountId, String name) {}

    private record Expiry(String label, Instant expiresAt) {
        @Override
        public String toString() {
            return label;
        }
    }

    private final PermissionRequest request;
    private final String network;
    private final Map<String, CheckBox> scopes = new LinkedHashMap<>();
    private final Map<String, CheckBox> accounts = new LinkedHashMap<>();
    private final ComboBox<Expiry> expiry = new ComboBox<>();
    private final TextField amountLimit = new TextField();

    PwcPermissionDialog(PermissionRequest request, String network, List<Account> offered, BooleanSupplier isWithdrawn) {
        super("Allow Access", "An application asks for access to your wallet", isWithdrawn);
        this.request = request;
        this.network = network;
        addRequester(request.requester().claimedName(), request.requester().peer());
        addNetwork(network);
        if(request.network() != null && !request.network().equals(network)) {
            note("The application asks for " + request.network() + ", but this wallet runs on " + network + ". You can only reject this request.").setStyle("-fx-font-weight: bold");
            valid.set(false);
        }

        VBox scopeBoxes = new VBox(4);
        for(String scope : request.scopes()) {
            CheckBox box = new CheckBox(SCOPES.getOrDefault(scope, scope));
            box.setSelected(true);
            box.setWrapText(true);
            box.selectedProperty().addListener((observable, oldValue, newValue) -> updateValid());
            scopes.put(scope, box);
            scopeBoxes.getChildren().add(box);
        }
        addDetail("Allow it to", scopeBoxes);

        VBox accountBoxes = new VBox(4);
        for(Account account : offered) {
            CheckBox box = new CheckBox(account.name());
            box.setSelected(true);
            accounts.put(account.accountId(), box);
            accountBoxes.getChildren().add(box);
        }
        if(offered.isEmpty()) {
            accountBoxes.getChildren().add(new javafx.scene.control.Label("No open wallet matches this request"));
        }
        addDetail("Accounts shared", accountBoxes);

        Instant now = Instant.now();
        Instant requested = request.expiresAt();
        DateTimeFormatter format = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault());
        expiry.getItems().add(new Expiry(requested == null ? "No expiry" : "As requested: " + format.format(requested), requested));
        for(Map.Entry<String, Duration> option : Map.of("1 hour", Duration.ofHours(1), "1 day", Duration.ofDays(1), "30 days", Duration.ofDays(30)).entrySet()
                .stream().sorted(Map.Entry.comparingByValue()).toList()) {
            Instant at = now.plus(option.getValue());
            if(requested == null || at.isBefore(requested)) {
                expiry.getItems().add(new Expiry(option.getKey(), at));
            }
        }
        expiry.getSelectionModel().selectFirst();
        addDetail("Access expires", expiry);

        if(request.scopes().contains("psbt:sign") || request.scopes().contains("psbt:create")) {
            Long requestedLimit = request.constraints() == null ? null : request.constraints().maxAmountSatsPerRequest();
            amountLimit.setPromptText(requestedLimit == null ? "No limit" : "");
            if(requestedLimit != null) {
                amountLimit.setText(Long.toString(requestedLimit));
            }
            amountLimit.textProperty().addListener((observable, oldValue, newValue) -> updateValid());
            addDetail("Limit per transaction (sat)", amountLimit);
        }
        if(request.scopes().contains("psbt:create")) {
            note("Transactions the application prepares reveal which of your coins they use.");
        }
        updateValid();
    }

    private Long requestedLimit() {
        return request.constraints() == null ? null : request.constraints().maxAmountSatsPerRequest();
    }

    /** The limit typed by the user, or null for none; -1 when the text is not a limit the user may choose. */
    private long limit() {
        String text = amountLimit.getText().trim();
        if(text.isEmpty()) {
            return requestedLimit() == null ? 0 : -1;
        }
        try {
            long value = Long.parseLong(text);
            return value > 0 && (requestedLimit() == null || value <= requestedLimit()) ? value : -1;
        } catch(NumberFormatException e) {
            return -1;
        }
    }

    private void updateValid() {
        boolean networkOk = request.network() == null || request.network().equals(network);
        valid.set(networkOk && scopes.values().stream().anyMatch(CheckBox::isSelected) && limit() >= 0);
    }

    GrantChoice choice() {
        List<String> chosenScopes = scopes.entrySet().stream().filter(entry -> entry.getValue().isSelected()).map(Map.Entry::getKey).toList();
        List<String> chosenAccounts = accounts.entrySet().stream().filter(entry -> entry.getValue().isSelected()).map(Map.Entry::getKey).toList();
        long limit = limit();
        Instant validUntil = request.constraints() == null ? null : request.constraints().validUntil();
        GrantConstraints constraints = limit > 0 || validUntil != null ? new GrantConstraints(limit > 0 ? limit : null, validUntil) : null;
        return new GrantChoice(network, chosenScopes, List.of(), chosenAccounts, expiry.getValue().expiresAt(), constraints);
    }
}
