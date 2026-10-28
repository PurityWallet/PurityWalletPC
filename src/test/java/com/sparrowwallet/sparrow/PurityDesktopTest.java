package com.sparrowwallet.sparrow;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.*;
import com.sparrowwallet.drongo.policy.Policy;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.control.LegacyReplayGuard;
import com.sparrowwallet.sparrow.control.LegacyReplayWarning;
import com.sparrowwallet.sparrow.control.UtxosTreeTable;
import com.sparrowwallet.sparrow.net.LegacyUtxoChecker;
import com.sparrowwallet.sparrow.net.LegacyUtxoChecker.Status;
import com.sparrowwallet.sparrow.net.LegacyUtxoChecker.Utxo;
import com.sparrowwallet.sparrow.wallet.*;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import com.sparrowwallet.sparrow.event.SendActionEvent;
import com.sparrowwallet.sparrow.event.SendPaymentsEvent;
import com.sparrowwallet.sparrow.io.CaravanMultisig;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.settings.GeneralSettingsController;
import com.sparrowwallet.sparrow.wallet.WalletForm;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "purity.ui.tests", matches = "true")
class PurityDesktopTest {
    private static final String RECIPIENT = "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa";
    private static final List<String> alerts = new ArrayList<>();
    private static ButtonType alertAnswer;
    private static Runnable duringAlert;
    private static Stage owner;
    private static Map<Window, List<WalletTabData>> walletWindows;
    private static final Recorder recorder = new Recorder();

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void startDesktop() throws Exception {
        Network.set(Network.MAINNET);
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(() -> { Platform.setImplicitExit(false); started.countDown(); });
        assertTrue(started.await(10, TimeUnit.SECONDS));
        onFx(() -> {
            AppServices.initialize(new Application() { public void start(Stage stage) {} }, new InteractionServices() {
                public Optional<ButtonType> showAlert(String title, String content, Alert.AlertType type, Node graphic, ButtonType... buttons) {
                    alerts.add(title);
                    if(duringAlert != null) duringAlert.run();
                    return Optional.ofNullable(alertAnswer);
                }
                public Optional<String> requestPassphrase(String name, Keystore keystore) { return Optional.empty(); }
            });
            owner = new Stage();
            owner.setScene(new Scene(new StackPane(), 600, 460));
            owner.show();
            var field = AppServices.class.getDeclaredField("walletWindows");
            field.setAccessible(true);
            walletWindows = (Map<Window, List<WalletTabData>>)field.get(AppServices.get());
            EventManager.get().register(recorder);
            return null;
        });
    }

    @BeforeEach
    void reset() throws Exception {
        onFx(() -> {
            walletWindows.clear();
            alerts.clear();
            alertAnswer = null;
            duringAlert = null;
            Network.set(Network.MAINNET);
            recorder.actions.clear();
            recorder.payments.clear();
            return null;
        });
    }

    @AfterAll
    static void closeDesktop() throws Exception {
        onFx(() -> {
            EventManager.get().unregister(recorder);
            EventManager.get().unregister(AppServices.get());
            owner.close();
            return null;
        });
        Platform.exit();
    }

    @Test
    void settingsAndAboutRenderWithoutRemovedControls() throws Exception {
        onFx(() -> {
            FXMLLoader settings = new FXMLLoader(AppServices.class.getResource("settings/general.fxml"));
            Parent root = settings.load();
            ((GeneralSettingsController)settings.getController()).initializeView(Config.get());
            owner.setScene(new Scene(root, 640, 560));
            root.resize(640, 560);
            root.applyCss();
            root.layout();
            assertNull(root.lookup("#fiatCurrency"));
            assertNull(root.lookup("#exchangeSource"));
            assertNull(root.lookup("#checkNewVersions"));
            assertEquals(List.of(com.sparrowwallet.sparrow.net.FeeRatesSource.ELECTRUM_SERVER), ((ComboBox<?>)root.lookup("#feeRatesSource")).getItems());
            assertNotNull(root.lookup("#notifyNewTransactions"));
            Path qa = Path.of("build/qa");
            Files.createDirectories(qa);
            ImageIO.write(SwingFXUtils.fromFXImage(root.snapshot(null, null), null), "png", qa.resolve("settings.png").toFile());

            FXMLLoader about = new FXMLLoader(AppServices.class.getResource("about.fxml"));
            Parent aboutRoot = about.load();
            ((AboutController)about.getController()).initializeView();
            owner.setScene(new Scene(aboutRoot));
            owner.sizeToScene();
            aboutRoot.applyCss();
            aboutRoot.layout();
            assertTrue(((Label)aboutRoot.lookup("#title")).getText().startsWith("PurityWallet "));
            ImageIO.write(SwingFXUtils.fromFXImage(aboutRoot.snapshot(null, null), null), "png", qa.resolve("about.png").toFile());
            return null;
        });
    }

