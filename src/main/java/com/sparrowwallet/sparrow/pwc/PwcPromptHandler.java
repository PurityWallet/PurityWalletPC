package com.sparrowwallet.sparrow.pwc;

import com.sparrowwallet.drongo.SecureString;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.control.LegacyReplayGuard;
import com.sparrowwallet.sparrow.control.WalletPasswordDialog;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.net.LegacyUtxoChecker;
import com.sparrowwallet.sparrow.wallet.WalletForm;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.stage.Window;
import org.bitcoinpurity.pwc.provider.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Shows each PWC prompt to the user, one at a time, and answers it exactly once. */
public class PwcPromptHandler implements PromptHandler {
    private static final Logger log = LoggerFactory.getLogger(PwcPromptHandler.class);

    private final PwcService service;
    private final PwcWalletBackend backend;
    private final LegacyReplayGuard replayGuard = new LegacyReplayGuard();

    public PwcPromptHandler(PwcService service, PwcWalletBackend backend) {
        this.service = service;
        this.backend = backend;
    }

    /** Called on the SDK thread {@code pwc-prompts}; returns once the prompt is answered or withdrawn. */
    @Override
    public void onPrompt(Prompt prompt) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                show(prompt, done);
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
                service.saveSoon();
                done.complete(null);
            }, done);
            case PermissionPrompt permission -> {
                PwcPermissionDialog dialog = new PwcPermissionDialog(permission.request(), PwcWalletBackend.network(),
                        offeredAccounts(permission.request(), backend.wallets(), backend), permission::isWithdrawn);
                decide(dialog, permission, () -> {
                    answer(() -> permission.grant(dialog.choice()));
                    service.saveSoon();
                    done.complete(null);
                }, done);
            }
            case PsbtSignPrompt psbt -> {
                PwcPsbtDialog dialog = new PwcPsbtDialog(psbt.request(), accountName(psbt.request().accountId()), Limits.defaults().highFeeRate(), psbt::isWithdrawn);
                decide(dialog, psbt, () -> checkReplay(psbt, done), done);
            }
            case MessageSignPrompt message -> {
                PwcMessageDialog dialog = new PwcMessageDialog(message.request(), accountName(message.request().accountId()), message::isWithdrawn);
                decide(dialog, message, () -> unlockThenApprove(message, message.request().accountId(), message::approve, done), done);
            }
        }
    }

    private void decide(PwcPromptDialog dialog, Prompt prompt, Runnable approved, CompletableFuture<Void> done) {
        Window owner = AppServices.getActiveWindow();
        if(owner != null) {
            dialog.initOwner(owner);
        }
        boolean approve = dialog.showAndWait().orElse(false);
        if(dialog.isWithdrawn()) {
            done.complete(null);
        } else if(approve) {
            approved.run();
        } else {
            answer(prompt::reject);
            done.complete(null);
        }
    }

    /** REQ-011: the legacy-chain check and replay acknowledgement come after the user approves and before the wallet signs. */
    private void checkReplay(PsbtSignPrompt prompt, CompletableFuture<Void> done) {
        String accountId = prompt.request().accountId();
        WalletForm form = backend.walletForm(accountId);
        if(form == null) {
            answer(prompt::reject);
            done.complete(null);
            return;
        }
        List<LegacyUtxoChecker.Utxo> inputs;
        try {
            PSBT psbt = new PSBT(prompt.request().psbt());
            inputs = LegacyUtxoChecker.inputs(psbt.getTransaction(), psbt, form.getWallet(), form.getWallet().getWalletTransactions());
        } catch(Exception e) {
            log.warn("Could not read the PWC PSBT for the replay check", e);
            answer(prompt::reject);
            done.complete(null);
            return;
        }
        replayGuard.check(inputs, () -> !prompt.isWithdrawn(), new Button(), () -> unlockThenApprove(prompt, accountId, prompt::approve, done))
                .thenAccept(continued -> {
                    if(!continued) {
                        answer(prompt::reject);
                        done.complete(null);
                    }
                });
    }

    /** Unlocking is a separate step after approval (PWC-UX-001 §1.5); the decrypted copy serves only the signature just approved. */
    private void unlockThenApprove(Prompt prompt, String accountId, Runnable approve, CompletableFuture<Void> done) {
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
        if(password.isEmpty() || prompt.isWithdrawn()) {
            answer(prompt::reject);
            done.complete(null);
            return;
        }
        Storage.DecryptWalletService decryptWalletService = new Storage.DecryptWalletService(copy, password.get());
        decryptWalletService.setOnSucceeded(event -> {
            backend.unlocked(accountId, decryptWalletService.getValue());
            answer(approve);
            if(prompt.isWithdrawn()) {
                backend.discardUnlocked(accountId);
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
