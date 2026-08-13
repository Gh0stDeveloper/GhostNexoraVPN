package com.ghostnexora.vpn.tunnel;

import android.net.Network;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;

/**
 * One-shot qualification of the exact Android VPN network created by the
 * service. The socket is explicitly bound to TRANSPORT_VPN and deliberately
 * not protected, so its bytes must cross TUN -> Xray -> selected outbound.
 */
public final class AndroidVpnDataPlaneProbe {
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int IO_TIMEOUT_MS = 8_000;
    private static final int MAX_STATUS_LINE_BYTES = 512;
    private static final byte[] TARGET_ADDRESS = new byte[]{1, 1, 1, 1};
    private static final int TARGET_PORT = 443;
    private static final String TARGET_HOST = "one.one.one.one";
    private static final String ENDPOINT = "https://one.one.one.one/";

    private AndroidVpnDataPlaneProbe() {
    }

    public static OutboundCheck verify(Network vpnNetwork) throws Exception {
        if (vpnNetwork == null) {
            throw new IllegalArgumentException("La red VPN de Android no está disponible");
        }
        long startedAt = System.nanoTime();
        Socket transport = new Socket();
        try {
            transport.setTcpNoDelay(true);
            transport.setSoTimeout(IO_TIMEOUT_MS);
            vpnNetwork.bindSocket(transport);
            transport.connect(
                    new InetSocketAddress(InetAddress.getByAddress(TARGET_ADDRESS), TARGET_PORT),
                    CONNECT_TIMEOUT_MS
            );

            SSLSocket tlsSocket = (SSLSocket) SSLContext.getDefault()
                    .getSocketFactory()
                    .createSocket(transport, TARGET_HOST, TARGET_PORT, true);
            tlsSocket.setUseClientMode(true);
            tlsSocket.setSoTimeout(IO_TIMEOUT_MS);
            SSLParameters parameters = tlsSocket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            parameters.setServerNames(Collections.singletonList(new SNIHostName(TARGET_HOST)));
            tlsSocket.setSSLParameters(parameters);

            try (SSLSocket managed = tlsSocket) {
                managed.startHandshake();
                sendSingleRequest(managed.getOutputStream());
                int statusCode = readHttpStatus(managed.getInputStream());
                long latencyMs = Math.max(
                        1L,
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
                );
                return new OutboundCheck(latencyMs, ENDPOINT, statusCode);
            }
        } finally {
            try {
                transport.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static void sendSingleRequest(OutputStream output) throws IOException {
        String request = "HEAD / HTTP/1.1\r\n" +
                "Host: " + TARGET_HOST + "\r\n" +
                "Connection: close\r\n\r\n";
        output.write(request.getBytes(StandardCharsets.US_ASCII));
        output.flush();
    }

    static int readHttpStatus(InputStream rawInput) throws IOException {
        BufferedInputStream input = rawInput instanceof BufferedInputStream
                ? (BufferedInputStream) rawInput
                : new BufferedInputStream(rawInput);
        StringBuilder line = new StringBuilder(64);
        while (line.length() < MAX_STATUS_LINE_BYTES) {
            int value = input.read();
            if (value < 0) {
                break;
            }
            if (value == '\n') {
                break;
            }
            if (value != '\r') {
                line.append((char) value);
            }
        }
        String statusLine = line.toString().trim();
        if (!statusLine.startsWith("HTTP/")) {
            throw new IOException("El destino no devolvió una respuesta HTTP válida");
        }
        String[] parts = statusLine.split("\\s+", 3);
        if (parts.length < 2) {
            throw new IOException("La respuesta HTTP no contiene código de estado");
        }
        int statusCode;
        try {
            statusCode = Integer.parseInt(parts[1]);
        } catch (NumberFormatException error) {
            throw new IOException("El código de estado HTTP no es válido", error);
        }
        if (statusCode < 100 || statusCode > 599) {
            throw new IOException("El código de estado HTTP está fuera de rango");
        }
        return statusCode;
    }
}