    @Test
    void invalidRecipientAndNoWalletDoNotPostSendEvents() throws Exception {
        onFx(() -> {
            AppServices.openDonation();
            assertEquals(List.of("Donation unavailable"), alerts);
            AppServices.openDonation(RECIPIENT);
            assertEquals(List.of("Donation unavailable", "No wallet available"), alerts);
            assertTrue(recorder.actions.isEmpty());
            assertTrue(recorder.payments.isEmpty());
            return null;
        });
    }

    @Test
    void allDesktopWalletTypesRequireSelectionAndLeaveAmountEmpty() throws Exception {
        List<Wallet> wallets = new ArrayList<>();
        Wallet watch = new Wallet("Watch only");
        watch.setPolicyType(PolicyType.SINGLE_HD);
        watch.setScriptType(ScriptType.P2WPKH);
        Keystore key = new Keystore("Keystore 1");
        key.setSource(KeystoreSource.SW_WATCH);
        key.setWalletModel(WalletModel.SPARROW);
        key.setKeyDerivation(new KeyDerivation("60bcd3a7", "m/84'/0'/0'"));
        key.setExtendedPublicKey(ExtendedKey.fromDescriptor("xpub6BrhGFTWPd3DXo8s2BPxHHzCmBCyj8QvamcEUaq8EDwnwXpvvcU9LzpJqENHcqHkqwTn2vPhynGVoEqj3PAB3NxnYZrvCsSfoCniJKaggdy"));
        watch.getKeystores().add(key);
        watch.setDefaultPolicy(Policy.getPolicy(watch.getPolicyType(), watch.getScriptType(), watch.getKeystores(), null));
        wallets.add(watch);
        Wallet hardware = watch.copy();
        hardware.setName("Hardware");
        hardware.getKeystores().getFirst().setSource(KeystoreSource.HW_USB);
        hardware.getKeystores().getFirst().setWalletModel(WalletModel.TREZOR_1);
        wallets.add(hardware);
        Wallet software = watch.copy();
        software.setName("Software");
        software.getKeystores().clear();
        software.getKeystores().add(Keystore.fromSeed(new DeterministicSeed("response seminar brave tip suit recall often sound stick owner lottery motion", "", 0, DeterministicSeed.Type.BIP39), PolicyType.SINGLE_HD, KeyDerivation.parsePath("m/84'/0'/0'")));
        software.setDefaultPolicy(Policy.getPolicy(software.getPolicyType(), software.getScriptType(), software.getKeystores(), null));
        wallets.add(software);
        try(var input = getClass().getResourceAsStream("/com/sparrowwallet/sparrow/io/caravan-multisig-export-1.json")) {
            wallets.add(new CaravanMultisig().importWallet(input, null));
        }
        for(Wallet wallet : wallets) {
            assertTrue(wallet.isValid(), wallet.getName());
            chooseDonation(wallet, ButtonType.OK);
            onFx(() -> {
                assertSame(wallet, recorder.actions.getLast().getWallet());
                var payment = recorder.payments.getLast();
                assertSame(wallet, payment.getWallet());
                assertEquals(-1, payment.getPayments().getFirst().getAmount());
                assertNull(payment.getBitcoinURI().getAmount());
                return null;
            });
        }
        assertEquals(4, recorder.payments.size());
        chooseDonation(watch, ButtonType.CANCEL);
        assertEquals(4, recorder.actions.size());
        assertEquals(4, recorder.payments.size());
    }

