package com.sparrowwallet.sparrow.pwc;

import org.bitcoinpurity.pwc.provider.PeerInfo;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PwcTextTest {
    @Test
    void amountsAreExactWithEightDecimals() {
        assertEquals("0.00050000 BTC (50,000 sat)", PwcText.sats(50_000));
        assertEquals("21.00000001 BTC (2,100,000,001 sat)", PwcText.sats(2_100_000_001L));
        assertEquals("0.00000000 BTC (0 sat)", PwcText.sats(0));
    }

    @Test
    void addressesAreGroupedInFours() {
        assertEquals("bcrt 1qxy 2kgd ygjr", PwcText.address("bcrt1qxy2kgdygjr"));
        assertEquals("abc", PwcText.address("abc"));
    }

    @Test
    void feeRatesConvertFromSatPerKvb() {
        assertEquals("1.25 sat/vB", PwcText.feeRate(1250));
        assertEquals("2 sat/vB", PwcText.feeRate(2000));
        assertEquals("1.001 sat/vB", PwcText.feeRate(1001));
    }

    @Test
    void invisibleAndConfusableCharactersAreNamed() {
        assertEquals(List.of(), PwcText.hiddenCharacters("Plain text\nwith\ttabs and spaces"));
        assertEquals(List.of("U+200B", "U+202E", "U+00A0"), PwcText.hiddenCharacters("a​b‮c d​"));
    }

    @Test
    void requestersAreNamedByTheOperatingSystem() {
        Path executable = Path.of("/usr/bin/trader");
        assertEquals(executable + " (process 42)", PwcText.application(new PeerInfo(42, executable)));
        assertEquals("Unknown application (process 42)", PwcText.application(new PeerInfo(42, null)));
        assertEquals("Unknown application", PwcText.application(new PeerInfo(null, null)));
    }
}
