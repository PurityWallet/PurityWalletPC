package com.sparrowwallet.sparrow.net;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.protocol.BlockHeader;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.VerificationException;
import com.sparrowwallet.sparrow.SparrowWallet;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.io.Server;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class PuritySignetTest {
    @TempDir Path home;
    private List<BlockHeader> headers;

    @BeforeEach
    void signet() throws Exception {
        Network.set(Network.SIGNET);
        System.setProperty(SparrowWallet.APP_HOME_PROPERTY, home.toString());
        try(var input = Objects.requireNonNull(getClass().getResourceAsStream("purity-signet-327022-327056.bin"))) {
            byte[] bytes = input.readAllBytes();
            headers = new ArrayList<>();
            for(int i = 0; i < bytes.length; i += 80) headers.add(new BlockHeader(bytes, i));
        }
    }

    @AfterEach
    void restore() {
        Network.set(Network.MAINNET);
        System.clearProperty(SparrowWallet.APP_HOME_PROPERTY);
    }

    @Test
    void startupAndRestartOfferTheSameSupportedNetworks() {
        assertEquals(List.of(Network.MAINNET, Network.SIGNET, Network.REGTEST), SparrowWallet.SUPPORTED_NETWORKS);
    }

    @Test
    void transactionLinksUseExactlyOneNetworkPathAndPreserveTemplates() {
        assertEquals("https://mempool.bitcoinpurity.org/signet/tx/abc", BlockExplorer.getTransactionUrl(BlockExplorer.MEMPOOL_SPACE.getServer(), "abc"));
        assertEquals("https://mempool.bitcoinpurity.org/signet/tx/abc", BlockExplorer.getTransactionUrl(new Server("https://mempool.bitcoinpurity.org"), "abc"));
        assertEquals("https://custom.explorer/signet/tx/abc", BlockExplorer.getTransactionUrl(new Server("https://custom.explorer/signet/"), "abc"));
        assertEquals("https://custom.explorer/transaction/abc", BlockExplorer.getTransactionUrl(new Server("https://custom.explorer/transaction/{0}"), "abc"));
        Network.set(Network.MAINNET);
        assertEquals("https://mempool.bitcoinpurity.org/tx/abc", BlockExplorer.getTransactionUrl(BlockExplorer.MEMPOOL_SPACE.getServer(), "abc"));
    }

    @Test
    void signetAsertUsesItsOwnPowLimit() {
        BigInteger limit = Network.SIGNET.getProofOfWorkLimit();
        assertEquals(limit, PurityHeaderState.calculateTarget(limit, 600, 0, limit));
        assertEquals(limit, PurityHeaderState.calculateTarget(limit, 30000000, 0, limit));
        assertEquals(BigInteger.ONE, PurityHeaderState.calculateTarget(limit, -30000000, 0, limit));
    }

    @Test
    void anchorsPuritySignetAndRetainsSharedHistory() {
        var profile = ChainCheckpoints.get(Network.SIGNET);
        assertEquals(327021, profile.getMaxHeight());
        assertEquals(327022, profile.getMinimumTipHeight());
        assertEquals(0x1d153796L, profile.getBitsAfter(327021));
        assertEquals("00000013c351c0d13fc2c8b3547413b3ed30854a2434869c064a5076bab6aad6", profile.getHash(327021).toString());
        assertEquals(327021, profile.getPinnedHeightAtOrAbove(327020));
        assertEquals(Network.SIGNET.getHeaderCheckpoints().getHash(2015), profile.getHash(2015));
        assertThrows(IllegalArgumentException.class, () -> profile.getHash(327022));
    }

    @Test
    void validatesActivationAndChangingAsertTargetsWithRealWork() {
        var state = ChainCheckpoints.get(Network.SIGNET).newChainState();
        assertEquals(327021, state.getHeight());
        BigInteger work = BigInteger.ZERO;
        for(BlockHeader header : headers) {
            state.add(header);
            work = work.add(HeaderChainState.getWork(header.getDifficultyTarget()));
        }
        assertEquals(327056, state.getHeight());
        assertEquals(headers.getLast().getHash(), state.getHash());
        assertEquals(work, state.getChainWork());
        assertNotEquals(headers.getFirst().getDifficultyTarget(), headers.get(1).getDifficultyTarget());
    }

    @Test
    void rejectsWrongActivationDifficultyPowAndTimestampsWithoutAdvancing() {
        var state = ChainCheckpoints.get(Network.SIGNET).newChainState();
        assertTrue(assertThrows(VerificationException.class, () -> state.add(change(headers.getFirst(), 76, headers.getFirst().getNonce() ^ 1))).getMessage().contains("activation"));
        state.add(headers.getFirst());
        assertTrue(assertThrows(VerificationException.class, () -> state.add(change(headers.get(1), 72, headers.getFirst().getDifficultyTarget()))).getMessage().contains("difficulty"));
        assertTrue(assertThrows(VerificationException.class, () -> state.add(change(headers.get(1), 76, headers.get(1).getNonce() ^ 1))).getMessage().contains("proof of work"));
        assertTrue(assertThrows(VerificationException.class, () -> state.add(change(headers.get(1), 68, 1))).getMessage().contains("median"));
        assertTrue(assertThrows(VerificationException.class, () -> state.add(change(headers.get(1), 68, System.currentTimeMillis() / 1000 + 10800))).getMessage().contains("future"));
        assertEquals(327022, state.getHeight());
        state.add(headers.get(1));
    }

    @Test
    void checksEachPeersActivationEvenWithAPopulatedLocalCache() throws Exception {
        var previousStore = ElectrumServer.headerStore;
        var previousRpc = ElectrumServer.electrumServerRpc;
        var previousTransport = ElectrumServer.transport;
        try {
            var store = HeaderStore.load(ChainCheckpoints.get(Network.SIGNET));
            store.append(headers);
            ElectrumServer.headerStore = store;
            ElectrumServer.transport = (CloseableTransport)java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class[]{CloseableTransport.class}, (proxy, method, args) -> {
                        throw new UnsupportedOperationException("No network connection expected");
                    });
            var peerHeader = new java.util.concurrent.atomic.AtomicReference<>(headers.get(1));
            ElectrumServer.electrumServerRpc = (ElectrumServerRpc)java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class[]{ElectrumServerRpc.class}, (proxy, method, args) -> {
                        assertEquals("getBlockHeadersChunk", method.getName());
                        assertEquals(327022, args[1]);
                        assertEquals(1, args[2]);
                        var chunk = new BlockHeaders();
                        chunk.count = 1;
                        chunk.hex = Utils.bytesToHex(peerHeader.get().bitcoinSerialize());
                        return chunk;
                    });
            var tip = new com.sparrowwallet.sparrow.ChainTip(327056, headers.getLast());
            var server = new ElectrumServer();
            assertThrows(VerificationException.class, () -> server.verifyPurityChain(tip));
            peerHeader.set(headers.getFirst());
            server.verifyPurityChain(tip);
            assertEquals(327056, store.getTipHeight());
        } finally {
            ElectrumServer.headerStore = previousStore;
            ElectrumServer.electrumServerRpc = previousRpc;
            ElectrumServer.transport = previousTransport;
        }
    }

    @Test
    void replaysAndRewindsAnIsolatedSignetCache() throws Exception {
        Path oldCache = Storage.getHeadersDir().toPath().resolve("318528");
        Files.write(oldCache, new byte[]{1, 2, 3});
        var profile = ChainCheckpoints.get(Network.SIGNET);
        var store = HeaderStore.load(profile);
        store.append(headers);
        var replayed = HeaderStore.load(profile);
        assertEquals(store.getTipHash(), replayed.getTipHash());
        assertEquals(store.getChainWork(), replayed.getChainWork());
        replayed.truncate(327030);
        replayed.append(headers.subList(9, headers.size()));
        assertEquals(store.getTipHash(), replayed.getTipHash());
        assertEquals("purity-signet-asert-v1", profile.getCacheNamespace());
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(oldCache));
        assertEquals(Sha256Hash.wrap("00000004b42cf68f94be9dd89b65e44cae31eee382137221b83b5b261d3e3c20"), replayed.getHash(327022));
    }

    private static BlockHeader change(BlockHeader header, int offset, long value) {
        byte[] bytes = header.bitcoinSerialize();
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, (int)value);
        return new BlockHeader(bytes);
    }
}