    @Test
    void legacyGuardRequiresExplicitAcceptanceAndDiscardsStaleResults() throws Exception {
        Utxo coin = new Utxo("ab".repeat(32), 0, 1234, "0014abcd");
        for(Status status : List.of(Status.SHARED, Status.UNKNOWN, Status.ABSENT)) {
            for(ButtonType answer : Arrays.asList(null, ButtonType.CANCEL, LegacyReplayGuard.ACCEPT)) {
                var current = new java.util.concurrent.atomic.AtomicBoolean(true);
                var actions = new java.util.concurrent.atomic.AtomicInteger();
                GuardFixture fixture = new GuardFixture(Map.of(coin, status));
                Button button = onFx(() -> {
                    alerts.clear();
                    alertAnswer = answer;
                    Button control = new Button("Sign");
                    fixture.check(List.of(coin), current::get, control, () -> { actions.incrementAndGet(); control.setDisable(true); });
                    assertTrue(control.isDisable());
                    return control;
                });
                assertTrue(fixture.finished.await(10, TimeUnit.SECONDS));
                onFx(() -> {
                    assertEquals(status == Status.ABSENT || answer == LegacyReplayGuard.ACCEPT ? 1 : 0, actions.get());
                    assertEquals(actions.get() > 0, button.isDisable());
                    assertEquals(status == Status.ABSENT ? List.of() : List.of("Legacy-chain replay risk"), alerts);
                    return null;
                });
            }
        }
        for(boolean changeDuringDialog : List.of(false, true)) {
            var current = new java.util.concurrent.atomic.AtomicBoolean(true);
            var actions = new java.util.concurrent.atomic.AtomicInteger();
            GuardFixture fixture = new GuardFixture(Map.of(coin, Status.SHARED));
            onFx(() -> {
                alertAnswer = LegacyReplayGuard.ACCEPT;
                if(changeDuringDialog) duringAlert = () -> current.set(false);
                fixture.check(List.of(coin), current::get, new Button(), actions::incrementAndGet);
                if(!changeDuringDialog) current.set(false);
                return null;
            });
            assertTrue(fixture.finished.await(10, TimeUnit.SECONDS));
            assertEquals(0, actions.get());
        }
    }

    @Test
    void offlineCheckIsUnknownAndRegtestSkipsLegacyGuard() throws Exception {
        Utxo coin = new Utxo("ab".repeat(32), 0, 1234, "0014abcd");
        CountDownLatch checked = new CountDownLatch(1);
        onFx(() -> {
            AppServices.onlineProperty().set(false);
            var service = new LegacyUtxoChecker.CheckService(List.of(coin));
            service.setOnSucceeded(event -> {
                assertEquals(Status.UNKNOWN, service.getValue().get(coin));
                checked.countDown();
            });
            service.start();
            return null;
        });
        assertTrue(checked.await(10, TimeUnit.SECONDS));
        onFx(() -> {
            Network.set(Network.REGTEST);
            var actions = new java.util.concurrent.atomic.AtomicInteger();
            new LegacyReplayGuard().check(List.of(coin), () -> true, new Button(), actions::incrementAndGet);
            assertEquals(1, actions.get());
            assertTrue(alerts.isEmpty());
            Network.set(Network.MAINNET);
            return null;
        });
    }

