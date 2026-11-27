package com.sparrowwallet.sparrow.pwc;

import org.bitcoinpurity.pwc.provider.PeerInfo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Text shown in PWC prompts (PWC-UX-001 §2, §4, §5). */
final class PwcText {
    private PwcText() {}

    static String sats(long sats) {
        return BigDecimal.valueOf(sats, 8).toPlainString() + " BTC (" + String.format(Locale.ROOT, "%,d", sats) + " sat)";
    }

    static String address(String address) {
        StringBuilder grouped = new StringBuilder();
        for(int i = 0; i < address.length(); i += 4) {
            if(i > 0) {
                grouped.append(' ');
            }
            grouped.append(address, i, Math.min(i + 4, address.length()));
        }
        return grouped.toString();
    }

    static String feeRate(long satPerKvb) {
        BigDecimal rate = BigDecimal.valueOf(satPerKvb, 3).stripTrailingZeros();
        return (rate.scale() < 0 ? rate.setScale(0) : rate).toPlainString() + " sat/vB";
    }

    /** Characters that are invisible or easily confused with a plain space, as U+XXXX, in order of first appearance. */
    static List<String> hiddenCharacters(String text) {
        List<String> hidden = new ArrayList<>();
        text.codePoints().forEach(c -> {
            boolean ordinary = c == '\n' || c == '\r' || c == '\t' || c == ' ';
            boolean suspicious = Character.getType(c) == Character.FORMAT || Character.isISOControl(c) || Character.isSpaceChar(c);
            String name = String.format(Locale.ROOT, "U+%04X", c);
            if(!ordinary && suspicious && !hidden.contains(name)) {
                hidden.add(name);
            }
        });
        return hidden;
    }

    static String application(PeerInfo peer) {
        String name = peer.executable() == null ? "Unknown application" : peer.executable().toString();
        return peer.pid() == null ? name : name + " (process " + peer.pid() + ")";
    }
}
