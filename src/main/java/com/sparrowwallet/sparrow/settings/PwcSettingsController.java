package com.sparrowwallet.sparrow.settings;

import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.control.UnlabeledToggleSwitch;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.pwc.PwcService;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.bitcoinpurity.pwc.provider.AccountInfo;
import org.bitcoinpurity.pwc.provider.Grant;
import org.bitcoinpurity.pwc.provider.PairedClient;
import org.bitcoinpurity.pwc.provider.PeerInfo;
import org.bitcoinpurity.pwc.provider.WalletInfo;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/** PWC-UX-001 §9: paired applications, their grants, and the switch for the local endpoint. */
public class PwcSettingsController extends SettingsDetailController {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault());

    @FXML
    private UnlabeledToggleSwitch enabled;

    @FXML
    private Label status;

    @FXML
    private TableView<PairedClient> clients;

    @FXML
    private TableView<Grant> grants;

    @FXML
    private Button unpair;

    @FXML
    private Button revoke;

    @Override
    public void initializeView(Config config) {
        clients.setPlaceholder(new Label("No applications paired yet"));
        clients.getColumns().add(column("Name given by app (not verified)", PairedClient::claimedName));
        clients.getColumns().add(column("Application", client -> application(client.peer())));
        clients.getColumns().add(column("Paired", client -> date(client.pairedAt())));
        clients.getColumns().add(column("Last connected", client -> date(client.lastConnectedAt())));
        clients.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        grants.setPlaceholder(new Label("No access granted"));
        grants.getColumns().add(column("Allowed", grant -> String.join(", ", grant.scopes())));
        grants.getColumns().add(column("Accounts", this::resources));
        grants.getColumns().add(column("Expires", grant -> grant.expiresAt() == null ? "Until PWC stops" : date(grant.expiresAt())));
        grants.getColumns().add(column("Limit (sat)", grant -> grant.constraints() == null || grant.constraints().maxAmountSatsPerRequest() == null ? "None"
                : String.format(Locale.ROOT, "%,d", grant.constraints().maxAmountSatsPerRequest())));
        grants.getColumns().add(column("Status", grant -> grant.status().name().toLowerCase(Locale.ROOT)));
        grants.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        clients.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, client) -> showGrants(client));
        unpair.disableProperty().bind(clients.getSelectionModel().selectedItemProperty().isNull());
        revoke.disableProperty().bind(grants.getSelectionModel().selectedItemProperty().isNull());

        enabled.setSelected(config.isPwcEnabled());
        enabled.selectedProperty().addListener((observable, oldValue, newValue) -> {
            config.setPwcEnabled(newValue);
            AppServices.get().setPwcEnabled(newValue);
            refresh(null);
        });
        refresh(null);
    }

    private static <T> TableColumn<T, String> column(String title, Function<T, String> value) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }

    private static String date(Instant instant) {
        return instant == null ? "" : DATE.format(instant);
    }

    private static String application(PeerInfo peer) {
        return peer == null || peer.executable() == null ? "Unknown" : peer.executable().toString();
    }

    private PwcService service() {
        return AppServices.get().getPwcService();
    }

    private String resources(Grant grant) {
        PwcService service = service();
        List<String> names = new ArrayList<>();
        for(String walletId : grant.walletIds()) {
            names.add(service == null || service.getBackend() == null ? walletId
                    : service.getBackend().wallet(walletId).map(WalletInfo::displayName).orElse("A closed wallet"));
        }
        for(String accountId : grant.accountIds()) {
            names.add(service == null || service.getBackend() == null ? accountId
                    : service.getBackend().account(accountId).map(AccountInfo::displayName).orElse("An account of a closed wallet"));
        }
        return names.isEmpty() ? "None" : String.join(", ", names);
    }

    private void showGrants(PairedClient client) {
        PwcService service = service();
        grants.getItems().setAll(client == null || service == null ? List.of()
                : service.grants().stream().filter(grant -> grant.clientId().equals(client.clientId())).toList());
    }

    @FXML
    public void refresh(ActionEvent event) {
        PwcService service = service();
        PwcService.Status current = service == null ? PwcService.Status.UNAVAILABLE : service.getStatus();
        String text = switch(current) {
            case RUNNING -> "Listening at " + service.getEndpoint();
            case STOPPED -> "Off";
            case FAILED -> "Not running";
            case UNAVAILABLE -> service == null ? "Available in the desktop application only" : "Not available";
        };
        String message = service == null ? null : service.getMessage();
        status.setText(message == null ? text : text + ". " + message);
        enabled.setDisable(current == PwcService.Status.UNAVAILABLE);

        PairedClient selected = clients.getSelectionModel().getSelectedItem();
        clients.getItems().setAll(service == null ? List.of() : service.pairedClients());
        if(selected != null) {
            clients.getItems().stream().filter(client -> client.clientId().equals(selected.clientId())).findFirst().ifPresent(clients.getSelectionModel()::select);
        }
        showGrants(clients.getSelectionModel().getSelectedItem());
    }

    @FXML
    public void unpair(ActionEvent event) {
        PairedClient client = clients.getSelectionModel().getSelectedItem();
        if(client != null && AppServices.showWarningDialog("Unpair application", "Unpair \"" + client.claimedName()
                + "\"? It loses all access and must pair again to reconnect.", ButtonType.CANCEL, ButtonType.OK).filter(ButtonType.OK::equals).isPresent()) {
            service().unpairClient(client.clientId());
            refresh(null);
        }
    }

    @FXML
    public void revoke(ActionEvent event) {
        Grant grant = grants.getSelectionModel().getSelectedItem();
        if(grant != null) {
            service().revokeGrant(grant.grantId());
            refresh(null);
        }
    }
}