    @Test
    void legacyWarningsAndUtxoStatesRender() throws Exception {
        onFx(() -> {
            FXMLLoader send = new FXMLLoader(AppServices.class.getResource("wallet/send.fxml"));
            Parent sendRoot = send.load();
            assertNotNull(sendRoot.lookup("#legacyReplayWarning"));
            EventManager.get().unregister(send.getController());

            Wallet wallet = new Wallet("Replay warning preview");
            wallet.setPolicyType(PolicyType.SINGLE_HD);
            wallet.setScriptType(ScriptType.P2WPKH);
            Keystore key = new Keystore("Watch only");
            key.setSource(KeystoreSource.SW_WATCH);
            key.setWalletModel(WalletModel.SPARROW);
            key.setKeyDerivation(new KeyDerivation("60bcd3a7", "m/84'/0'/0'"));
            key.setExtendedPublicKey(ExtendedKey.fromDescriptor("xpub6BrhGFTWPd3DXo8s2BPxHHzCmBCyj8QvamcEUaq8EDwnwXpvvcU9LzpJqENHcqHkqwTn2vPhynGVoEqj3PAB3NxnYZrvCsSfoCniJKaggdy"));
            wallet.getKeystores().add(key);
            wallet.setDefaultPolicy(Policy.getPolicy(wallet.getPolicyType(), wallet.getScriptType(), wallet.getKeystores(), null));
            WalletNode node = wallet.getNode(KeyPurpose.RECEIVE).getChildren().iterator().next();
            for(int i = 0; i < 4; i++) {
                node.getTransactionOutputs().add(new BlockTransactionHashIndex(com.sparrowwallet.drongo.protocol.Sha256Hash.wrap(String.format("%064x", i + 1)), 970000, new Date(1700000000000L), 100L, 0, 10000 + i));
            }
            WalletUtxosEntry entries = new WalletUtxosEntry(wallet);
            Status[] states = {Status.SHARED, Status.UNKNOWN, Status.CHECKING, Status.ABSENT};
            for(int i = 0; i < 4; i++) ((UtxoEntry)entries.getChildren().get(i)).legacyStatusProperty().set(states[i]);
            UtxosTreeTable table = new UtxosTreeTable();
            table.setTableType(TableType.UTXOS);
            table.initialize(entries);
            LegacyReplayWarning warning = new LegacyReplayWarning();
            warning.setStatus(Status.SHARED);
            var root = new javafx.scene.layout.VBox(15, warning, table);
            root.setStyle("-fx-padding: 20;");
            root.getStylesheets().addAll(AppServices.class.getResource("general.css").toExternalForm(), AppServices.class.getResource("wallet/utxos.css").toExternalForm());
            owner.setScene(new Scene(root, 1000, 480));
            root.applyCss();
            root.layout();
            assertTrue(warning.isVisible());
            assertTrue(warning.getText().contains("All selected inputs"));
            assertEquals(warning.getText(), ((javafx.scene.text.Text)warning.lookup(".text")).getText());
            assertEquals("Legacy chain", table.getColumns().getLast().getText());
            assertTrue(table.lookupAll(".tree-table-cell").stream().filter(Labeled.class::isInstance).map(Labeled.class::cast).anyMatch(cell -> "⚠ Replay risk".equals(cell.getText())));
            Path qa = Path.of("build/qa");
            Files.createDirectories(qa);
            ImageIO.write(SwingFXUtils.fromFXImage(root.snapshot(null, null), null), "png", qa.resolve("legacy-replay-light.png").toFile());
            warning.setStatus(Status.UNKNOWN);
            assertTrue(warning.isVisible());
            assertTrue(warning.getText().contains("unknown"));
            warning.setStatus(Status.ABSENT);
            assertFalse(warning.isVisible());
            assertFalse(warning.isManaged());
            warning.setStatus(Status.SHARED);
            root.getStylesheets().add(AppServices.class.getResource("darktheme.css").toExternalForm());
            root.applyCss();
            root.layout();
            ImageIO.write(SwingFXUtils.fromFXImage(root.snapshot(null, null), null), "png", qa.resolve("legacy-replay-dark.png").toFile());

            owner.setScene(new Scene(sendRoot, 1000, 760));
            ((SendController)send.getController()).setWalletForm(new WalletForm(new Storage(Path.of("build/desktop-test-home/replay-preview").toFile()), wallet));
            LegacyReplayWarning sendWarning = (LegacyReplayWarning)sendRoot.lookup("#legacyReplayWarning");
            sendWarning.setStatus(Status.SHARED);
            sendRoot.applyCss();
            sendRoot.layout();
            assertTrue(sendWarning.isVisible());
            assertEquals(sendWarning.getText(), ((javafx.scene.text.Text)sendWarning.lookup(".text")).getText());
            ImageIO.write(SwingFXUtils.fromFXImage(sendRoot.snapshot(null, null), null), "png", qa.resolve("send-legacy-replay.png").toFile());
            return null;
        });
    }

