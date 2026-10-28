package com.sparrowwallet.sparrow.net;

import com.fasterxml.jackson.databind.JsonNode;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.protocol.*;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.tern.http.client.HttpResponseException;
import javafx.concurrent.Service;
import javafx.concurrent.Task;

import java.util.*;
import java.util.concurrent.*;

public class LegacyUtxoChecker {
    public static final String API = "https://mempool.space/api";

    public enum Status { CHECKING, SHARED, ABSENT, UNKNOWN }

    public record Utxo(String txid, long index, long value, String script) {
        public static Utxo from(BlockTransactionHashIndex output, WalletNode node) {
            return new Utxo(output.getHash().toString(), output.getIndex(), output.getValue(), Utils.bytesToHex(node.getOutputScript().getProgram()));
        }
    }

    @FunctionalInterface
    public interface JsonRequest {
        JsonNode get(String path) throws Exception;
    }

    private final JsonRequest request;

    public LegacyUtxoChecker(JsonRequest request) {
        this.request = request;
    }

    public Map<Utxo, Status> check(List<Utxo> inputs) throws InterruptedException {
        Map<Utxo, Status> results = new ConcurrentHashMap<>();
        Map<String, List<Utxo>> groups = new LinkedHashMap<>();
        for(Utxo input : inputs) {
            results.put(input, Status.UNKNOWN);
            if(input.txid() != null && input.txid().matches("[a-fA-F0-9]{64}") && input.index() >= 0 && input.index() <= Integer.MAX_VALUE) {
                groups.computeIfAbsent(input.txid(), _ -> new ArrayList<>()).add(input);
            }
        }
        try(ExecutorService executor = Executors.newFixedThreadPool(4)) {
            List<Callable<Void>> tasks = groups.entrySet().stream().<Callable<Void>>map(group -> () -> {
                checkTransaction(group.getKey(), group.getValue(), results);
                return null;
            }).toList();
            executor.invokeAll(tasks);
        }
        return Map.copyOf(results);
    }

    private void checkTransaction(String txid, List<Utxo> coins, Map<Utxo, Status> results) {
        try {
            JsonNode tx;
            try {
                tx = request.get("/tx/" + txid);
            } catch(HttpResponseException e) {
                if(e.getStatusCode() == 404) {
                    coins.forEach(coin -> results.put(coin, Status.ABSENT));
                    return;
                }
                throw e;
            }
            if(tx == null || !txid.equals(tx.path("txid").asText()) || !tx.path("vout").isArray()) {
                return;
            }
            JsonNode outspends = request.get("/tx/" + txid + "/outspends");
            if(outspends == null || !outspends.isArray()) {
                return;
            }
            for(Utxo coin : coins) {
                JsonNode output = tx.path("vout").path((int)coin.index());
                JsonNode spent = outspends.path((int)coin.index()).path("spent");
                JsonNode value = output.path("value");
                JsonNode script = output.path("scriptpubkey");
                if(!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0 || !script.isTextual()
                        || !script.textValue().matches("(?i)([a-f0-9]{2})*") || !spent.isBoolean() || coin.value() < 0 || coin.script() == null) {
                    continue;
                }
                results.put(coin, !spent.booleanValue() && value.longValue() == coin.value() && script.textValue().equalsIgnoreCase(coin.script()) ? Status.SHARED : Status.ABSENT);
            }
        } catch(Exception e) {
            // Unavailable or malformed responses must never imply replay protection.
            if(e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static Status summarize(List<Utxo> inputs, Map<Utxo, Status> results) {
        if(inputs.stream().anyMatch(input -> results.getOrDefault(input, Status.UNKNOWN) == Status.UNKNOWN || results.get(input) == Status.CHECKING)) {
            return Status.UNKNOWN;
        }
        return !inputs.isEmpty() && inputs.stream().allMatch(input -> results.get(input) == Status.SHARED) ? Status.SHARED : Status.ABSENT;
    }

    public static List<Utxo> inputs(Transaction transaction, PSBT psbt, Wallet wallet, Map<Sha256Hash, BlockTransaction> inputTransactions) {
        List<Utxo> inputs = new ArrayList<>();
        for(int i = 0; i < transaction.getInputs().size(); i++) {
            TransactionOutPoint outpoint = transaction.getInputs().get(i).getOutpoint();
            TransactionOutput output = psbt == null ? null : psbt.getPsbtInputs().get(i).getUtxo();
            if(output == null) {
                BlockTransaction previous = wallet == null ? null : wallet.getWalletTransaction(outpoint.getHash());
                if(previous == null && inputTransactions != null) {
                    previous = inputTransactions.get(outpoint.getHash());
                }
                if(previous != null && previous.getTransaction() != null && outpoint.getIndex() < previous.getTransaction().getOutputs().size()) {
                    output = previous.getTransaction().getOutputs().get((int)outpoint.getIndex());
                }
            }
            inputs.add(new Utxo(outpoint.getHash().toString(), outpoint.getIndex(), output == null ? -1 : output.getValue(),
                    output == null ? null : Utils.bytesToHex(output.getScriptBytes())));
        }
        return List.copyOf(inputs);
    }

    public static class CheckService extends Service<Map<Utxo, Status>> {
        private final List<Utxo> inputs;

        public CheckService(List<Utxo> inputs) {
            this.inputs = List.copyOf(inputs);
        }

        @Override
        protected Task<Map<Utxo, Status>> createTask() {
            boolean online = AppServices.onlineProperty().get();
            var proxy = AppServices.getTorProxy();
            return new Task<>() {
                @Override
                protected Map<Utxo, Status> call() throws Exception {
                    if(Network.get() != Network.MAINNET || !online) {
                        Map<Utxo, Status> results = new HashMap<>();
                        inputs.forEach(input -> results.put(input, Status.UNKNOWN));
                        return results;
                    }
                    var client = new com.sparrowwallet.tern.http.client.HttpClientService(10_000, new TorHttpProxySupplier(proxy));
                    try {
                        var http = client.getHttpClient(com.sparrowwallet.tern.http.client.HttpUsage.DEFAULT);
                        http.connect();
                        return new LegacyUtxoChecker(path -> http.getJson(API + path, JsonNode.class, Map.of("Cache-Control", "no-cache"))).check(inputs);
                    } finally {
                        client.stop();
                    }
                }
            };
        }
    }
}
