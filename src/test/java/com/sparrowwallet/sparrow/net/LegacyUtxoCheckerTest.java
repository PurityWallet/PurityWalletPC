package com.sparrowwallet.sparrow.net;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparrowwallet.tern.http.client.HttpResponseException;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.sparrowwallet.sparrow.net.LegacyUtxoChecker.Status.*;
import static org.junit.jupiter.api.Assertions.*;

class LegacyUtxoCheckerTest {
    private static final String TXID = "ab".repeat(32);
    private static final LegacyUtxoChecker.Utxo COIN = new LegacyUtxoChecker.Utxo(TXID, 0, 1234, "0014abcd");
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void requiresMatchingUnspentOutputAndGroupsTransactionRequests() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var checker = new LegacyUtxoChecker(path -> {
            calls.incrementAndGet();
            return json(path.endsWith("outspends") ? "[{\"spent\":false},{\"spent\":true}]" : transaction(TXID, 1234, "0014abcd"));
        });
        var spent = new LegacyUtxoChecker.Utxo(TXID, 1, 1234, "0014abcd");
        var result = checker.check(List.of(COIN, spent));
        assertEquals(SHARED, result.get(COIN));
        assertEquals(ABSENT, result.get(spent));
        assertEquals(2, calls.get());
    }

    @Test
    void valueScriptAndSpentDifferencesAreAbsent() throws Exception {
        for(String tx : List.of(transaction(TXID, 1235, "0014abcd"), transaction(TXID, 1234, "0014ffff"))) {
            assertEquals(ABSENT, check(tx, "[{\"spent\":false}]"));
        }
        assertEquals(ABSENT, check(transaction(TXID, 1234, "0014abcd"), "[{\"spent\":true}]"));
    }

    @Test
    void onlyTransaction404MeansAbsent() throws Exception {
        assertEquals(ABSENT, new LegacyUtxoChecker(path -> { throw new HttpResponseException("missing", 404); }).check(List.of(COIN)).get(COIN));
        assertEquals(UNKNOWN, new LegacyUtxoChecker(path -> {
            if(path.endsWith("outspends")) throw new HttpResponseException("missing", 404);
            return json(transaction(TXID, 1234, "0014abcd"));
        }).check(List.of(COIN)).get(COIN));
        for(int code : List.of(429, 500, 503)) {
            assertEquals(UNKNOWN, new LegacyUtxoChecker(path -> { throw new HttpResponseException("unavailable", code); }).check(List.of(COIN)).get(COIN));
        }
        assertEquals(UNKNOWN, new LegacyUtxoChecker(path -> { throw new java.net.SocketTimeoutException(); }).check(List.of(COIN)).get(COIN));
    }

    @Test
    void malformedOrIncompleteDataRemainsUnknown() throws Exception {
        for(String tx : List.of("{}", transaction("cd".repeat(32), 1234, "0014abcd"),
                "{\"txid\":\"" + TXID + "\",\"vout\":[{\"value\":1234.5,\"scriptpubkey\":\"0014abcd\"}]}",
                "{\"txid\":\"" + TXID + "\",\"vout\":[]}")) {
            assertEquals(UNKNOWN, check(tx, "[{\"spent\":false}]"));
        }
        for(String spent : List.of("{}", "[]", "[{}]", "[{\"spent\":\"false\"}]")) {
            assertEquals(UNKNOWN, check(transaction(TXID, 1234, "0014abcd"), spent));
        }
        var missing = new LegacyUtxoChecker.Utxo(TXID, 0, -1, null);
        assertEquals(UNKNOWN, new LegacyUtxoChecker(path -> json(path.endsWith("outspends") ? "[{\"spent\":false}]" : transaction(TXID, 1234, "0014abcd"))).check(List.of(missing)).get(missing));
    }

    @Test
    void replayWarningRequiresAllInputsAndNeverTreatsUnknownAsSafe() {
        var other = new LegacyUtxoChecker.Utxo("cd".repeat(32), 0, 1234, "0014abcd");
        assertEquals(SHARED, LegacyUtxoChecker.summarize(List.of(COIN, other), Map.of(COIN, SHARED, other, SHARED)));
        assertEquals(ABSENT, LegacyUtxoChecker.summarize(List.of(COIN, other), Map.of(COIN, SHARED, other, ABSENT)));
        assertEquals(UNKNOWN, LegacyUtxoChecker.summarize(List.of(COIN, other), Map.of(COIN, SHARED)));
        assertEquals(UNKNOWN, LegacyUtxoChecker.summarize(List.of(COIN, other), Map.of(COIN, ABSENT, other, UNKNOWN)));
        assertEquals(ABSENT, LegacyUtxoChecker.summarize(List.of(), Map.of()));
    }

    @Test
    void inputMetadataComesFromExactPsbtOutpointsAndMissingDataStaysUnknown() {
        var tx = new com.sparrowwallet.drongo.protocol.Transaction();
        tx.addInput(com.sparrowwallet.drongo.protocol.Sha256Hash.wrap(TXID), 1, new com.sparrowwallet.drongo.protocol.Script(new byte[0]));
        tx.addInput(com.sparrowwallet.drongo.protocol.Sha256Hash.wrap("cd".repeat(32)), 0, new com.sparrowwallet.drongo.protocol.Script(new byte[0]));
        tx.addOutput(1000, new com.sparrowwallet.drongo.protocol.Script(new byte[0]));
        var psbt = new com.sparrowwallet.drongo.psbt.PSBT(tx);
        psbt.getPsbtInputs().getFirst().setWitnessUtxo(new com.sparrowwallet.drongo.protocol.TransactionOutput(null, 1234, new com.sparrowwallet.drongo.protocol.Script(new byte[]{0x51})));
        var inputs = LegacyUtxoChecker.inputs(tx, psbt, null, null);
        assertEquals(new LegacyUtxoChecker.Utxo(TXID, 1, 1234, "51"), inputs.getFirst());
        assertEquals(new LegacyUtxoChecker.Utxo("cd".repeat(32), 0, -1, null), inputs.getLast());
        var previous = new com.sparrowwallet.drongo.protocol.Transaction();
        previous.addOutput(1234, new com.sparrowwallet.drongo.protocol.Script(new byte[]{0x51}));
        var hash = com.sparrowwallet.drongo.protocol.Sha256Hash.wrap("cd".repeat(32));
        var history = Map.of(hash, new com.sparrowwallet.drongo.wallet.BlockTransaction(hash, 970000, new Date(), 0L, previous));
        assertEquals(new LegacyUtxoChecker.Utxo(hash.toString(), 0, 1234, "51"), LegacyUtxoChecker.inputs(tx, null, null, history).getLast());
    }

    @org.junit.jupiter.api.Tag("checkpoint")
    @Test
    void liveLegacyApiRecognizesMissingTransaction() throws Exception {
        var client = new com.sparrowwallet.tern.http.client.HttpClientService(10_000);
        try {
            var checker = new LegacyUtxoChecker(path -> client.requestJson(LegacyUtxoChecker.API + path, JsonNode.class, Map.of("Cache-Control", "no-cache")));
            assertEquals(ABSENT, checker.check(List.of(COIN)).get(COIN));
        } finally {
            client.stop();
        }
    }

    private LegacyUtxoChecker.Status check(String tx, String spent) throws Exception {
        return new LegacyUtxoChecker(path -> json(path.endsWith("outspends") ? spent : tx)).check(List.of(COIN)).get(COIN);
    }

    private JsonNode json(String value) throws Exception { return mapper.readTree(value); }

    private String transaction(String txid, long value, String script) {
        String output = "{\"value\":" + value + ",\"scriptpubkey\":\"" + script + "\"}";
        return "{\"txid\":\"" + txid + "\",\"vout\":[" + output + "," + output + "]}";
    }
}