    @Test
    void signingBroadcastAndSeedRoutesStopWhenReplayWarningIsCancelled() throws Exception {
        for(String route : List.of("signPSBT", "broadcastTransaction", "signFromSeed")) {
            var tx = new com.sparrowwallet.drongo.protocol.Transaction();
            tx.addInput(com.sparrowwallet.drongo.protocol.Sha256Hash.wrap("ab".repeat(32)), 0, new com.sparrowwallet.drongo.protocol.Script(new byte[0]));
            tx.addOutput(1000, new com.sparrowwallet.drongo.protocol.Script(new byte[]{0x51}));
            var psbt = new com.sparrowwallet.drongo.psbt.PSBT(tx);
            psbt.getPsbtInputs().getFirst().setWitnessUtxo(new com.sparrowwallet.drongo.protocol.TransactionOutput(null, 1234, new com.sparrowwallet.drongo.protocol.Script(new byte[]{0x51})));
            var inputs = LegacyUtxoChecker.inputs(tx, psbt, null, null);
            GuardFixture fixture = new GuardFixture(Map.of(inputs.getFirst(), Status.SHARED));
            DeterministicSeed seed = new DeterministicSeed("response seminar brave tip suit recall often sound stick owner lottery motion", "", 0, DeterministicSeed.Type.BIP39);
            onFx(() -> {
                alertAnswer = ButtonType.CANCEL;
                Button button = new Button("Protected action");
                owner.setScene(new Scene(new StackPane(button), 400, 200));
                var controller = new com.sparrowwallet.sparrow.transaction.HeadersController();
                for(var entry : Map.of("headersForm", new com.sparrowwallet.sparrow.transaction.HeadersForm(new com.sparrowwallet.sparrow.transaction.TransactionData("Replay test", psbt)),
                        "signButton", button, "broadcastButton", button, "legacyReplayGuard", fixture).entrySet()) {
                    var field = controller.getClass().getDeclaredField(entry.getKey());
                    field.setAccessible(true);
                    field.set(controller, entry.getValue());
                }
                if(route.equals("signFromSeed")) {
                    var method = controller.getClass().getDeclaredMethod(route, DeterministicSeed.class);
                    method.setAccessible(true);
                    method.invoke(controller, seed);
                } else {
                    controller.getClass().getMethod(route, javafx.event.ActionEvent.class).invoke(controller, new javafx.event.ActionEvent());
                }
                return null;
            });
            assertTrue(fixture.finished.await(10, TimeUnit.SECONDS));
            onFx(() -> {
                assertFalse(psbt.isSigned());
                assertEquals("Legacy-chain replay risk", alerts.getLast());
                if(route.equals("signFromSeed")) assertTrue(seed.getMnemonicCode().isEmpty());
                return null;
            });
        }
    }

    private static class GuardFixture extends LegacyReplayGuard {
        private final Map<Utxo, Status> results;
        private final CountDownLatch finished = new CountDownLatch(1);

        private GuardFixture(Map<Utxo, Status> results) { this.results = results; }

        @Override
        protected Service<Map<Utxo, Status>> createService(List<Utxo> inputs) {
            var service = new Service<Map<Utxo, Status>>() {
                @Override protected Task<Map<Utxo, Status>> createTask() {
                    return new Task<>() {
                        @Override protected Map<Utxo, Status> call() { return results; }
                    };
                }
            };
            service.addEventHandler(javafx.concurrent.WorkerStateEvent.WORKER_STATE_SUCCEEDED, event -> Platform.runLater(finished::countDown));
            return service;
        }
    }

    private static void chooseDonation(Wallet wallet, ButtonType result) throws Exception {
        AtomicReference<Throwable> selectionError = new AtomicReference<>();
        onFx(() -> {
            WalletForm form = new WalletForm(new Storage(Path.of("build/test-home/donation-wallet").toFile()), wallet);
            walletWindows.put(owner, List.of(new WalletTabData(TabData.TabType.WALLET, form)));
            Platform.runLater(() -> {
                try {
                    DialogPane pane = (DialogPane)Window.getWindows().stream().map(w -> w.getScene().getRoot()).filter(DialogPane.class::isInstance).findFirst().orElseThrow();
                    ComboBox<?> choices = (ComboBox<?>)pane.lookup(".combo-box");
                    assertEquals(List.of(wallet), choices.getItems());
                    ((Button)pane.lookupButton(result)).fire();
                } catch(Throwable error) {
                    selectionError.set(error);
                    List.copyOf(Window.getWindows()).stream().filter(w -> w != owner).forEach(Window::hide);
                }
            });
            AppServices.openDonation(RECIPIENT);
            return null;
        });
        onFx(() -> null);
        assertNull(selectionError.get(), () -> String.valueOf(selectionError.get()));
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(20, TimeUnit.SECONDS);
    }

    public static class Recorder {
        final List<SendActionEvent> actions = new ArrayList<>();
        final List<SendPaymentsEvent> payments = new ArrayList<>();
        @Subscribe public void send(SendActionEvent event) { actions.add(event); }
        @Subscribe public void pay(SendPaymentsEvent event) { payments.add(event); }
    }
}
