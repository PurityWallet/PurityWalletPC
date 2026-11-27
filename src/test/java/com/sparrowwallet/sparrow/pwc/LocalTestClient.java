package com.sparrowwallet.sparrow.pwc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparrowwallet.drongo.Utils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A minimal PWC LOCAL client (PWC-TRANSPORT-LOCAL-001 §4, §5): one request at a time, events ignored. */
final class LocalTestClient implements Closeable {
    record Credentials(String clientId, byte[] secret) {}

    static final class RpcError extends Exception {
        final String code;

        RpcError(String code, String message) {
            super(code + ": " + message);
            this.code = code;
        }
    }

    private final ObjectMapper json = new ObjectMapper();
    private final Closeable connection;
    private final BufferedReader in;
    private final Writer out;
    private int nextId = 1;
    private byte[] serverNonce;

    private LocalTestClient(Closeable connection, InputStream in, OutputStream out) {
        this.connection = connection;
        this.in = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        this.out = new OutputStreamWriter(out, StandardCharsets.UTF_8);
    }

    static LocalTestClient connect(Path endpoint) throws IOException {
        if(endpoint.toString().startsWith("\\\\.\\pipe\\")) {
            RandomAccessFile pipe = new RandomAccessFile(endpoint.toString(), "rw");
            return new LocalTestClient(pipe, Channels.newInputStream(pipe.getChannel()), Channels.newOutputStream(pipe.getChannel()));
        }
        SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
        channel.connect(UnixDomainSocketAddress.of(endpoint));
        return new LocalTestClient(channel, Channels.newInputStream(channel), Channels.newOutputStream(channel));
    }

    JsonNode call(String method, Map<String, Object> params) throws IOException, RpcError {
        int id = nextId++;
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        request.put("params", params);
        out.write(json.writeValueAsString(request) + "\n");
        out.flush();
        while(true) {
            String line = in.readLine();
            if(line == null) {
                throw new EOFException("The provider closed the connection");
            }
            JsonNode frame = json.readTree(line);
            if(frame.path("id").asInt(-1) != id) {
                continue;
            }
            if(frame.has("error")) {
                JsonNode error = frame.get("error");
                JsonNode data = error.path("data");
                throw new RpcError(data.path("code").asText(String.valueOf(error.path("code").asInt())), error.path("message").asText());
            }
            return frame.get("result");
        }
    }

    void hello() throws IOException, RpcError {
        JsonNode result = call("local.hello", Map.of("protocols", List.of("pwc-local/1")));
        serverNonce = Utils.hexToBytes(result.get("server_nonce").asText());
    }

    Credentials pair(String name, String code) throws IOException, RpcError {
        JsonNode result = call("local.pair", Map.of("client_name", name, "pairing_code", code));
        return new Credentials(result.get("client_id").asText(), Utils.hexToBytes(result.get("client_secret").asText()));
    }

    void authenticate(Credentials credentials) throws Exception {
        byte[] clientNonce = new byte[32];
        new SecureRandom().nextBytes(clientNonce);
        byte[] id = credentials.clientId().getBytes(StandardCharsets.UTF_8);
        byte[] proof = hmac(credentials.secret(), "PWC-LOCAL-1 client", serverNonce, clientNonce, id);
        JsonNode result = call("local.authenticate", Map.of("client_id", credentials.clientId(), "client_nonce", Utils.bytesToHex(clientNonce),
                "client_proof", Utils.bytesToHex(proof)));
        byte[] expected = hmac(credentials.secret(), "PWC-LOCAL-1 provider", clientNonce, serverNonce, id);
        if(!MessageDigest.isEqual(expected, Utils.hexToBytes(result.get("server_proof").asText()))) {
            throw new IOException("The provider's proof does not match");
        }
    }

    private static byte[] hmac(byte[] key, String label, byte[] first, byte[] second, byte[] id) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        mac.update(label.getBytes(StandardCharsets.US_ASCII));
        mac.update((byte)0);
        mac.update(first);
        mac.update(second);
        return mac.doFinal(id);
    }

    /** Polls an Operation until it reaches a terminal state. */
    JsonNode await(JsonNode operation) throws Exception {
        for(int i = 0; i < 100 && List.of("queued", "awaiting_approval", "awaiting_unlock", "awaiting_signer", "processing").contains(operation.get("state").asText()); i++) {
            Thread.sleep(100);
            operation = call("operation.get", Map.of("operation_id", operation.get("operation_id").asText()));
        }
        return operation;
    }

    @Override
    public void close() throws IOException {
        connection.close();
    }
}
