package com.ghostnexora.vpn.tunnel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.os.ParcelFileDescriptor;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public final class XrayActiveOutboundTest {
    @Test
    public void runningCoreReturnsHttpThroughItsConfiguredOutbound() throws Exception {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        var worker = Executors.newSingleThreadExecutor();
        var engine = new XrayCoreEngine(InstrumentationRegistry.getInstrumentation().getTargetContext());
        try (ServerSocket server = new ServerSocket(0, 2, loopback)) {
            server.setSoTimeout(14_000);
            var responder = worker.submit(() -> {
                for (int attempt = 0; attempt < 2; attempt++) {
                    try (Socket client = server.accept()) {
                        client.setSoTimeout(5_000);
                        var request = new BufferedReader(new InputStreamReader(
                                client.getInputStream(), StandardCharsets.US_ASCII));
                        assertEquals("GET /generate_204 HTTP/1.1", request.readLine());
                        String header;
                        while ((header = request.readLine()) != null && !header.isEmpty()) {
                            // Consume the request headers before sending the response.
                        }
                        client.getOutputStream().write(("HTTP/1.1 204 No Content\r\n" +
                                "Connection: close\r\nContent-Length: 0\r\n\r\n")
                                .getBytes(StandardCharsets.US_ASCII));
                    }
                }
                return null;
            });
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            try (ParcelFileDescriptor read = pipe[0]; ParcelFileDescriptor write = pipe[1]) {
                // This fixture exercises the native dispatcher without an Android TUN.
                engine.start("{\"log\":{\"loglevel\":\"warning\"},\"stats\":{}," +
                        "\"outbounds\":[{\"tag\":\"proxy\",\"protocol\":\"freedom\"}]}", read.getFd());
                String endpoint = "http://127.0.0.1:" + server.getLocalPort() + "/generate_204";
                OutboundCheck result = engine.verifyActiveOutbound(endpoint);
                assertEquals(endpoint, result.getEndpoint());
                assertTrue(result.getLatencyMs() > 0);
                responder.get(2, TimeUnit.SECONDS);
            } finally {
                engine.stop();
            }
        } finally {
            worker.shutdownNow();
        }
    }
}
