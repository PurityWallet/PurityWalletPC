package com.sparrowwallet.sparrow.pwc;

import com.sparrowwallet.drongo.SecureString;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionOutPoint;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.control.DeviceSignDialog;
import com.sparrowwallet.sparrow.control.LegacyReplayGuard;
import com.sparrowwallet.sparrow.control.WalletPasswordDialog;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.net.LegacyUtxoChecker;
import com.sparrowwallet.sparrow.wallet.WalletForm;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.stage.Window;
import javafx.util.Duration;
import org.bitcoinpurity.pwc.provider.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

/** Shows each PWC prompt to the user, one at a time, and answers it exactly once. */
public class PwcPromptHandler implements PromptHandler {
    private static final Logger log = LoggerFactory.getLogger(PwcPromptHandler.class);

    /** Signs with the account's USB device after the user confirms on it, on the JavaFX thread; null if cancelled or withdrawn. */
    interface DeviceSigner {
        PSBT sign(Wallet wallet, PSBT psbt, BooleanSupplier withdrawn);
    }

    private final PwcService service;
    private final PwcWalletBackend backend;
    private final DeviceSigner device;
    private final LegacyReplayGuard replayGuard = new LegacyReplayGuard();

    public PwcPromptHandler(PwcService service, PwcWalletBackend backend) {
        this(service, backend, PwcPromptHandler::signWithDeviceDialog);
    }

    PwcPromptHandler(PwcService service, PwcWalletBackend backend, DeviceSigner device) {
        this.service = service;
        this.backend = backend;
        this.device = device;
    }

    /** The existing device dialog, closed when the Provider withdraws the request. */
    private static PSBT signWithDeviceDialog(Wallet wallet, PSBT psbt, BooleanSupplier withdrawn) {
        List<String> fingerprints = wallet.getKeystores().stream().map(Keystore::getKeyDerivation).map(derivation -> derivation.getMasterFingerprint()).toList();
        DeviceSignDialog dialog = new DeviceSignDialog(wallet, fingerprints, psbt);
        Window owner = AppServices.getActiveWindow();
        if(owner != null) {
            dialog.initOwner(owner);
        }
        Timeline withdrawalCheck = new Timeline(new KeyFrame(Duration.millis(500), event -> {
            if(withdrawn.getAsBoolean()) {
                dialog.close();
            }
        }));
        withdrawalCheck.setCycleCount(Timeline.INDEFINITE);
        withdrawalCheck.play();
        try {
            return dialog.showAndWait().orElse(null);
        } finally {
            withdrawalCheck.stop();
        }
    }

