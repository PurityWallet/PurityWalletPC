package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.sparrow.net.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class PurityConfigTest {
    @AfterEach
    void resetNetwork() {
        Network.set(Network.MAINNET);
    }

    @Test
    void freshDefaultsUseOnlyPurityServices() {
        Network.set(Network.MAINNET);
        Config config = new Config();
        assertEquals("ssl://electrum.bitcoinpurity.org:50002", config.getPublicElectrumServer().getUrl());
        assertEquals("https://mempool.bitcoinpurity.org", config.getBlockExplorer().getUrl());
        assertEquals(1, PublicElectrumServer.getServers().size());
        assertEquals(config.getPublicElectrumServer(), PublicElectrumServer.getServers().getFirst().getServer());
        assertEquals(1, Arrays.stream(BlockExplorer.values()).filter(e -> e != BlockExplorer.NONE).count());
    }

    @Test
    void savedExternalPreferencesCannotEnableRequests() {
        Config config = Config.getGson().fromJson("""
                {"feeRatesSource":"MEMPOOL_SPACE","exchangeSource":"COINBASE","checkNewVersions":true}
                """, Config.class);
        assertEquals(FeeRatesSource.ELECTRUM_SERVER, config.getFeeRatesSource());
        assertEquals(ExchangeSource.NONE, config.getExchangeSource());
        assertFalse(config.isFetchRates());
        assertFalse(config.isCheckNewVersions());
    }

    @Test
    void oldPublicPeerIsReplacedWithoutTouchingExplicitPrivateConnection() {
        Config config = Config.getGson().fromJson("""
                {"serverType":"ELECTRUM_SERVER",
                 "publicElectrumServer":"ssl://electrum.blockstream.info:50002",
                 "electrumServer":"ssl://my.private.node:50002",
                 "blockExplorer":"https://my.explorer/tx/{0}"}
                """, Config.class);
        assertEquals("electrum.bitcoinpurity.org", config.getPublicElectrumServer().getHost());
        assertEquals("ssl://my.private.node:50002", config.getServer().getUrl());
        assertEquals("https://my.explorer/tx/{0}", config.getBlockExplorer().getUrl());
    }

    @Test
    void savedCoreConnectionAndNotificationPreferenceSurvive() {
        Config config = Config.getGson().fromJson("""
                {"serverType":"BITCOIN_CORE","coreServer":"http://localhost:8332",
                 "notifyNewTransactions":false}
                """, Config.class);
        assertEquals("http://localhost:8332", config.getServer().getUrl());
        assertFalse(config.isNotifyNewTransactions());
    }

    @Test
    void nonMainnetDoesNotOfferBitcoinPublicPeers() {
        for(Network network : new Network[]{Network.TESTNET, Network.TESTNET4, Network.SIGNET, Network.REGTEST}) {
            Network.set(network);
            assertTrue(PublicElectrumServer.getServers().isEmpty());
            assertNull(new Config().getPublicElectrumServer());
        }
    }
}
