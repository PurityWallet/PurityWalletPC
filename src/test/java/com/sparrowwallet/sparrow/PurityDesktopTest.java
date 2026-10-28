package com.sparrowwallet.sparrow;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.*;
import com.sparrowwallet.drongo.policy.Policy;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.*;
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
                    return Optional.empty();
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
