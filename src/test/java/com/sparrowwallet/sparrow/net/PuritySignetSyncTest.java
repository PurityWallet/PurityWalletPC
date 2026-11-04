package com.sparrowwallet.sparrow.net;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.VerificationException;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.ChainTip;
import com.sparrowwallet.sparrow.SparrowWallet;
import com.sparrowwallet.sparrow.io.Config;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("checkpoint")
class PuritySignetSyncTest {
    @TempDir static Path home;
    private static final String TXID = "35453173b38a5f533be32e4552740949340255aaa88d3bed2ed513f0e3472155";

    @BeforeAll static void isolate() { System.setProperty(SparrowWallet.APP_HOME_PROPERTY, home.toString()); }
    @AfterAll static void restoreHome() { System.clearProperty(SparrowWallet.APP_HOME_PROPERTY); Network.set(Network.MAINNET); }

    @Test
    @Timeout(120)
    void synchronizesSignetReplaysCacheAndVerifiesAnActivationTransaction() throws Exception {
        Network.set(Network.SIGNET);
        Config config = Config.get();
        ServerType previousType = config.getServerType();
        CloseableTransport previousTransport = ElectrumServer.transport;
        ElectrumServerRpc previousRpc = ElectrumServer.electrumServerRpc;
        HeaderStore previousStore = ElectrumServer.headerStore;
        ServerCapability previousCapability = ElectrumServer.serverCapability;
        ChainTip previousTip = AppServices.getAnnouncedTip();
        var endpoint = PublicElectrumServer.PURITY_SIGNET.getServer();
        try(CloseableTransport tls = endpoint.getProtocol().getTransport(endpoint.getHostAndPort())) {
            config.setServerType(ServerType.PUBLIC_ELECTRUM_SERVER);
            tls.connect();
            Thread reader = new Thread(() -> {
                try { ((TcpTransport)tls).readInputLoop(); }
                catch(ServerException e) { /* transport closes with the test */ }
            }, "PuritySignetAcceptanceReadThread");
            reader.setDaemon(true);
            reader.start();
            ElectrumServer.transport = tls;
            ElectrumServer.electrumServerRpc = new SimpleElectrumServerRpc();
            var rpc = ElectrumServer.electrumServerRpc;
            var version = rpc.getServerVersion(tls, "PurityWallet", ElectrumServer.SUPPORTED_VERSIONS);
            ElectrumServer.serverCapability = ElectrumServer.getServerCapability(version);
            assertTrue(ElectrumServer.isVerificationMandatory());
            assertTrue(ElectrumServer.isVerifyingTransactions());
            BlockHeaderTip announced = rpc.subscribeBlockHeaders(tls);
            assertNull(ElectrumServer.getTipValidationError(announced));
            ChainTip tip = new ChainTip(announced.height, announced.getBlockHeader());
            AppServices.setAnnouncedTip(tip);
            ElectrumServer.headerStore = null;
            ElectrumServer server = new ElectrumServer();
            assertThrows(VerificationException.class, () -> server.verifyPurityChain(new ChainTip(327021, tip.header())));
            server.verifyPurityChain(tip);
            server.syncHeaders(tip);
            HeaderStore store = ElectrumServer.getHeaderStore();
            assertEquals(327022, store.getStartHeight());
            assertEquals(tip.header().getHash(), store.getHash(tip.height()));
            assertEquals(PurityHeaderState.SIGNET_ACTIVATION_HASH, store.getHash(327022));
            assertTrue(store.getChainWork().signum() > 0);
            HeaderStore replayed = HeaderStore.load(ChainCheckpoints.get(Network.SIGNET));
            assertEquals(store.getTipHash(), replayed.getTipHash());
            assertEquals(store.getChainWork(), replayed.getChainWork());
            ElectrumServer.headerStore = replayed;
            var reference = new BlockTransaction(Sha256Hash.wrap(TXID), 327022, null, null, null);
            var proof = rpc.getTransactionMerkleProofs(tls, null, List.of(reference)).get(TXID + ":327022");
            assertNotNull(proof);
            assertEquals(replayed.getHeader(327022).getHash(), server.getProvenHeader(reference.getHash(), 327022).getHash());
            assertTrue(ElectrumServer.verifyProof(reference.getHash(), proof, replayed.getHeader(327022)));
            assertFalse(ElectrumServer.verifyProof(Sha256Hash.ZERO_HASH, proof, replayed.getHeader(327022)));
            assertEquals(ChainCheckpoints.get(Network.SIGNET).getHash(2015), server.getVerifiedHeader(2015).getHash());
            System.out.println("Purity signet sync passed: endpoint=" + endpoint.getUrl() + ", height=" + tip.height()
                    + ", hash=" + tip.header().getHash() + ", headers=" + (tip.height() - 327021)
                    + ", work=" + replayed.getChainWork() + ", cache replay and valid/invalid Merkle proofs passed");
        } finally {
            ElectrumServer.transport = previousTransport;
            ElectrumServer.electrumServerRpc = previousRpc;
            ElectrumServer.headerStore = previousStore;
            ElectrumServer.serverCapability = previousCapability;
            ElectrumServer.verifiedHistoricalHeaders.clear();
            ElectrumServer.provenTransactionHeaders.clear();
            AppServices.setAnnouncedTip(previousTip);
            config.setServerType(previousType);
        }
    }
}
