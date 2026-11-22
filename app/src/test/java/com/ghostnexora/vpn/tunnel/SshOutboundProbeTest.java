package com.ghostnexora.vpn.tunnel;

import org.junit.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public final class SshOutboundProbeTest {
    @Test
    public void sendsUnresolvedHostnameToSocksAndFailsWhenProxyRejectsIt() throws Exception {
        InetAddress loopback = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (ServerSocket listener = new ServerSocket(0, 1, loopback)) {
            listener.setSoTimeout(5_000);
            Future<String> destination = worker.submit(() -> {
                try (Socket client = listener.accept()) {
                    client.setSoTimeout(5_000);
                    DataInputStream input = new DataInputStream(client.getInputStream());
                    assertEquals(5, input.readUnsignedByte());
                    input.readFully(new byte[input.readUnsignedByte()]);
                    client.getOutputStream().write(new byte[]{5, 0});
                    assertEquals(5, input.readUnsignedByte());
                    assertEquals(1, input.readUnsignedByte());
                    assertEquals(0, input.readUnsignedByte());
                    assertEquals(3, input.readUnsignedByte());
                    byte[] host = new byte[input.readUnsignedByte()];
                    input.readFully(host);
                    assertEquals(443, input.readUnsignedShort());
                    client.getOutputStream().write(new byte[]{5, 5, 0, 1, 0, 0, 0, 0, 0, 0});
                    return new String(host, StandardCharsets.US_ASCII);
                }
            });
            assertThrows(IOException.class, () -> SshOutboundProbe.verifyEndpoint(
                    listener.getLocalPort(), URI.create("https://ghost.invalid/generate_204")));
            assertEquals("ghost.invalid", destination.get(6, TimeUnit.SECONDS));
        } finally {
            worker.shutdownNow();
        }
    }
}
