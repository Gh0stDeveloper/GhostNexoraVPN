package com.ghostnexora.vpn.tunnel;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/** Checks the already authenticated loopback bridge without starting another SSH session. */
final class SshOutboundProbe {
    private static final int IO_TIMEOUT_MS = 4_000;

    private SshOutboundProbe() { }

    static OutboundCheck verify(int socksPort) throws IOException {
        IOException failure = null;
        for (String url : XrayCoreEngine.CONNECTIVITY_TEST_URLS) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("Verificación SSH cancelada");
            try {
                return verifyEndpoint(socksPort, URI.create(url));
            } catch (IOException error) {
                failure = error;
            }
        }
        throw new IOException("Sin respuesta HTTPS válida a través de SSH", failure);
    }

    static OutboundCheck verifyEndpoint(int socksPort, URI endpoint) throws IOException {
        String host = endpoint.getHost();
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", socksPort));
        long start = System.nanoTime();
        try (Socket socket = new Socket(proxy)) {
            socket.setSoTimeout(IO_TIMEOUT_MS);
            // The SOCKS bridge forwards the hostname to SSH without local DNS.
            socket.connect(InetSocketAddress.createUnresolved(host, 443), IO_TIMEOUT_MS);
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            try (SSLSocket tls = (SSLSocket) factory.createSocket(socket, host, 443, true)) {
                SSLParameters parameters = tls.getSSLParameters();
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                tls.setSSLParameters(parameters);
                tls.setSoTimeout(IO_TIMEOUT_MS);
                tls.startHandshake();
                String request = "GET " + endpoint.getRawPath() + " HTTP/1.1\r\nHost: " + host
                        + "\r\nConnection: close\r\n\r\n";
                tls.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
                tls.getOutputStream().flush();
                requireNoContent(new BufferedInputStream(tls.getInputStream()));
                long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                return new OutboundCheck(Math.max(1L, elapsed), endpoint.toString());
            }
        }
    }

    private static void requireNoContent(BufferedInputStream input) throws IOException {
        StringBuilder status = new StringBuilder();
        for (int value; status.length() < 256 && (value = input.read()) != -1; ) {
            if (value == '\n') {
                if (status.toString().matches("HTTP/1\\.[01] 204(?: [^\\r]*)?\\r")) return;
                break;
            }
            status.append((char) value);
        }
        throw new IOException("El destino no devolvió HTTP 204");
    }
}
