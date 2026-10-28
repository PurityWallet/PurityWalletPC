package com.sparrowwallet.sparrow.net;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.protocol.*;
import com.sparrowwallet.sparrow.SparrowWallet;
import com.sparrowwallet.sparrow.io.Storage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PurityHeaderStateTest {
    @TempDir static Path home;
    private static List<BlockHeader> headers;

    @BeforeAll
    static void loadFixture() throws IOException {
        System.setProperty(SparrowWallet.APP_HOME_PROPERTY, home.toString());
        byte[] bytes;
        try(var input = PurityHeaderStateTest.class.getResourceAsStream("purity-mainnet-961632-963650.bin")) {
            bytes = Objects.requireNonNull(input).readAllBytes();
        }
        assertEquals(2019 * 80, bytes.length);
        headers = new ArrayList<>();
        for(int i = 0; i < bytes.length; i += 80) {
            headers.add(new BlockHeader(bytes, i));
        }
    }

    @BeforeEach void mainnet() { Network.set(Network.MAINNET); }
    @AfterAll static void restore() { System.clearProperty(SparrowWallet.APP_HOME_PROPERTY); Network.set(null); }

    @Test
    void usesOnlySharedHistoricalCheckpoints() {
        var checkpoints = ChainCheckpoints.get(Network.MAINNET);
        assertEquals(961631, checkpoints.getMaxHeight());
        assertEquals(961631, checkpoints.newChainState().getHeight());
        for(int height = 2015; height <= 961631; height += 2016) {
            assertEquals(Network.MAINNET.getHeaderCheckpoints().getHash(height), checkpoints.getHash(height));
        }
        assertThrows(IllegalArgumentException.class, () -> checkpoints.getHash(963647));
        assertThrows(IllegalArgumentException.class, () -> checkpoints.getPinnedHeightAtOrAbove(961632));
    }

    @Test
    void validatesActivationAndAFullPeriodOfAsertWithRealWork() {
        var state = ChainCheckpoints.get(Network.MAINNET).newChainState();
        BigInteger work = BigInteger.ZERO;
        for(BlockHeader header : headers) {
            state.add(header);
            work = work.add(HeaderChainState.getWork(header.getDifficultyTarget()));
        }
        assertEquals(963650, state.getHeight());
        assertEquals(headers.getLast().getHash(), state.getHash());
        assertEquals(work, state.getChainWork());
        assertEquals("0000000000000000003ea74f4dafdda7ed4e02c4c1ccb9768e0ca4f9e1a35159", headers.get(5).getHash().toString());
        assertEquals(0x196f5c1aL, headers.get(2016).getDifficultyTarget());
        assertEquals(0x196f114eL, headers.get(2017).getDifficultyTarget());
    }

    @Test
    void rejectsTheBitcoinConstantDifficultyRuleAfterReplacingThePin() {
        var state = ChainCheckpoints.get(Network.MAINNET).newChainState();
        headers.subList(0, 2017).forEach(state::add);
        BlockHeader staleBits = change(headers.get(2017), 72, headers.get(2016).getDifficultyTarget());
        var failure = assertThrows(VerificationException.class, () -> state.add(staleBits));
        assertTrue(failure.getMessage().contains("difficulty"));
        assertEquals(963648, state.getHeight());
        state.add(headers.get(2017));
    }

    @Test
    void rejectsWrongActivationHashWithoutAdvancing() {
        var state = ChainCheckpoints.get(Network.MAINNET).newChainState();
        headers.subList(0, 5).forEach(state::add);
        BlockHeader wrong = change(headers.get(5), 76, headers.get(5).getNonce() ^ 1);
        assertTrue(assertThrows(VerificationException.class, () -> state.add(wrong)).getMessage().contains("activation"));
        assertEquals(961636, state.getHeight());
        state.add(headers.get(5));
    }

    @Test
    void enforcesLinkageProofOfWorkSeededMedianAndFutureBound() {
        var state = ChainCheckpoints.get(Network.MAINNET).newChainState();
        assertThrows(VerificationException.class, () -> state.add(headers.get(1)));
        BlockHeader wrongNonce = change(headers.getFirst(), 76, headers.getFirst().getNonce() ^ 1);
        assertFalse(wrongNonce.verifyProofOfWork());
        assertTrue(assertThrows(VerificationException.class, () -> state.add(wrongNonce)).getMessage().contains("proof of work"));
        BlockHeader oldTime = change(headers.getFirst(), 68, 1);
        assertTrue(assertThrows(VerificationException.class, () -> state.add(oldTime)).getMessage().contains("median"));
        BlockHeader future = change(headers.getFirst(), 68, System.currentTimeMillis() / 1000 + 10800);
        assertTrue(assertThrows(VerificationException.class, () -> state.add(future)).getMessage().contains("future"));
        assertEquals(961631, state.getHeight());
        assertEquals(BigInteger.ZERO, state.getChainWork());
    }

    @Test
    void rejectsTamperedOrIncompleteMedianSeed() throws IOException {
        List<BlockHeader> seed;
        try(var input = Objects.requireNonNull(PurityHeaderStateTest.class.getResourceAsStream("/purity/mainnet-anchor.hex"))) {
            seed = new ArrayList<>(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.US_ASCII).lines().map(s -> new BlockHeader(Utils.hexToBytes(s))).toList());
        }
        assertThrows(VerificationException.class, () -> new PurityHeaderState(seed.subList(1, 11)));
        seed.set(0, change(seed.getFirst(), 68, seed.getFirst().getTime() + 1));
        assertThrows(VerificationException.class, () -> new PurityHeaderState(seed));
    }

    @Test
    void integerAsertRoundsTowardZeroAndClamps() {
        BigInteger reference = BigInteger.ONE.shiftLeft(208);
        assertEquals(reference, PurityHeaderState.calculateTarget(reference, 600, 0));
        assertEquals(reference, PurityHeaderState.calculateTarget(reference, 599, 0));
        assertEquals(reference, PurityHeaderState.calculateTarget(reference, 601, 0));
        assertEquals(reference.shiftLeft(1), PurityHeaderState.calculateTarget(reference, 87000, 0));
        assertEquals(reference.shiftRight(1), PurityHeaderState.calculateTarget(reference, -85800, 0));
        assertEquals(reference.shiftRight(1), PurityHeaderState.calculateTarget(reference, -85801, 0));
        assertEquals(BigInteger.ONE, PurityHeaderState.calculateTarget(reference, -30000000, 0));
        assertEquals(Network.MAINNET.getProofOfWorkLimit(), PurityHeaderState.calculateTarget(reference, 30000000, 0));
    }

    @Test
    void reloadsAndRewindsPurityCacheWithoutTouchingBitcoinCache() throws IOException {
        Path legacy = Storage.getHeadersDir().toPath().resolve("963648");
        Files.write(legacy, new byte[]{1, 2, 3});
        var checkpoints = ChainCheckpoints.get(Network.MAINNET);
        var store = HeaderStore.load(checkpoints);
        store.append(headers);
        var reloaded = HeaderStore.load(checkpoints);
        assertEquals(963650, reloaded.getTipHeight());
        assertEquals(store.getChainWork(), reloaded.getChainWork());
        reloaded.truncate(963647);
        reloaded.append(headers.subList(2016, 2019));
        assertEquals(store.getTipHash(), reloaded.getTipHash());
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(legacy));
        Path cache = Storage.getHeadersDir().toPath().resolve("purity-mainnet-asert-v1/961632");
        byte[] corrupted = Files.readAllBytes(cache);
        corrupted[6 * 80 + 76] ^= 1;
        Files.write(cache, corrupted);
        assertEquals(961637, HeaderStore.load(checkpoints).getTipHeight());
    }

    @Test
    void matchesCoreIntegerReferenceVectorsAcrossHeights() {
        BigInteger reference = BigInteger.ONE.shiftLeft(208);
        long[] offsets = {-86401, -86400, -12345, -1, 0, 1, 12345, 43199, 86399, 86400, 86401};
        //Core pow_tests integer vectors scaled down by 24 bits to stay within the mainnet target domain.
        String[] targets = {"8000000000000000000000000000000000000000000000000000",
                "8000000000000000000000000000000000000000000000000000",
                "e7e3800000000000000000000000000000000000000000000000",
                "10000000000000000000000000000000000000000000000000000",
                "10000000000000000000000000000000000000000000000000000",
                "10000000000000000000000000000000000000000000000000000",
                "11aae000000000000000000000000000000000000000000000000",
                "16a01000000000000000000000000000000000000000000000000",
                "1ffff000000000000000000000000000000000000000000000000",
                "20000000000000000000000000000000000000000000000000000",
                "20000000000000000000000000000000000000000000000000000"};
        for(long height : new long[]{0, 4, 2016}) {
            for(int i = 0; i < offsets.length; i++) {
                assertEquals(new BigInteger(targets[i], 16), PurityHeaderState.calculateTarget(reference, 600 * (height + 1) + offsets[i], height));
            }
        }
        assertEquals(reference.multiply(BigInteger.valueOf(131071)).shiftRight(16), PurityHeaderState.calculateTarget(reference, 86999, 0));
        assertEquals(reference.multiply(BigInteger.valueOf(131071)).shiftRight(17), PurityHeaderState.calculateTarget(reference, 598, 0));
    }

    @Test
    void refusesAPeerBelowActivationBeforeAnyNetworkRequest() {
        assertThrows(VerificationException.class, () -> new ElectrumServer().verifyMainnetChain(
                new com.sparrowwallet.sparrow.ChainTip(961636, headers.get(4))));
    }

    @Test
    void rechecksOldPostForkProofsOutsideTheRecentReorgWindow() throws Exception {
        var previousStore = ElectrumServer.headerStore;
        int previousFork = ElectrumServer.lastReorgForkHeight;
        try {
            var store = HeaderStore.load(ChainCheckpoints.get(Network.MAINNET));
            store.truncate(961631);
            store.append(headers);
            ElectrumServer.headerStore = store;
            ElectrumServer.lastReorgForkHeight = Integer.MAX_VALUE;
            var wallet = new com.sparrowwallet.drongo.wallet.Wallet("Old Bitcoin proof");
            wallet.setPolicyType(com.sparrowwallet.drongo.policy.PolicyType.SINGLE_HD);
            wallet.setScriptType(ScriptType.P2WPKH);
            wallet.setStoredBlockHeight(970000);
            var bitcoinHash = Network.MAINNET.getHeaderCheckpoints().getHash(963647);
            wallet.updateTransactions(Map.of(Sha256Hash.ZERO_HASH, new com.sparrowwallet.drongo.wallet.BlockTransaction(
                    Sha256Hash.ZERO_HASH, 963647, null, null, null, bitcoinHash)));
            Class<?> lockClass = Arrays.stream(ElectrumServer.class.getDeclaredClasses()).filter(c -> c.getSimpleName().equals("WalletSyncLock")).findFirst().orElseThrow();
            var constructor = lockClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object lock = constructor.newInstance();
            var check = ElectrumServer.class.getDeclaredMethod("checkStoredProofs", com.sparrowwallet.drongo.wallet.Wallet.class, lockClass);
            check.setAccessible(true);
            assertEquals(true, check.invoke(null, wallet, lock));
            assertEquals(963646, ElectrumServer.lastReorgForkHeight);
        } finally {
            ElectrumServer.headerStore = previousStore;
            ElectrumServer.lastReorgForkHeight = previousFork;
        }
    }

    @Test
    void checksEachPeersActivationEvenWithAPopulatedLocalCache() throws Exception {
        var previousStore = ElectrumServer.headerStore;
        var previousRpc = ElectrumServer.electrumServerRpc;
        var previousTransport = ElectrumServer.transport;
        try {
            var store = HeaderStore.load(ChainCheckpoints.get(Network.MAINNET));
            store.truncate(961631);
            store.append(headers);
            ElectrumServer.headerStore = store;
            ElectrumServer.transport = (CloseableTransport)java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class[]{CloseableTransport.class}, (proxy, method, args) -> {
                        throw new UnsupportedOperationException("No network connection expected");
                    });
            var peerHeader = new java.util.concurrent.atomic.AtomicReference<>(headers.get(6));
            var calls = new java.util.concurrent.atomic.AtomicInteger();
            ElectrumServer.electrumServerRpc = (ElectrumServerRpc)java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class[]{ElectrumServerRpc.class}, (proxy, method, args) -> {
                        assertEquals("getBlockHeadersChunk", method.getName());
                        assertEquals(961637, args[1]);
                        assertEquals(1, args[2]);
                        calls.incrementAndGet();
                        var chunk = new BlockHeaders();
                        chunk.count = 1;
                        chunk.max = 2016;
                        chunk.hex = Utils.bytesToHex(peerHeader.get().bitcoinSerialize());
                        return chunk;
                    });
            var tip = new com.sparrowwallet.sparrow.ChainTip(963650, headers.getLast());
            var server = new ElectrumServer();
            assertThrows(VerificationException.class, () -> server.verifyMainnetChain(tip));
            assertEquals(1, calls.get());
            peerHeader.set(headers.get(5));
            server.verifyMainnetChain(tip);
            assertEquals(2, calls.get());
            assertEquals(963650, store.getTipHeight());
        } finally {
            ElectrumServer.headerStore = previousStore;
            ElectrumServer.electrumServerRpc = previousRpc;
            ElectrumServer.transport = previousTransport;
        }
    }

    private static BlockHeader change(BlockHeader header, int offset, long value) {
        byte[] raw = header.bitcoinSerialize();
        ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, (int)value);
        return new BlockHeader(raw);
    }
}
