package com.ghostnexora.vpn.tunnel;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;

/** Active loopback SOCKS -> Xray -> SSH -> TLS connectivity probe. */
final class Socks5OutboundProbe {
    private static final int CONNECT_TIMEOUT_MS = 4_000;
    private static final int IO_TIMEOUT_MS = 12_000;
    private static final InetAddress IPV4_LOOPBACK;

    static final List<SocksProbeTarget> targets = Collections.unmodifiableList(Arrays.asList(
            new SocksProbeTarget(new byte[]{1, 1, 1, 1}, 443, "one.one.one.one"),
            new SocksProbeTarget(new byte[]{8, 8, 8, 8}, 443, "dns.google")
    ));

    static {
        try {
            IPV4_LOOPBACK = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        } catch (IOException impossible) {
            throw new ExceptionInInitializerError(impossible);
        }
    }

    private Socks5OutboundProbe() {
    }

    static SocksProbeResult measure(int localPort, SocksProbeTarget target) throws Exception {
        if (localPort < 1 || localPort > 65_535) {
            throw new IllegalArgumentException("Invalid Xray health-check port");
        }
        long startedAt = System.nanoTime();
        Socket socket = new Socket();
        try {
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(IO_TIMEOUT_MS);
            socket.connect(new InetSocketAddress(IPV4_LOOPBACK, localPort), CONNECT_TIMEOUT_MS);

            InputStream input = socket.getInputStream();
            OutputStream output = socket.getOutputStream();
            negotiateNoAuthentication(input, output);
            requestIpv4Connect(input, output, target.getAddress(), target.getPort());

            SSLSocket tlsSocket = (SSLSocket) SSLContext.getDefault().getSocketFactory().createSocket(
                    socket,
                    target.getServerName(),
                    target.getPort(),
                    true
            );
            tlsSocket.setUseClientMode(true);
            tlsSocket.setSoTimeout(IO_TIMEOUT_MS);
            SSLParameters parameters = tlsSocket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            parameters.setServerNames(Collections.singletonList(new SNIHostName(target.getServerName())));
            tlsSocket.setSSLParameters(parameters);
            try (SSLSocket managed = tlsSocket) {
                managed.startHandshake();
                long latencyMs = Math.max(
                        1L,
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
                );
                return new SocksProbeResult(
                        latencyMs,
                        managed.getSession().getProtocol() != null ? managed.getSession().getProtocol() : "",
                        managed.getSession().getCipherSuite() != null ? managed.getSession().getCipherSuite() : ""
                );
            }
        } finally {
            try {
                socket.close();
            } catch (Throwable ignored) {
            }
        }
    }

    static void negotiateNoAuthentication(InputStream input, OutputStream output) throws IOException {
        output.write(new byte[]{0x05, 0x01, 0x00});
        output.flush();

        byte[] response = new byte[2];
        readFully(input, response);
        if ((response[0] & 0xFF) != 0x05) {
            throw new IOException("Xray health-check returned an invalid SOCKS version");
        }
        if ((response[1] & 0xFF) != 0x00) {
            throw new IOException("Xray health-check rejected SOCKS no-authentication mode");
        }
    }

    static void requestIpv4Connect(
            InputStream input,
            OutputStream output,
            byte[] address,
            int port
    ) throws IOException {
        output.write(buildIpv4ConnectRequest(address, port));
        output.flush();
        readConnectReply(input);
    }

    static byte[] buildIpv4ConnectRequest(byte[] address, int port) {
        if (address == null || address.length != 4) {
            throw new IllegalArgumentException("SOCKS IPv4 address must contain four bytes");
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("SOCKS target port is invalid");
        }
        return new byte[]{
                0x05,
                0x01,
                0x00,
                0x01,
                address[0],
                address[1],
                address[2],
                address[3],
                (byte) (port >>> 8),
                (byte) port
        };
    }

    static void readConnectReply(InputStream input) throws IOException {
        byte[] header = new byte[4];
        readFully(input, header);
        if ((header[0] & 0xFF) != 0x05) {
            throw new IOException("Xray health-check returned an invalid SOCKS reply");
        }
        int replyCode = header[1] & 0xFF;
        if (replyCode != 0x00) {
            throw new IOException("Xray SOCKS outbound rejected the probe (" + replyLabel(replyCode) + ")");
        }

        int addressLength;
        switch (header[3] & 0xFF) {
            case 0x01:
                addressLength = 4;
                break;
            case 0x03:
                addressLength = input.read();
                if (addressLength < 0) {
                    throw new IOException("Xray health-check returned a truncated domain reply");
                }
                break;
            case 0x04:
                addressLength = 16;
                break;
            default:
                throw new IOException("Xray health-check returned an invalid address type");
        }
        readFully(input, new byte[addressLength + 2]);
    }

    private static void readFully(InputStream input, byte[] destination) throws IOException {
        int offset = 0;
        while (offset < destination.length) {
            int read = input.read(destination, offset, destination.length - offset);
            if (read < 0) {
                throw new IOException("Xray health-check SOCKS connection closed early");
            }
            offset += read;
        }
    }

    private static String replyLabel(int code) {
        switch (code) {
            case 0x01: return "general failure";
            case 0x02: return "connection not allowed";
            case 0x03: return "network unreachable";
            case 0x04: return "host unreachable";
            case 0x05: return "connection refused";
            case 0x06: return "TTL expired";
            case 0x07: return "command not supported";
            case 0x08: return "address type not supported";
            default: return "code " + code;
        }
    }
}

final class SocksProbeResult {
    private final long latencyMs;
    private final String tlsProtocol;
    private final String cipherSuite;

    SocksProbeResult(long latencyMs, String tlsProtocol, String cipherSuite) {
        this.latencyMs = latencyMs;
        this.tlsProtocol = tlsProtocol;
        this.cipherSuite = cipherSuite;
    }

    public long getLatencyMs() { return latencyMs; }
    public String getTlsProtocol() { return tlsProtocol; }
    public String getCipherSuite() { return cipherSuite; }
}

final class SocksProbeTarget {
    private final byte[] address;
    private final int port;
    private final String serverName;

    SocksProbeTarget(byte[] address, int port, String serverName) {
        this.address = address.clone();
        this.port = port;
        this.serverName = serverName;
    }

    public byte[] getAddress() { return address.clone(); }
    public int getPort() { return port; }
    public String getServerName() { return serverName; }
    public String getEndpoint() { return "https://" + serverName + ":" + port; }
}