    /** Called on the SDK thread {@code pwc-prompts}; returns once the prompt is answered or withdrawn. */
    @Override
    public void onPrompt(Prompt prompt) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                if(backend.isClosed() || prompt.isWithdrawn()) {
                    answer(prompt::reject);
                    done.complete(null);
                } else {
                    show(prompt, done);
                }
            } catch(Throwable e) {
                log.error("Could not show a PWC prompt", e);
                answer(prompt::reject);
                done.complete(null);
            }
        });
        //Stop waiting once PWC stops, since the JavaFX thread may no longer run the dialog when the application quits
        while(!done.isDone() && service.isRunning()) {
            try {
                done.get(1, TimeUnit.SECONDS);
            } catch(TimeoutException e) {
                //Still deciding
            } catch(InterruptedException | ExecutionException e) {
                return;
            }
        }
    }

    private static void answer(Runnable answer) {
        try {
            answer.run();
        } catch(IllegalStateException e) {
            log.debug("PWC prompt already answered", e);
        }
    }

    /** The accounts a permission prompt may share: those requested, or every readable account when the request names none (Core §8.1). */
    static List<PwcPermissionDialog.Account> offeredAccounts(PermissionRequest request, List<WalletInfo> wallets, PwcWalletBackend backend) {
        boolean listed = wallets.stream().anyMatch(wallet -> request.walletIds().contains(wallet.walletId())
                || wallet.accountIds().stream().anyMatch(request.accountIds()::contains));
        List<PwcPermissionDialog.Account> offered = new ArrayList<>();
        for(WalletInfo wallet : wallets) {
            for(String accountId : wallet.accountIds()) {
                if(!listed || request.walletIds().contains(wallet.walletId()) || request.accountIds().contains(accountId)) {
                    String accountName = backend.account(accountId).map(AccountInfo::displayName).orElse(accountId);
                    offered.add(new PwcPermissionDialog.Account(accountId, wallet.displayName() + " — " + accountName));
                }
            }
        }
        return offered;
    }

    private String accountName(String accountId) {
        WalletForm form = backend.walletForm(accountId);
        return form == null ? accountId : form.getMasterWallet().getName() + " — " + form.getWallet().getDisplayName();
    }

    private void show(Prompt prompt, CompletableFuture<Void> done) {
        switch(prompt) {
            case PairingPrompt pairing -> decide(new PwcPairingDialog(pairing.request(), pairing::isWithdrawn), pairing, () -> {
                answer(pairing::approve);
                done.complete(null);
            }, done);
            case PermissionPrompt permission -> {
                PwcPermissionDialog dialog = new PwcPermissionDialog(permission.request(), PwcWalletBackend.network(),
                        offeredAccounts(permission.request(), backend.wallets(), backend), permission::isWithdrawn);
                decide(dialog, permission, () -> {
                    answer(() -> permission.grant(dialog.choice()));
                    done.complete(null);
                }, done);
            }
            case PsbtSignPrompt psbt -> {
                PwcPsbtDialog dialog = new PwcPsbtDialog(psbt.request(), accountName(psbt.request().accountId()), Limits.defaults().highFeeRate(), psbt::isWithdrawn);
                decide(dialog, psbt, () -> checkReplay(psbt, done), done);
            }
            case MessageSignPrompt message -> {
                PwcMessageDialog dialog = new PwcMessageDialog(message.request(), accountName(message.request().accountId()), message::isWithdrawn);
                MessageSignRequest request = message.request();
                String subject = PwcWalletBackend.messageSubject(normalized(request.address()), request.message(), request.format());
                decide(dialog, message, () -> unlockThenApprove(message, request.accountId(), subject, message::approve, done), done);
            }
        }
    }

    private void decide(PwcPromptDialog dialog, Prompt prompt, Runnable approved, CompletableFuture<Void> done) {
        Window owner = AppServices.getActiveWindow();
        if(owner != null) {
            dialog.initOwner(owner);
        }
        boolean approve = dialog.showAndWait().orElse(false);
        if(dialog.isWithdrawn() || prompt.isWithdrawn() || backend.isClosed()) {
            answer(prompt::reject);
            done.complete(null);
        } else if(approve) {
            approved.run();
        } else {
            answer(prompt::reject);
            done.complete(null);
        }
    }

    private static String normalized(String address) {
        try {
            return Address.fromString(address).toString();
        } catch(Exception e) {
            return address;
        }
    }

    /**
     * REQ-011 and REQ-017: the legacy-chain check and replay acknowledgement come after the user approves and before the wallet signs.
     * Continuing records a clearance that the later PWC broadcast of this transaction relies on.
     */
    private void checkReplay(PsbtSignPrompt prompt, CompletableFuture<Void> done) {
        String accountId = prompt.request().accountId();
        WalletForm form = backend.walletForm(accountId);
        if(form == null) {
            answer(prompt::reject);
            done.complete(null);
            return;
        }
        List<LegacyUtxoChecker.Utxo> inputs;
        Transaction transaction;
        try {
            PSBT psbt = new PSBT(prompt.request().psbt());
            transaction = psbt.getTransaction();
            inputs = replayInputs(psbt, form.getWallet().getWalletTransactions());
        } catch(Exception e) {
            log.warn("Could not read the PWC PSBT for the replay check", e);
            answer(prompt::reject);
            done.complete(null);
            return;
        }
        Runnable approve = () -> {
            backend.replayCleared(transaction);
            prompt.approve();
        };
        String subject = PwcWalletBackend.psbtSubject(prompt.request().psbt(), prompt.request().inputsToSign());
        Runnable sign = backend.signsWithDevice(accountId) ? () -> signOnDeviceThenApprove(prompt, accountId, subject, approve, done)
                : () -> unlockThenApprove(prompt, accountId, subject, approve, done);
        replayGuard.check(inputs, () -> !prompt.isWithdrawn() && !backend.isClosed(), new Button(), sign)
                .thenAccept(continued -> {
                    if(!continued) {
                        answer(prompt::reject);
                        done.complete(null);
                    }
                });
    }

    static List<LegacyUtxoChecker.Utxo> replayInputs(PSBT psbt, Map<Sha256Hash, BlockTransaction> history) {
        List<LegacyUtxoChecker.Utxo> inputs = new ArrayList<>();
        for(int i = 0; i < psbt.getTransaction().getInputs().size(); i++) {
            TransactionOutPoint outpoint = psbt.getTransaction().getInputs().get(i).getOutpoint();
            BlockTransaction known = history.get(outpoint.getHash());
            Transaction previous = known == null ? null : known.getTransaction();
            if(previous == null || !outpoint.getHash().equals(previous.getTxId())) {
                previous = psbt.getPsbtInputs().get(i).getNonWitnessUtxo();
            }
            TransactionOutput output = previous != null && outpoint.getHash().equals(previous.getTxId())
                    && outpoint.getIndex() < previous.getOutputs().size() ? previous.getOutputs().get((int)outpoint.getIndex()) : null;
            inputs.add(new LegacyUtxoChecker.Utxo(outpoint.getHash().toString(), outpoint.getIndex(), output == null ? -1 : output.getValue(),
                    output == null ? null : Utils.bytesToHex(output.getScriptBytes())));
        }
        return inputs;
    }

    /** The device confirmation is part of the prompt flow, so that {@code signPsbt} never waits for the user (REQ-017). */
    private void signOnDeviceThenApprove(PsbtSignPrompt prompt, String accountId, String subject, Runnable approve, CompletableFuture<Void> done) {
        WalletForm form = backend.walletForm(accountId);
        byte[] requested = prompt.request().psbt();
        PSBT signed = null;
        if(form != null) {
            try {
                signed = device.sign(form.getWallet(), new PSBT(requested), prompt::isWithdrawn);
                if(signed != null) {
                    new PSBT(requested).verifyCombinedSignatures(signed);
                }
            } catch(Exception e) {
                log.warn("The device signature for a PWC request failed", e);
                signed = null;
            }
        }
        if(prompt.isWithdrawn() || backend.isClosed()) {
            answer(prompt::reject);
            done.complete(null);
            return;
        }
        if(signed == null) {
            answer(prompt::reject);
            done.complete(null);
            return;
        }
        backend.deviceSigned(accountId, subject, signed);
        answer(approve);
        if(prompt.isWithdrawn()) {
            backend.discardApproval(accountId);
        }
        done.complete(null);
    }

    /** Unlocking is a separate step after approval (PWC-UX-001 §1.5); the decrypted copy serves only the signature just approved. */
    private void unlockThenApprove(Prompt prompt, String accountId, String subject, Runnable approve, CompletableFuture<Void> done) {
        WalletForm form = backend.walletForm(accountId);
        if(form == null) {
            answer(prompt::reject);
            done.complete(null);
            return;
        }
        Wallet wallet = form.getWallet();
        if(!wallet.isEncrypted()) {
            answer(approve);
            done.complete(null);
            return;
        }

        Wallet copy = wallet.copy();
        WalletPasswordDialog passwordDialog = new WalletPasswordDialog(copy.getMasterName(), WalletPasswordDialog.PasswordRequirement.LOAD);
        Window owner = AppServices.getActiveWindow();
        if(owner != null) {
            passwordDialog.initOwner(owner);
        }
        Optional<SecureString> password = passwordDialog.showAndWait();
        if(password.isEmpty() || prompt.isWithdrawn() || backend.isClosed()) {
            password.ifPresent(SecureString::clear);
            answer(prompt::reject);
            done.complete(null);
            return;
        }
        Storage.DecryptWalletService decryptWalletService = new Storage.DecryptWalletService(copy, password.get());
        decryptWalletService.setOnSucceeded(event -> {
            Wallet decrypted = decryptWalletService.getValue();
            if(prompt.isWithdrawn() || backend.isClosed()) {
                decrypted.clearPrivate();
                answer(prompt::reject);
            } else {
                backend.unlocked(accountId, subject, decrypted);
                answer(approve);
                if(prompt.isWithdrawn()) {
                    backend.discardApproval(accountId);
                }
            }
            done.complete(null);
        });
        decryptWalletService.setOnFailed(event -> {
            AppServices.showErrorDialog("Incorrect Password", "The wallet could not be unlocked, so the request was rejected.");
            answer(prompt::reject);
            done.complete(null);
        });
        decryptWalletService.start();
    }
}
