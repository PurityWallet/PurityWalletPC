package com.sparrowwallet.sparrow.net;

import com.google.gson.GsonBuilder;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.protocol.*;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.ChainTip;
import com.sparrowwallet.sparrow.SparrowWallet;
import com.sparrowwallet.sparrow.io.Config;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@Tag("checkpoint")
class PurityMainnetSyncTest {
    @TempDir static Path home;
    private static final String TXID = "25b055c20d7bebd9d48a0a22b13b9bc6e355587b61cde954d7630c79bcd8c5c8";

    @BeforeAll static void isolate() { System.setProperty(SparrowWallet.APP_HOME_PROPERTY, home.toString()); }
    @AfterAll static void restoreHome() { System.clearProperty(SparrowWallet.APP_HOME_PROPERTY); }

    @Test
    @Timeout(180)
    void synchronizesEveryHeaderReplaysAndProvesAPostForkTransaction() throws Exception {
        Network.set(Network.MAINNET);
        Config config = Config.get();
        ServerType previousType = config.getServerType();
        CloseableTransport previousTransport = ElectrumServer.transport;
        ElectrumServerRpc previousRpc = ElectrumServer.electrumServerRpc;
        HeaderStore previousStore = ElectrumServer.headerStore;
        ServerCapability previousCapability = ElectrumServer.serverCapability;
        ChainTip previousTip = AppServices.getAnnouncedTip();
        var endpoint = PublicElectrumServer.PURITY.getServer();
        try(CloseableTransport tls = endpoint.getProtocol().getTransport(endpoint.getHostAndPort())) {
            config.setServerType(ServerType.PUBLIC_ELECTRUM_SERVER);
            assertTrue(config.isVerifyTransactions());
            tls.connect();
            Thread reader = new Thread(() -> {
                try { ((TcpTransport)tls).readInputLoop(); }
                catch(ServerException e) { /* transport closes with the test */ }
            }, "PurityAcceptanceReadThread");
            reader.setDaemon(true);
            reader.start();
            ElectrumServer.transport = tls;
            ElectrumServer.electrumServerRpc = new SimpleElectrumServerRpc();
            var rpc = ElectrumServer.electrumServerRpc;
            var version = rpc.getServerVersion(tls, "PurityWallet", ElectrumServer.SUPPORTED_VERSIONS);
            ElectrumServer.serverCapability = ElectrumServer.getServerCapability(version);
            assertTrue(ElectrumServer.serverCapability.supportsMerkleProofs());
            assertTrue(ElectrumServer.isVerificationMandatory());
            assertTrue(ElectrumServer.isVerifyingTransactions());

            BlockHeaderTip announced = rpc.subscribeBlockHeaders(tls);
            assertNull(ElectrumServer.getTipValidationError(announced));
            assertTrue(announced.height >= PurityHeaderState.ACTIVATION_HEIGHT);
            ChainTip tip = new ChainTip(announced.height, announced.getBlockHeader());
            AppServices.setAnnouncedTip(tip);
            ElectrumServer.headerStore = null;
            ElectrumServer server = new ElectrumServer();
            server.verifyMainnetChain(tip);
            server.syncHeaders(tip);
            HeaderStore store = ElectrumServer.getHeaderStore();
            assertEquals(961632, store.getStartHeight());
            assertTrue(store.getTipHeight() >= tip.height());
            assertEquals(tip.header().getHash(), store.getHash(tip.height()));
            assertEquals(PurityHeaderState.ACTIVATION_HASH, store.getHash(PurityHeaderState.ACTIVATION_HEIGHT));
            assertEquals("000000000000003e346d906ea62abdf577ede019541e82796f81e243ff907626", store.getHash(963647).toString());
            assertTrue(store.getChainWork().signum() > 0);

            HeaderStore replayed = HeaderStore.load(ChainCheckpoints.get(Network.MAINNET));
            assertEquals(store.getTipHeight(), replayed.getTipHeight());
            assertEquals(store.getTipHash(), replayed.getTipHash());
            assertEquals(store.getChainWork(), replayed.getChainWork());
            ElectrumServer.headerStore = replayed;
            assertEquals(replayed.getHeader(963647).getHash(), server.getProvenHeader(Sha256Hash.wrap(TXID), 963647).getHash());
            var reference = new BlockTransaction(Sha256Hash.wrap(TXID), 963647, null, null, null);
            var proof = rpc.getTransactionMerkleProofs(tls, null, List.of(reference)).get(TXID + ":963647");
            assertNotNull(proof);
            assertTrue(ElectrumServer.verifyProof(reference.getHash(), proof, replayed.getHeader(963647)));
            assertFalse(ElectrumServer.verifyProof(Sha256Hash.ZERO_HASH, proof, replayed.getHeader(963647)));
            var tampered = new TransactionMerkleProof();
            tampered.block_height = proof.block_height;
            tampered.pos = proof.pos;
            tampered.merkle = new ArrayList<>(proof.merkle);
            assertFalse(tampered.merkle.isEmpty());
            tampered.merkle.set(0, Sha256Hash.ZERO_HASH.toString());
            assertFalse(ElectrumServer.verifyProof(reference.getHash(), tampered, replayed.getHeader(963647)));
            String rawTx = rpc.getTransactions(tls, null, Set.of(TXID)).get(TXID);
            assertEquals(reference.getHash(), new Transaction(Utils.hexToBytes(rawTx)).getTxId());
            assertEquals("00000000000000000002a7c4c1e48d76c5a37902165a270156b7a8d72728a054", server.getVerifiedHeader(800000).getHash().toString());
            assertTrue(config.isVerifyTransactions());
            assertTrue(ElectrumServer.isVerificationMandatory());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("verifiedAtUtc", Instant.now().toString());
            result.put("endpoint", endpoint.getUrl());
            result.put("serverVersion", version);
            result.put("startHeight", store.getStartHeight());
            result.put("announcedHeight", tip.height());
            result.put("announcedHash", tip.header().getHash().toString());
            result.put("verifiedHeight", replayed.getTipHeight());
            result.put("verifiedHash", replayed.getTipHash().toString());
            result.put("headerCount", replayed.getTipHeight() - replayed.getStartHeight() + 1);
            result.put("chainWork", replayed.getChainWork().toString());
            result.put("cacheReplay", true);
            result.put("merkleProofTxid", TXID);
            result.put("invalidProofsRejected", true);
            Path report = Path.of("build/reports/purity-mainnet-sync.json");
            Files.createDirectories(report.getParent());
            Files.writeString(report, new GsonBuilder().setPrettyPrinting().create().toJson(result));
            System.out.println("Purity full sync passed: " + result);
        } finally {
            ElectrumServer.transport = previousTransport;
            ElectrumServer.electrumServerRpc = previousRpc;
            ElectrumServer.headerStore = previousStore;
            ElectrumServer.serverCapability = previousCapability;
            ElectrumServer.verifiedHistoricalHeaders.clear();
            ElectrumServer.provenTransactionHeaders.clear();
            AppServices.setAnnouncedTip(previousTip);
            config.setServerType(previousType);
            Network.set(null);
        }
    }
}
