package com.sparrowwallet.sparrow.pwc;

import com.fasterxml.jackson.databind.JsonNode;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.crypto.Bip322;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.InteractionServices;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.settings.PwcSettingsController;
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
import org.bitcoinpurity.pwc.provider.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/** The PWC prompts and settings page in a real JavaFX toolkit (run by {@code desktopTest}, under Xvfb in CI). */
@EnabledIfSystemProperty(named = "purity.ui.tests", matches = "true")
class PwcDesktopTest {
    private static final PeerInfo PEER = new PeerInfo(42, Path.of("/usr/bin/trader"));
    private static final Requester REQUESTER = new Requester("cl-1", "Trader", PEER);
    private static Stage owner;

    @TempDir
    File dir;

    @BeforeAll
    static void startDesktop() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            started.countDown();
        });
        assertTrue(started.await(10, TimeUnit.SECONDS));
        onFx(() -> {
            AppServices.initialize(new Application() {
                public void start(Stage stage) {}
            }, new InteractionServices() {
                public Optional<ButtonType> showAlert(String title, String content, Alert.AlertType type, Node graphic, ButtonType... buttons) {
                    return Optional.of(ButtonType.OK);
                }

                public Optional<String> requestPassphrase(String name, Keystore keystore) {
                    return Optional.empty();
                }
            });
            owner = new Stage();
            owner.setScene(new Scene(new StackPane(), 600, 460));
            owner.show();
            return null;
        });
    }

    @BeforeEach
    void regtest() {
        Network.set(Network.REGTEST);
    }

    @AfterAll
    static void closeDesktop() throws Exception {
        onFx(() -> {
            owner.close();
            return null;
        });
        Platform.exit();
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(30, TimeUnit.SECONDS);
    }

    /** Shows the dialog, runs {@code whileShown} once it is on screen, and returns its result. A failed check closes the dialog and is rethrown. */
    private static Boolean show(Callable<PwcPromptDialog> create, Consumer<PwcPromptDialog> whileShown) throws Exception {
        Throwable[] failure = new Throwable[1];
        Boolean result = onFx(() -> {
            PwcPromptDialog dialog = create.call();
            dialog.initOwner(owner);
            dialog.addEventHandler(DialogEvent.DIALOG_SHOWN, event -> Platform.runLater(() -> {
                try {
                    whileShown.accept(dialog);
                } catch(Throwable e) {
                    failure[0] = e;
                    dialog.close();
                }
            }));
            return dialog.showAndWait().orElse(null);
        });
        if(failure[0] instanceof Error error) {
            throw error;
        }
        if(failure[0] != null) {
            throw new IllegalStateException(failure[0]);
        }
        return result;
    }

    private static List<Node> nodes(Node root) {
        Set<Node> found = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Node> pending = new ArrayDeque<>(List.of(root));
        while(!pending.isEmpty()) {
            Node node = pending.pop();
            if(found.add(node)) {
                if(node instanceof Parent parent) {
                    pending.addAll(parent.getChildrenUnmodifiable());
                }
                if(node instanceof ScrollPane scroll && scroll.getContent() != null) {
                    pending.add(scroll.getContent());
                }
                if(node instanceof TitledPane titled && titled.getContent() != null) {
                    pending.add(titled.getContent());
                }
            }
        }
        return new ArrayList<>(found);
    }

    private static String allText(Node root) {
        StringBuilder text = new StringBuilder();
        for(Node node : nodes(root)) {
            if(node instanceof Labeled labeled && labeled.getText() != null) {
                text.append(labeled.getText()).append('\n');
            }
            if(node instanceof TextInputControl input) {
                text.append(input.getText()).append('\n');
            }
        }
        return text.toString();
    }

    private static List<CheckBox> checkBoxes(Node root) {
        return nodes(root).stream().filter(CheckBox.class::isInstance).map(CheckBox.class::cast).toList();
    }

    private static void snapshot(PwcPromptDialog dialog, String name) {
        try {
            Path qa = Path.of("build/qa");
            Files.createDirectories(qa);
            ImageIO.write(SwingFXUtils.fromFXImage(dialog.getDialogPane().snapshot(null, null), null), "png", qa.resolve(name).toFile());
        } catch(Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void pairingShowsTheCodeAndTheReportedApplicationAndNeverApprovesByDefault() throws Exception {
        Boolean result = show(() -> new PwcPairingDialog(new PairingRequest("Trader", "Claims to trade", "123456", PEER), () -> false), dialog -> {
            String text = allText(dialog.getDialogPane());
            assertTrue(text.contains("123 456"), text);
            assertTrue(text.contains("/usr/bin/trader (process 42)"), text);
            assertTrue(text.contains("\"Trader\" (not verified)"), text);
            assertFalse(dialog.approveButton().isDefaultButton());
            assertFalse(((Button)dialog.getDialogPane().lookupButton(PwcPromptDialog.REJECT)).isDefaultButton());
            snapshot(dialog, "pwc-pairing.png");
            dialog.approveButton().fire();
        });
        assertEquals(Boolean.TRUE, result);
    }

    @Test
    void closingThePromptRejectsIt() throws Exception {
        AtomicBoolean withdrawn = new AtomicBoolean();
        PwcPromptDialog[] shown = new PwcPromptDialog[1];
        Boolean result = show(() -> new PwcPairingDialog(new PairingRequest("Trader", null, "123456", PEER), withdrawn::get), dialog -> {
            shown[0] = dialog;
            dialog.close();
        });
        assertEquals(Boolean.FALSE, result);
        assertFalse(shown[0].isWithdrawn());
    }

    @Test
    void aWithdrawnPromptClosesByItself() throws Exception {
        AtomicBoolean withdrawn = new AtomicBoolean();
        PwcPromptDialog[] shown = new PwcPromptDialog[1];
        Boolean result = show(() -> new PwcPairingDialog(new PairingRequest("Trader", null, "123456", PEER), withdrawn::get), dialog -> {
            shown[0] = dialog;
            withdrawn.set(true);
        });
        assertEquals(Boolean.FALSE, result);
        assertTrue(shown[0].isWithdrawn());
    }

    @Test
    void psbtPromptShowsComputedAmountsAndNeedsTheTestNetworkAcknowledged() throws Exception {
        PsbtSignRequest request = new PsbtSignRequest(REQUESTER, "acc", "regtest", List.of(1), 2,
                List.of(new OutputSummary("bcrt1qpaymentaddressxx", "0014", 250_000, false), new OutputSummary("bcrt1qchangeaddress", "0014", 99_000, true)),
                1_000, 1_250, new byte[0]);
        Boolean result = show(() -> new PwcPsbtDialog(request, "Main — Deposit", 500_000, () -> false), dialog -> {
            String text = allText(dialog.getDialogPane());
            assertTrue(text.contains("bcrt 1qpa ymen tadd ress xx"), text);
            assertTrue(text.contains("0.00250000 BTC (250,000 sat)"), text);
            assertTrue(text.contains("0.00001000 BTC (1,000 sat) at 1.25 sat/vB"), text);
            assertTrue(text.contains("0.00251000 BTC (251,000 sat)"), "total leaving the account");
            assertTrue(text.contains("Change returning to this account (1)"), text);
            assertTrue(text.contains("inputs #2 of 2"), text);
            assertTrue(text.contains("does not send"), text);
            assertTrue(dialog.approveButton().isDisabled(), "the test network warning must be acknowledged first");
            snapshot(dialog, "pwc-psbt.png");
            checkBoxes(dialog.getDialogPane()).forEach(box -> box.setSelected(true));
            assertFalse(dialog.approveButton().isDisabled());
            dialog.approveButton().fire();
        });
        assertEquals(Boolean.TRUE, result);
    }

    @Test
    void messagePromptMarksInvisibleCharacters() throws Exception {
        MessageSignRequest request = new MessageSignRequest(REQUESTER, "acc", "regtest", "bcrt1qaddress", "Pay​me ", MessageFormat.BIP322_SIMPLE);
        Boolean result = show(() -> new PwcMessageDialog(request, "Main — Deposit", () -> false), dialog -> {
            String text = allText(dialog.getDialogPane());
            assertTrue(text.contains("Pay​me "), "the exact message is shown");
            assertTrue(text.contains("U+200B"), text);
            assertEquals(2, checkBoxes(dialog.getDialogPane()).size(), "invisible characters and the test network each need an acknowledgement");
            dialog.close();
        });
        assertEquals(Boolean.FALSE, result);
    }

    @Test
    void permissionPromptNarrowsTheGrant() throws Exception {
        Instant requestedExpiry = Instant.now().plusSeconds(90 * 24 * 3600);
        PermissionRequest request = new PermissionRequest(REQUESTER, List.of("wallets:read", "balances:read", "psbt:sign"), null, List.of(), List.of(),
                requestedExpiry, new GrantConstraints(100_000L, null));
        List<PwcPermissionDialog.Account> accounts = List.of(new PwcPermissionDialog.Account("a1", "Main — Deposit"), new PwcPermissionDialog.Account("a2", "Main — Account #1"));
        PwcPermissionDialog[] shown = new PwcPermissionDialog[1];
        Boolean result = show(() -> new PwcPermissionDialog(request, "regtest", accounts, () -> false), dialog -> {
            shown[0] = (PwcPermissionDialog)dialog;
            List<CheckBox> boxes = checkBoxes(dialog.getDialogPane());
            boxes.stream().filter(box -> box.getText().startsWith("See balances")).findFirst().orElseThrow().setSelected(false);
            boxes.stream().filter(box -> box.getText().equals("Main — Account #1")).findFirst().orElseThrow().setSelected(false);
            TextField limit = (TextField)dialog.getDialogPane().lookupAll(".text-field").iterator().next();
            limit.setText("200000");
            assertTrue(dialog.approveButton().isDisabled(), "the limit cannot be raised above the request");
            limit.setText("50000");
            assertFalse(dialog.approveButton().isDisabled());
            snapshot(dialog, "pwc-permission.png");
            dialog.approveButton().fire();
        });
        assertEquals(Boolean.TRUE, result);
        GrantChoice choice = onFx(() -> shown[0].choice());
        assertEquals("regtest", choice.network());
        assertEquals(List.of("wallets:read", "psbt:sign"), choice.scopes());
        assertEquals(List.of("a1"), choice.accountIds());
        assertEquals(requestedExpiry, choice.expiresAt());
        assertEquals(50_000L, choice.constraints().maxAmountSatsPerRequest());
    }

    @Test
    void permissionForAnotherNetworkCanOnlyBeRejected() throws Exception {
        PermissionRequest request = new PermissionRequest(REQUESTER, List.of("wallets:read"), "mainnet", List.of(), List.of(), null, null);
        Boolean result = show(() -> new PwcPermissionDialog(request, "regtest", List.of(), () -> false), dialog -> {
            assertTrue(dialog.approveButton().isDisabled());
            assertTrue(allText(dialog.getDialogPane()).contains("You can only reject"));
            dialog.close();
        });
        assertEquals(Boolean.FALSE, result);
    }

    /** Polls for a shown PWC prompt and fires one of its buttons. */
    private static void answerWhenShown(ButtonType button, Consumer<DialogPane> check) throws Exception {
        for(int i = 0; i < 200; i++) {
            boolean answered = onFx(() -> {
                for(Window window : List.copyOf(Window.getWindows())) {
                    if(window.isShowing() && window.getScene() != null && window.getScene().getRoot() instanceof DialogPane pane && pane.lookupButton(PwcPromptDialog.APPROVE) != null) {
                        check.accept(pane);
                        checkBoxes(pane).stream().filter(box -> box.getStyle().contains("-fx-accent")).forEach(box -> box.setSelected(true));
                        ((Button)pane.lookupButton(button)).fire();
                        return true;
                    }
                }
                return false;
            });
            if(answered) {
                return;
            }
            Thread.sleep(100);
        }
        fail("No PWC prompt was shown");
    }

    @Test
    @EnabledIf("com.sparrowwallet.sparrow.pwc.PwcPlatform#hasNativeLibrary")
    void promptsAnswerTheProviderAndTheSettingsPageManagesThePairing() throws Exception {
        FakeHost host = new FakeHost();
        Wallet wallet = PwcTestWallets.software("Desktop", ScriptType.P2WPKH);
        PwcTestWallets.fund(wallet, 0, 300_000, 900);
        host.forms.add(new WalletForm(new Storage(new File(dir, "desktop")), wallet));
        Path endpoints = PwcPlatform.isWindows() ? null : dir.toPath().resolve("run");
        PwcService service = new PwcService(host, new File(dir, "pwc"), endpoints, "pwc-desktop-" + UUID.randomUUID().toString().substring(0, 8), PwcPromptHandler::new);
        var field = AppServices.class.getDeclaredField("pwcService");
        field.setAccessible(true);
        field.set(AppServices.get(), service);
        service.start();
        ExecutorService client = Executors.newSingleThreadExecutor();
        try {
            assertEquals(PwcService.Status.RUNNING, service.getStatus(), service.getMessage());
            String accountId = service.getBackend().getIds().accountId(wallet);
            Address address = wallet.getFreshNode(KeyPurpose.RECEIVE).getAddress();

            Future<JsonNode> session = client.submit(() -> {
                LocalTestClient local = LocalTestClient.connect(service.getEndpoint());
                local.hello();
                local.pair("Desktop Test", "135790");
                JsonNode grant = local.await(local.call("session.requestPermissions", Map.of("scopes", List.of("wallets:read", "messages:sign", "psbt:sign"),
                        "idempotency_key", UUID.randomUUID().toString())));
                assertEquals("completed", grant.get("state").asText(), grant.toString());
                JsonNode signed = local.await(local.call("message.sign", Map.of("account_id", accountId, "address", address.toString(), "message", "Desktop test",
                        "format", "bip322-simple", "idempotency_key", UUID.randomUUID().toString())));
                JsonNode refused = local.await(local.call("message.sign", Map.of("account_id", accountId, "address", address.toString(), "message", "Refuse me",
                        "format", "bip322-simple", "idempotency_key", UUID.randomUUID().toString())));
                local.close();
                return new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().set("signed", signed).deepCopy();
            });
            answerWhenShown(PwcPromptDialog.APPROVE, pane -> assertTrue(allText(pane).contains("135 790"), allText(pane)));
            answerWhenShown(PwcPromptDialog.APPROVE, pane -> assertTrue(allText(pane).contains("Desktop — "), allText(pane)));
            answerWhenShown(PwcPromptDialog.APPROVE, pane -> assertTrue(allText(pane).contains("Desktop test"), allText(pane)));
            answerWhenShown(PwcPromptDialog.REJECT, pane -> assertTrue(allText(pane).contains("Refuse me"), allText(pane)));
            JsonNode signed = session.get(60, TimeUnit.SECONDS).get("signed");
            assertEquals("completed", signed.get("state").asText(), signed.toString());
            String signature = signed.get("result").get("signature").asText();
            assertTrue(Bip322.verifyMessageBip322(ScriptType.P2WPKH, address, "Desktop test", signature));
            assertEquals(1, service.pairedClients().size());

            onFx(() -> {
                FXMLLoader loader = new FXMLLoader(AppServices.class.getResource("settings/pwc.fxml"));
                Parent root = loader.load();
                ((PwcSettingsController)loader.getController()).initializeView(Config.get());
                owner.setScene(new Scene(root, 760, 560));
                root.applyCss();
                root.layout();
                assertTrue(((Label)root.lookup("#status")).getText().startsWith("Listening at"), ((Label)root.lookup("#status")).getText());
                @SuppressWarnings("unchecked")
                TableView<PairedClient> clients = (TableView<PairedClient>)root.lookup("#clients");
                assertEquals(1, clients.getItems().size());
                clients.getSelectionModel().selectFirst();
                @SuppressWarnings("unchecked")
                TableView<Grant> grants = (TableView<Grant>)root.lookup("#grants");
                assertEquals(1, grants.getItems().size());
                assertTrue(grants.getItems().getFirst().accountIds().contains(accountId));
                Path qa = Path.of("build/qa");
                Files.createDirectories(qa);
                ImageIO.write(SwingFXUtils.fromFXImage(root.snapshot(null, null), null), "png", qa.resolve("pwc-settings.png").toFile());

                grants.getSelectionModel().selectFirst();
                ((Button)root.lookup("#revoke")).fire();
                assertEquals(GrantStatus.REVOKED, service.grants().getFirst().status());
                ((Button)root.lookup("#unpair")).fire();
                assertEquals(List.of(), service.pairedClients());
                assertEquals(0, clients.getItems().size());
                return null;
            });
        } finally {
            client.shutdownNow();
            service.stop();
            field.set(AppServices.get(), null);
        }
    }
}
