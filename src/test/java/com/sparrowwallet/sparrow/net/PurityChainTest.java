package com.sparrowwallet.sparrow.net;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.protocol.BlockHeader;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.VerificationException;
import com.sparrowwallet.sparrow.SparrowWallet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PurityChainTest {
    @AfterEach
    void resetNetwork() {
        Network.set(null);
    }

    @Test
    void productIdentityAndPublicEndpoints() {
        assertEquals("PurityWallet", SparrowWallet.APP_NAME);
        assertEquals("1.1.0", SparrowWallet.APP_VERSION + SparrowWallet.APP_VERSION_SUFFIX);
        assertEquals("puritywallet", SparrowWallet.APP_ID);
        assertEquals("puritywallet.home", SparrowWallet.APP_HOME_PROPERTY);
        assertEquals("PURITYWALLET_NETWORK", SparrowWallet.NETWORK_ENV_PROPERTY);
        Network.set(Network.MAINNET);
        assertEquals(List.of("ssl://electrum.bitcoinpurity.org:50002"), PublicElectrumServer.getServers().stream().map(PublicElectrumServer::getUrl).toList());
        assertEquals("https://mempool.bitcoinpurity.org", BlockExplorer.MEMPOOL_SPACE.getServer().getUrl());
        for(BroadcastSource source : BroadcastSource.values()) {
            if(source.getSupportedNetworks().contains(Network.MAINNET)) {
                assertEquals("https://mempool.bitcoinpurity.org", source.getTlsUrl());
                assertEquals(source.getTlsUrl(), source.getBaseUrl(null));
            }
        }
    }

    @Test
    void activationPinAndHistoricalPinSelection() {
        Network.set(Network.MAINNET);
        HeaderCheckpoints checkpoints = HeaderCheckpoints.get(Network.MAINNET);
        assertEquals(961637, checkpoints.getMaxHeight());
        assertEquals(Sha256Hash.wrap("0000000000000000003ea74f4dafdda7ed4e02c4c1ccb9768e0ca4f9e1a35159"), checkpoints.getHash(961637));
        assertEquals(961637, checkpoints.getPinnedHeightAtOrAbove(961632));
        assertEquals(961631, checkpoints.getPinnedHeightAtOrAbove(961631));
        assertThrows(IllegalArgumentException.class, () -> checkpoints.getHash(961636));
    }

    @Test
    void validatesRealPurityHeadersAndRejectsBitcoinFork() throws Exception {
        Network.set(Network.MAINNET);
        List<String> lines;
        try(var stream = getClass().getResourceAsStream("/purity/headers.txt")) {
            lines = new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }
        HeaderChainState state = HeaderCheckpoints.get(Network.MAINNET).newChainState();
        for(String hex : lines) {
            state.add(new BlockHeader(Utils.hexToBytes(hex)));
        }
        assertEquals(961637 + lines.size(), state.getHeight());
        HeaderChainState wrongChain = HeaderCheckpoints.get(Network.MAINNET).newChainState();
        assertThrows(VerificationException.class, () -> wrongChain.add(Network.MAINNET.getGenesisHeader()));
        BlockHeader changedTarget = new BlockHeader(Utils.hexToBytes(lines.getFirst()));
        byte[] altered = changedTarget.bitcoinSerialize();
        altered[72] ^= 1;
        assertThrows(VerificationException.class, () -> HeaderCheckpoints.get(Network.MAINNET).newChainState().add(new BlockHeader(altered)));
    }

    @Test
    void asertUsesOneDayHalfLife() {
        Network.set(Network.MAINNET);
        long reference = 0x1c008000L;
        assertEquals(reference, HeaderChainState.calculateAsert(reference, 600, 0));
        assertEquals(Utils.encodeCompactBits(Utils.decodeCompactBits(reference).shiftLeft(1)), HeaderChainState.calculateAsert(reference, 600 + 86400, 0));
        assertEquals(Utils.encodeCompactBits(Utils.decodeCompactBits(reference).shiftRight(1)), HeaderChainState.calculateAsert(reference, 600 - 86400, 0));
    }
}
