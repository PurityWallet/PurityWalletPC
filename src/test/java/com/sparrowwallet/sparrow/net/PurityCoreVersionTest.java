package com.sparrowwallet.sparrow.net;

import com.sparrowwallet.sparrow.net.cormorant.bitcoind.NetworkInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PurityCoreVersionTest {
    @Test
    void acceptsPurityVersionNumbering() {
        assertTrue(new NetworkInfo(10000, "/Satoshi:29.0/Purity:1.0.0/", true).supportsPurity());
        assertTrue(new NetworkInfo(10100, "/Satoshi:29.0/Purity:1.1.0/", true).supportsPurity());
    }

    @Test
    void rejectsBitcoinNodeAndPreReleasePurity() {
        assertFalse(new NetworkInfo(290000, "/Satoshi:29.0/", true).supportsPurity());
        assertFalse(new NetworkInfo(9999, "/Satoshi:29.0/Purity:0.99.99/", true).supportsPurity());
    }
}
