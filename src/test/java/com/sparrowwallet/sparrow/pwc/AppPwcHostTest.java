package com.sparrowwallet.sparrow.pwc;

import com.github.arteam.simplejsonrpc.client.exception.JsonRpcException;
import com.github.arteam.simplejsonrpc.core.domain.ErrorMessage;
import com.sparrowwallet.sparrow.net.ElectrumServerRpcException;
import com.sparrowwallet.sparrow.net.ServerException;
import org.bitcoinpurity.pwc.provider.ErrorCode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppPwcHostTest {
    @Test
    void broadcastFailuresSeparateRefusalsFromAnUnreachableServer() {
        JsonRpcException refusal = new JsonRpcException(new ErrorMessage(1, "min relay fee not met", null));
        assertEquals(ErrorCode.TRANSACTION_REJECTED, AppPwcHost.broadcastFailure(new ServerException("min relay fee not met",
                new ElectrumServerRpcException("min relay fee not met", refusal))).code());
        assertEquals(ErrorCode.TRANSACTION_REJECTED, AppPwcHost.broadcastFailure(new ServerException("Received txid was different (ab)")).code());

        assertEquals(ErrorCode.NETWORK_UNAVAILABLE, AppPwcHost.broadcastFailure(new ServerException("Read timed out",
                new ElectrumServerRpcException("Read timed out", new IllegalStateException(new SocketTimeoutException("Read timed out"))))).code());
        assertEquals(ErrorCode.NETWORK_UNAVAILABLE, AppPwcHost.broadcastFailure(new ServerException(new ElectrumServerRpcException("Connection closed", new IOException("Connection closed")))).code());
        assertEquals(ErrorCode.NETWORK_UNAVAILABLE, AppPwcHost.broadcastFailure(new IllegalStateException("No transport")).code());
    }
}
