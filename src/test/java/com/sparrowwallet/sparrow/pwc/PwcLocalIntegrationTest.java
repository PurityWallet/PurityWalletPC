package com.sparrowwallet.sparrow.pwc;

import com.fasterxml.jackson.databind.JsonNode;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.wallet.WalletForm;
import org.bitcoinpurity.pwc.provider.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** A real Provider and LOCAL endpoint with a regtest test wallet, driven by a LOCAL client. No transaction is signed or broadcast. */
@EnabledIf("com.sparrowwallet.sparrow.pwc.PwcPlatform#hasNativeLibrary")
class PwcLocalIntegrationTest {
    @TempDir
    File dir;

    @Test
    void pairsAuthenticatesReadsTheBalanceAndRejectsASignature() throws Exception {
        Network.set(Network.REGTEST);
        FakeHost host = new FakeHost();
        Wallet wallet = PwcTestWallets.software("Integration", ScriptType.P2WPKH);
        PwcTestWallets.fund(wallet, 0, 1_000_000, 900);
        PwcTestWallets.fund(wallet, 1, 500_000, 900);
        host.forms.add(new WalletForm(new Storage(new File(dir, "integration")), wallet));

        List<Object> requests = new CopyOnWriteArrayList<>();
        AtomicReference<PwcWalletBackend> backend = new AtomicReference<>();
        PromptHandler handler = prompt -> {
            switch(prompt) {
                case PairingPrompt pairing -> {
                    requests.add(pairing.request());
                    pairing.approve();
                }
                case PermissionPrompt permission -> {
                    requests.add(permission.request());
                    List<String> accounts = backend.get().wallets().stream().flatMap(w -> w.accountIds().stream()).toList();
                    permission.grant(new GrantChoice("regtest", permission.request().scopes(), List.of(), accounts, null, null));
                }
                case PsbtSignPrompt psbt -> {
                    requests.add(psbt.request());
                    psbt.reject();
                }
                case MessageSignPrompt message -> message.reject();
            }
        };
        String providerId = "pwc-it-" + UUID.randomUUID().toString().substring(0, 8);
        Path endpoints = PwcPlatform.isWindows() ? null : dir.toPath().resolve("run");
        PwcService service = new PwcService(host, new File(dir, "pwc"), endpoints, providerId, (s, b) -> {
            backend.set(b);
            return handler;
        });
        service.start();
        try {
            assertEquals(PwcService.Status.RUNNING, service.getStatus(), service.getMessage());

            LocalTestClient.Credentials credentials;
            try(LocalTestClient client = LocalTestClient.connect(service.getEndpoint())) {
                client.hello();
                credentials = client.pair("Integration Test", "246810");
            }
            PairingRequest pairing = (PairingRequest)requests.getFirst();
            assertEquals("Integration Test", pairing.claimedName());
            assertEquals("246810", pairing.pairingCode());
            assertEquals(ProcessHandle.current().pid(), pairing.peer().pid().longValue());
            assertEquals(1, service.pairedClients().size());

            String accountId = backend.get().getIds().accountId(wallet);
            try(LocalTestClient client = LocalTestClient.connect(service.getEndpoint())) {
                client.hello();
                client.authenticate(credentials);

                LocalTestClient.RpcError denied = assertThrows(LocalTestClient.RpcError.class, () -> client.call("wallet.list", Map.of()));
                assertEquals("PERMISSION_REQUIRED", denied.code);

                JsonNode grant = client.await(client.call("session.requestPermissions", Map.of("scopes", List.of("wallets:read", "balances:read", "psbt:sign"),
                        "network", "regtest", "idempotency_key", UUID.randomUUID().toString())));
                assertEquals("completed", grant.get("state").asText(), grant.toString());

                JsonNode wallets = client.call("wallet.list", Map.of()).get("wallets");
                assertEquals(1, wallets.size());
                assertEquals(backend.get().getIds().walletId(wallet), wallets.get(0).get("wallet_id").asText());
                assertEquals(accountId, wallets.get(0).get("account_ids").get(0).asText());

                JsonNode balance = client.call("account.getBalance", Map.of("account_id", accountId));
                assertEquals("1500000", balance.get("confirmed_sats").asText(), balance.toString());
                assertEquals("1500000", balance.get("spendable_sats").asText());

                byte[] foreign = PwcTestWallets.software("Other", ScriptType.P2WPKH, PwcTestWallets.OTHER_SEED).getFreshNode(KeyPurpose.RECEIVE).getOutputScript().getProgram();
                CreatedPsbt created = backend.get().createPsbt(accountId, new CreateRequest(List.of(new TxOut(250_000, foreign)), 2_000, null, true), Set.of());
                JsonNode signing = client.await(client.call("psbt.sign", Map.of("account_id", accountId, "psbt", Base64.getEncoder().encodeToString(created.psbt()),
                        "idempotency_key", UUID.randomUUID().toString())));
                assertEquals("rejected", signing.get("state").asText(), signing.toString());
                assertEquals("USER_REJECTED", signing.get("error").get("code").asText());
            }

            PsbtSignRequest signRequest = (PsbtSignRequest)requests.stream().filter(PsbtSignRequest.class::isInstance).findFirst().orElseThrow();
            assertEquals(accountId, signRequest.accountId());
            assertTrue(signRequest.outputs().stream().anyMatch(o -> !o.toThisAccount() && o.amountSats() == 250_000));
            assertTrue(signRequest.outputs().stream().anyMatch(OutputSummary::toThisAccount));
            assertTrue(signRequest.feeSats() > 0);

            service.stop();
            service.start();
            try(LocalTestClient client = LocalTestClient.connect(service.getEndpoint())) {
                client.hello();
                client.authenticate(credentials);
                assertEquals(1, client.call("wallet.list", Map.of()).get("wallets").size(), "pairing and grant survive a restart");
            }
        } finally {
            service.stop();
        }
    }
}
