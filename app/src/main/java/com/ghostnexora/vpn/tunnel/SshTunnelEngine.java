package com.ghostnexora.vpn.tunnel;

import android.content.Context;

import com.ghostnexora.vpn.data.model.ConnectionMode;
import com.ghostnexora.vpn.data.model.ProxyConfig;
import com.ghostnexora.vpn.data.model.TlsVerificationMode;
import com.ghostnexora.vpn.data.model.VpnProfile;
import com.ghostnexora.vpn.util.PayloadAction;
import com.ghostnexora.vpn.util.PayloadContext;
import com.ghostnexora.vpn.util.PayloadEngine;
import com.ghostnexora.vpn.util.PayloadPlan;
import com.jcraft.jsch.ChannelDirectTCPIP;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SocketFactory;
import com.jcraft.jsch.UserInfo;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;

/** Java SSH engine for direct, TLS/SNI, payload and upstream-proxy VPN modes. */
public final class SshTunnelEngine {
    private static final int MAX_SERVER_MESSAGE_CHARS = 8 * 1024;
    private static final Pattern ANSI_SGR =
            Pattern.compile("\\u001B\\[([0-9;]*)m");
    private static final Pattern ANSI_CSI =
            Pattern.compile("\\u001B\\[[0-?]*[ -/]*[@-~]");
    private static final Pattern ANSI_OSC =
            Pattern.compile("\\u001B\\][^\\u0007]*(?:\\u0007|\\u001B\\\\)");

    private final Context context;
    private final Function1<? super String, Unit> onStatus;
    private final PhysicalNetworkSocketConnector socketConnector;

    public SshTunnelEngine(Context context, Function1<? super String, Unit> onStatus) {
        this.context = context != null ? context.getApplicationContext() : null;
        this.onStatus = onStatus != null ? onStatus : ignored -> Unit.INSTANCE;
        this.socketConnector = new PhysicalNetworkSocketConnector(this.context, this.onStatus);
    }

    public SshTunnelEngine(Context context) {
        this(context, ignored -> Unit.INSTANCE);
    }

    public Session connect(VpnProfile profile) {
        if (profile == null) throw new IllegalArgumentException("profile == null");
        ConnectionMode mode = profile.getSelectedMode();
        if (!mode.isSsh()) throw new IllegalArgumentException("El motor SSH no soporta " + mode.getLabel());

        String transportHost = profile.getHost().trim();
        int transportPort = clampPort(profile.getPort());
        if (transportHost.isEmpty()) throw new IllegalArgumentException("El host del perfil no puede estar vacío");
        if (profile.getUsername().trim().isEmpty()) throw new IllegalArgumentException("El usuario SSH no puede estar vacío");
        if (profile.getPassword().isEmpty()) throw new IllegalArgumentException("La contraseña SSH es obligatoria");

        // A fresh JSch instance has no identities. Avoid removeAllIdentity(), whose Java API
        // declares JSchException even though there is nothing to clear on a new instance.
        JSch jsch = new JSch();
        configureKnownHosts(jsch);

        String password = profile.getPassword();
        try {
            Session session = jsch.getSession(profile.getUsername().trim(), transportHost, transportPort);
            session.setPassword(password);
            ProfileUserInfo userInfo = new ProfileUserInfo(password, onStatus);
            session.setUserInfo(userInfo);
            session.setConfig("StrictHostKeyChecking", "ask");
            session.setConfig("PreferredAuthentications", "password,keyboard-interactive");
            session.setConfig("MaxAuthTries", "3");
            session.setConfig(
                    "server_host_key",
                    "ssh-ed25519,ecdsa-sha2-nistp521,ecdsa-sha2-nistp384,ecdsa-sha2-nistp256,rsa-sha2-512,rsa-sha2-256"
            );
            session.setServerAliveInterval(15_000);
            session.setServerAliveCountMax(3);
            session.setTimeout(25_000);
            session.setSocketFactory(new TunnelSocketFactory(profile, onStatus, socketConnector));
            try {
                status("[SSH] Abriendo sesión y negociando algoritmos");
                session.connect(25_000);
                status("[SSH] Autenticación completada · sesión cifrada activa");
            } catch (JSchException error) {
                String message = error.getMessage() != null ? error.getMessage() : "";
                if (message.toLowerCase(Locale.US).contains("auth fail")) {
                    throw new IllegalStateException(
                            "Autenticación SSH fallida. Verifica usuario, contraseña, puerto y acceso por contraseña.",
                            error
                    );
                }
                throw error;
            }
            return session;
        } catch (JSchException error) {
            throw new IllegalStateException(firstMessage(error, "Fallo inicializando SSH"), error);
        }
    }

    public SshTunnelHandle connectWithSocks(VpnProfile profile) {
        Session session = connect(profile);
        try {
            SshSocksServer socksServer = new SshSocksServer(session, onStatus);
            socksServer.start();
            return new SshTunnelHandle(session, socksServer);
        } catch (Throwable error) {
            try { session.disconnect(); } catch (Throwable ignored) { }
            throw runtime(error);
        }
    }

    public void disconnect(Session session) {
        try {
            if (session != null) session.disconnect();
        } catch (Throwable ignored) { }
    }

    private void configureKnownHosts(JSch jsch) {
        if (context == null) return;
        java.io.File knownHosts = new java.io.File(context.getFilesDir(), "ssh_known_hosts");
        try {
            if (!knownHosts.exists() && !knownHosts.createNewFile()) return;
            jsch.setKnownHosts(knownHosts.getAbsolutePath());
        } catch (Throwable ignored) { }
    }

    private void status(String message) {
        onStatus.invoke(message);
    }

    static String normalizeServerMessage(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        String withHtmlColors = ansiSgrToHtml(raw);
        String withoutAnsi = ANSI_OSC.matcher(ANSI_CSI.matcher(withHtmlColors).replaceAll(""))
                .replaceAll("")
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        StringBuilder clean = new StringBuilder(
                Math.min(withoutAnsi.length(), MAX_SERVER_MESSAGE_CHARS)
        );
        int consecutiveLineBreaks = 0;
        for (int index = 0;
             index < withoutAnsi.length() && clean.length() < MAX_SERVER_MESSAGE_CHARS;
             index++) {
            char value = withoutAnsi.charAt(index);
            if (value == '\n') {
                if (clean.length() > 0 && consecutiveLineBreaks < 2) clean.append('\n');
                consecutiveLineBreaks++;
                continue;
            }
            if (value == '\t') {
                if (clean.length() + 4 <= MAX_SERVER_MESSAGE_CHARS) clean.append("    ");
                consecutiveLineBreaks = 0;
                continue;
            }
            if (Character.isISOControl(value)) continue;
            clean.append(value);
            consecutiveLineBreaks = 0;
        }
        return clean.toString().trim();
    }

    /** Converts common terminal SGR colors into legacy HTML understood by Injector-style banners. */
    private static String ansiSgrToHtml(String raw) {
        java.util.regex.Matcher matcher = ANSI_SGR.matcher(raw);
        StringBuilder converted = new StringBuilder(raw.length() + 64);
        int previousEnd = 0;
        String color = null;
        boolean bold = false;
        boolean formattingOpen = false;
        while (matcher.find()) {
            converted.append(raw, previousEnd, matcher.start());
            if (formattingOpen) appendAnsiClose(converted, color, bold);

            String sequence = matcher.group(1);
            String[] values = sequence == null || sequence.isEmpty()
                    ? new String[]{"0"}
                    : sequence.split(";");
            for (String value : values) {
                int code;
                try {
                    code = Integer.parseInt(value);
                } catch (NumberFormatException ignored) {
                    continue;
                }
                if (code == 0) {
                    color = null;
                    bold = false;
                } else if (code == 1) {
                    bold = true;
                } else if (code == 22) {
                    bold = false;
                } else if (code == 39) {
                    color = null;
                } else {
                    String mapped = ansiColor(code);
                    if (mapped != null) color = mapped;
                }
            }
            formattingOpen = color != null || bold;
            if (formattingOpen) appendAnsiOpen(converted, color, bold);
            previousEnd = matcher.end();
        }
        converted.append(raw, previousEnd, raw.length());
        if (formattingOpen) appendAnsiClose(converted, color, bold);
        return converted.toString();
    }

    private static void appendAnsiOpen(StringBuilder output, String color, boolean bold) {
        if (color != null) output.append("<font color=\"").append(color).append("\">");
        if (bold) output.append("<b>");
    }

    private static void appendAnsiClose(StringBuilder output, String color, boolean bold) {
        if (bold) output.append("</b>");
        if (color != null) output.append("</font>");
    }

    private static String ansiColor(int code) {
        switch (code) {
            case 30: return "#90A4AE";
            case 31: return "#FF5252";
            case 32: return "#69F0AE";
            case 33: return "#FFD740";
            case 34: return "#40C4FF";
            case 35: return "#EA80FC";
            case 36: return "#18FFFF";
            case 37: return "#FFFFFF";
            case 90: return "#B0BEC5";
            case 91: return "#FF8A80";
            case 92: return "#B9F6CA";
            case 93: return "#FFE57F";
            case 94: return "#80D8FF";
            case 95: return "#EA80FC";
            case 96: return "#84FFFF";
            case 97: return "#FFFFFF";
            default: return null;
        }
    }

    private static int clampPort(int port) {
        return Math.max(1, Math.min(65_535, port));
    }

    private static RuntimeException runtime(Throwable error) {
        return error instanceof RuntimeException
                ? (RuntimeException) error
                : new IllegalStateException(firstMessage(error, "Fallo SSH"), error);
    }

    private static String firstMessage(Throwable error, String fallback) {
        Throwable current = error;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().trim().isEmpty()) {
                return current.getMessage().replace('\n', ' ').trim();
            }
            current = current.getCause();
        }
        return fallback;
    }
}

final class SshTunnelHandle implements Closeable {
    private final Session session;
    private final SshSocksServer socksServer;

    SshTunnelHandle(Session session, SshSocksServer socksServer) {
        this.session = session;
        this.socksServer = socksServer;
    }

    public Session getSession() { return session; }
    public SshSocksServer getSocksServer() { return socksServer; }
    public int getSocksPort() { return socksServer.getLocalPort(); }

    @Override
    public void close() {
        try { socksServer.close(); } catch (Throwable ignored) { }
        try { session.disconnect(); } catch (Throwable ignored) { }
    }
}

final class TunnelSocketFactory implements SocketFactory {
    private static final int MAX_HANDSHAKE_BYTES = 16 * 1024;
    private static final int MAX_SSH_BANNER_BYTES = 512;

    private final VpnProfile profile;
    private final Function1<? super String, Unit> onStatus;
    private final PhysicalNetworkSocketConnector socketConnector;
    private final Map<Socket, InputStream> wrappedInputs =
            Collections.synchronizedMap(new WeakHashMap<>());

    TunnelSocketFactory(
            VpnProfile profile,
            Function1<? super String, Unit> onStatus,
            PhysicalNetworkSocketConnector socketConnector
    ) {
        this.profile = profile;
        this.onStatus = onStatus;
        this.socketConnector = socketConnector;
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        String configuredHost = profile.getHost().trim();
        String targetHost = configuredHost.isEmpty() ? host : configuredHost;
        int targetPort = profile.getPort() >= 1 && profile.getPort() <= 65_535 ? profile.getPort() : port;
        ConnectionMode mode = profile.getSelectedMode();
        TlsVerificationMode verificationMode = profile.getSelectedTlsVerificationMode();
        String configuredSni = profile.getSni().trim();
        String sniHost = configuredSni.isEmpty() ? targetHost : configuredSni;
        ProxyConfig proxy = mode.getRequiresProxy()
                && !profile.getProxy().getHost().trim().isEmpty()
                && profile.getProxy().getPort() >= 1
                && profile.getProxy().getPort() <= 65_535
                ? profile.getProxy()
                : null;

        Socket socket;
        boolean payloadAlreadySent = false;

        if (mode.getUsesTls() && !sniHost.equals(targetHost)) {
            status("[TLS] Modo Injector · extremo TCP/SSH " + targetHost + ":" + targetPort +
                    " · SNI TLS " + sniHost);
        }
        if (mode.getUsesTls() && !verificationMode.getVerifiesCertificateChain()) {
            status("[TLS] Compatibilidad explícita · CA y SNI/SAN flexibles · " +
                    "identidad final protegida por huella SSH");
        }

        if (proxy != null) {
            socket = connectDirect(proxy.getHost().trim(), proxy.getPort());
            String type = proxy.getType().trim().toLowerCase(Locale.US);
            if (type.equals("socks") || type.equals("socks5")) {
                performSocks5Handshake(socket, targetHost, targetPort);
            } else if (mode.getRequiresPayload()) {
                wrappedInputs.put(socket, performPayloadHandshake(socket, targetHost, targetPort));
                payloadAlreadySent = true;
            } else {
                performHttpConnectHandshake(socket, targetHost, targetPort);
            }
        } else {
            socket = connectDirect(targetHost, targetPort);
        }

        if (mode.getUsesTls()) {
            status("[TLS] Handshake sobre " + targetHost + ":" + targetPort + " · SNI " + sniHost +
                    " · SSH lógico " + targetHost + ":" + targetPort);
            try {
                javax.net.ssl.SSLSocket tlsSocket = TlsTransport.upgrade(
                        socket,
                        targetHost,
                        targetPort,
                        sniHost,
                        verificationMode
                );
                status("[TLS] " + tlsSocket.getSession().getProtocol() + " · " +
                        tlsSocket.getSession().getCipherSuite() + " · " + verificationMode.getLabel());
                socket = tlsSocket;
            } catch (Exception error) {
                try { socket.close(); } catch (Throwable ignored) { }
                throw new IOException(firstMessage(error, "Fallo TLS"), error);
            }
        }

        if (mode.getRequiresPayload() && !payloadAlreadySent) {
            wrappedInputs.put(socket, performPayloadHandshake(socket, targetHost, targetPort));
        }
        return socket;
    }

    @Override
    public InputStream getInputStream(Socket socket) throws IOException {
        InputStream wrapped = wrappedInputs.remove(socket);
        return wrapped != null ? wrapped : socket.getInputStream();
    }

    @Override
    public OutputStream getOutputStream(Socket socket) throws IOException {
        return socket.getOutputStream();
    }

    private Socket connectDirect(String host, int port) throws IOException {
        status("[NETWORK] Abriendo transporte físico TCP · " + host + ":" + port);
        return socketConnector.connect(host, port, 20_000);
    }

    private void performHttpConnectHandshake(Socket socket, String host, int port) throws IOException {
        String request = "CONNECT " + host + ":" + port + " HTTP/1.1\r\n" +
                "Host: " + host + ":" + port + "\r\n" +
                "Proxy-Connection: Keep-Alive\r\n" +
                "Connection: Keep-Alive\r\n\r\n";
        OutputStream output = socket.getOutputStream();
        output.write(request.getBytes(StandardCharsets.UTF_8));
        output.flush();
        String header = readHttpHeader(socket.getInputStream());
        String statusLine = firstLine(header);
        int code = parseHttpCode(statusLine);
        if (code < 200 || code > 299) {
            throw new IOException("HTTP proxy rechazó la conexión: " + statusLine);
        }
    }

    private String readHttpHeader(InputStream input) throws IOException {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        int previous = -1;
        int beforePrevious = -1;
        int thirdPrevious = -1;
        while (captured.size() < MAX_HANDSHAKE_BYTES) {
            int current = input.read();
            if (current < 0) throw new IOException("El proxy cerró la conexión durante el handshake");
            captured.write(current);
            boolean crlfEnd = thirdPrevious == '\r' && beforePrevious == '\n' && previous == '\r' && current == '\n';
            boolean lfEnd = previous == '\n' && current == '\n';
            if (crlfEnd || lfEnd) return new String(captured.toByteArray(), StandardCharsets.ISO_8859_1);
            thirdPrevious = beforePrevious;
            beforePrevious = previous;
            previous = current;
        }
        throw new IOException("Cabecera HTTP demasiado grande");
    }

    private void performSocks5Handshake(Socket socket, String host, int port) throws IOException {
        OutputStream output = socket.getOutputStream();
        InputStream input = socket.getInputStream();
        output.write(new byte[]{0x05, 0x01, 0x00});
        output.flush();
        byte[] methodResponse = new byte[2];
        readFully(input, methodResponse);
        if ((methodResponse[0] & 0xFF) != 0x05 || (methodResponse[1] & 0xFF) != 0x00) {
            throw new IOException("El proxy SOCKS5 requiere un método de autenticación no soportado");
        }

        byte[] hostBytes = host.getBytes(StandardCharsets.UTF_8);
        if (hostBytes.length > 255) throw new IllegalArgumentException("Nombre de host demasiado largo para SOCKS5");
        byte[] request = new byte[7 + hostBytes.length];
        request[0] = 0x05;
        request[1] = 0x01;
        request[2] = 0x00;
        request[3] = 0x03;
        request[4] = (byte) hostBytes.length;
        System.arraycopy(hostBytes, 0, request, 5, hostBytes.length);
        int portIndex = 5 + hostBytes.length;
        request[portIndex] = (byte) ((port >>> 8) & 0xFF);
        request[portIndex + 1] = (byte) (port & 0xFF);
        output.write(request);
        output.flush();

        byte[] header = new byte[4];
        readFully(input, header);
        if ((header[1] & 0xFF) != 0x00) {
            throw new IOException("SOCKS5 rechazó la conexión (código " + (header[1] & 0xFF) + ")");
        }
        int addressLength;
        switch (header[3] & 0xFF) {
            case 0x01: addressLength = 4; break;
            case 0x03:
                addressLength = input.read();
                if (addressLength < 0) throw new IOException("Respuesta SOCKS5 incompleta");
                break;
            case 0x04: addressLength = 16; break;
            default: throw new IOException("Tipo de dirección SOCKS5 inválido");
        }
        readFully(input, new byte[addressLength + 2]);
    }

    private PushbackInputStream performPayloadHandshake(Socket socket, String host, int port) throws IOException {
        PayloadPlan plan = PayloadEngine.INSTANCE.compile(
                profile.getPayload(),
                new PayloadContext(
                        host,
                        port,
                        profile.getSni().trim().isEmpty() ? host : profile.getSni(),
                        profile.getProxy().getHost(),
                        profile.getProxy().getPort()
                ),
                null
        );
        OutputStream output = socket.getOutputStream();
        for (PayloadAction action : plan.getActions()) {
            if (action instanceof PayloadAction.Send) {
                output.write(((PayloadAction.Send) action).getText().getBytes(StandardCharsets.UTF_8));
                output.flush();
            } else if (action instanceof PayloadAction.Delay) {
                try {
                    Thread.sleep(((PayloadAction.Delay) action).getMillis());
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Payload interrumpido", interrupted);
                }
            }
        }
        status("[PAYLOAD] " + plan.getSegmentCount() + " segmento(s) enviado(s) · contenido protegido");

        PushbackInputStream input = new PushbackInputStream(socket.getInputStream(), MAX_HANDSHAKE_BYTES);
        if (!looksLikeHttpPayload(plan.getRendered())) return input;

        int previousTimeout = socket.getSoTimeout();
        socket.setSoTimeout(8_000);
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            int previous = -1;
            int beforePrevious = -1;
            int thirdPrevious = -1;
            while (captured.size() < MAX_HANDSHAKE_BYTES) {
                int current = input.read();
                if (current < 0) break;
                captured.write(current);
                boolean crlfEnd = thirdPrevious == '\r' && beforePrevious == '\n' && previous == '\r' && current == '\n';
                boolean lfEnd = previous == '\n' && current == '\n';
                String partial = captured.size() <= MAX_SSH_BANNER_BYTES
                        ? new String(captured.toByteArray(), StandardCharsets.ISO_8859_1).trim()
                        : "";
                boolean sshBannerComplete = current == '\n' && partial.toUpperCase(Locale.US).startsWith("SSH-");
                if (crlfEnd || lfEnd || sshBannerComplete) break;
                thirdPrevious = beforePrevious;
                beforePrevious = previous;
                previous = current;
            }
        } catch (SocketTimeoutException ignored) {
            // Some injector-compatible endpoints send no HTTP header before SSH.
        } finally {
            socket.setSoTimeout(previousTimeout);
        }

        byte[] responseBytes = captured.toByteArray();
        if (responseBytes.length == 0) {
            status("[PAYLOAD] Sin cabecera HTTP inmediata · esperando banner SSH");
            return input;
        }
        String responseText = new String(responseBytes, StandardCharsets.ISO_8859_1);
        String firstLine = firstLine(responseText);
        if (firstLine.toUpperCase(Locale.US).startsWith("HTTP/")) {
            int code = parseHttpCode(firstLine);
            if (code < 0 || ((code < 200 || code > 299) && code != 101)) {
                throw new IOException("El servidor rechazó el payload: " + firstLine);
            }
            status("[PAYLOAD] Respuesta aceptada · HTTP " + code);
        } else {
            input.unread(responseBytes);
            if (firstLine.toUpperCase(Locale.US).startsWith("SSH-")) {
                status("[PAYLOAD] Banner SSH directo recibido");
            }
        }
        return input;
    }

    private static boolean looksLikeHttpPayload(String payload) {
        String trimmed = payload.trim();
        int space = trimmed.indexOf(' ');
        String first = (space >= 0 ? trimmed.substring(0, space) : trimmed).toUpperCase(Locale.US);
        return first.equals("CONNECT") || first.equals("GET") || first.equals("POST") || first.equals("HEAD")
                || first.equals("OPTIONS") || first.equals("PUT") || first.equals("PATCH");
    }

    private static void readFully(InputStream input, byte[] buffer) throws IOException {
        int offset = 0;
        while (offset < buffer.length) {
            int count = input.read(buffer, offset, buffer.length - offset);
            if (count < 0) throw new IOException("Socket cerrado durante el handshake");
            offset += count;
        }
    }

    private static String firstLine(String value) {
        int newline = value.indexOf('\n');
        String line = newline >= 0 ? value.substring(0, newline) : value;
        return line.replace("\r", "").trim();
    }

    private static int parseHttpCode(String statusLine) {
        String[] parts = statusLine.trim().split("\\s+");
        if (parts.length < 2) return -1;
        try { return Integer.parseInt(parts[1]); } catch (NumberFormatException ignored) { return -1; }
    }

    private void status(String value) {
        onStatus.invoke(value);
    }

    private static String firstMessage(Throwable error, String fallback) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && !message.trim().isEmpty()) return message.replace('\n', ' ').trim();
            current = current.getCause();
        }
        return fallback;
    }
}

/** Loopback-only SOCKS5 bridge. Every CONNECT opens an SSH direct-tcpip channel. */
final class SshSocksServer implements Closeable {
    private static final InetAddress IPV4_LOOPBACK;
    static {
        try {
            IPV4_LOOPBACK = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        } catch (IOException impossible) {
            throw new ExceptionInInitializerError(impossible);
        }
    }

    private final Session session;
    private final Function1<? super String, Unit> onStatus;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean firstChannelOpenedReported = new AtomicBoolean(false);
    private final AtomicBoolean firstForwardedChannelReported = new AtomicBoolean(false);
    private final AtomicBoolean firstDownlinkReported = new AtomicBoolean(false);
    private final AtomicBoolean firstIoFailureReported = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Set<Socket> clients = ConcurrentHashMap.newKeySet();
    private volatile ServerSocket serverSocket;

    SshSocksServer(Session session, Function1<? super String, Unit> onStatus) {
        this.session = session;
        this.onStatus = onStatus != null ? onStatus : ignored -> Unit.INSTANCE;
    }

    int getLocalPort() {
        ServerSocket server = serverSocket;
        return server != null ? server.getLocalPort() : 0;
    }

    void start() {
        if (!running.compareAndSet(false, true)) throw new IllegalStateException("El bridge SOCKS SSH ya está activo");
        try {
            serverSocket = new ServerSocket(0, 64, IPV4_LOOPBACK);
        } catch (Throwable error) {
            running.set(false);
            executor.shutdownNow();
            throw new IllegalStateException("No se pudo iniciar el bridge SOCKS SSH", error);
        }
        executor.execute(() -> {
            while (running.get()) {
                try {
                    ServerSocket server = serverSocket;
                    if (server == null) break;
                    Socket client = server.accept();
                    clients.add(client);
                    executor.execute(() -> handleClient(client));
                } catch (SocketException error) {
                    if (running.get()) break;
                } catch (IOException error) {
                    if (running.get()) status("[SOCKS] WARN · accept · " + safe(error));
                }
            }
        });
    }

    private void handleClient(Socket client) {
        ChannelDirectTCPIP channel = null;
        boolean socksHandshakeCompleted = false;
        try {
            client.setSoTimeout(15_000);
            InputStream input = client.getInputStream();
            OutputStream output = client.getOutputStream();

            byte[] greeting = new byte[2];
            readFully(input, greeting);
            if ((greeting[0] & 0xFF) != 5) throw new IOException("Versión SOCKS no soportada");
            int methodCount = greeting[1] & 0xFF;
            byte[] methods = new byte[methodCount];
            readFully(input, methods);
            boolean supportsNoAuth = false;
            for (byte method : methods) if ((method & 0xFF) == 0) supportsNoAuth = true;
            if (!supportsNoAuth) {
                output.write(new byte[]{0x05, (byte) 0xFF});
                return;
            }
            output.write(new byte[]{0x05, 0x00});
            output.flush();

            byte[] request = new byte[4];
            readFully(input, request);
            if ((request[0] & 0xFF) != 5 || (request[1] & 0xFF) != 1) {
                sendReply(output, 0x07);
                return;
            }

            String targetHost;
            switch (request[3] & 0xFF) {
                case 0x01: {
                    byte[] address = new byte[4];
                    readFully(input, address);
                    targetHost = InetAddress.getByAddress(address).getHostAddress();
                    break;
                }
                case 0x03: {
                    int length = input.read();
                    if (length < 0) throw new IOException("SOCKS5 incompleto");
                    byte[] domain = new byte[length];
                    readFully(input, domain);
                    targetHost = new String(domain, StandardCharsets.UTF_8);
                    break;
                }
                case 0x04: {
                    byte[] address = new byte[16];
                    readFully(input, address);
                    targetHost = InetAddress.getByAddress(address).getHostAddress();
                    break;
                }
                default:
                    sendReply(output, 0x08);
                    return;
            }
            byte[] portBytes = new byte[2];
            readFully(input, portBytes);
            int targetPort = ((portBytes[0] & 0xFF) << 8) | (portBytes[1] & 0xFF);

            channel = (ChannelDirectTCPIP) session.openChannel("direct-tcpip");
            channel.setHost(targetHost);
            channel.setPort(targetPort);
            String originAddress = client.getInetAddress() != null ? client.getInetAddress().getHostAddress() : "127.0.0.1";
            channel.setOrgIPAddress(originAddress != null ? originAddress : "127.0.0.1");
            channel.setOrgPort(client.getPort());
            InputStream remoteInput = channel.getInputStream();
            OutputStream remoteOutput = channel.getOutputStream();
            channel.connect(20_000);
            if (!channel.isConnected()) throw new IllegalStateException("El servidor SSH no abrió el canal direct-tcpip");
            if (firstChannelOpenedReported.compareAndSet(false, true)) {
                status("[SOCKS] Canal direct-tcpip abierto por el servidor SSH");
            }

            sendReply(output, 0x00);
            socksHandshakeCompleted = true;
            client.setSoTimeout(0);

            executor.execute(() -> {
                try {
                    SshIoBridge.copyClientToSshAndHalfClose(input, remoteOutput, 8192, () -> {
                        if (firstForwardedChannelReported.compareAndSet(false, true)) {
                            status("[SOCKS] Subida activa · datos enviados por direct-tcpip SSH");
                        }
                    });
                } catch (IOException error) {
                    reportIoFailure("subida", error);
                }
            });

            SshIoBridge.copyFromSshChannel(remoteInput, output, 8192, () -> {
                if (firstDownlinkReported.compareAndSet(false, true)) {
                    status("[SOCKS] Bajada activa · respuesta remota recibida por SSH");
                }
            });
        } catch (Throwable error) {
            if (!socksHandshakeCompleted && running.get()) {
                try { sendReply(client.getOutputStream(), 0x01); } catch (Throwable ignored) { }
                status("[SOCKS] ERROR · canal direct-tcpip no disponible · " + safe(error));
            } else if (socksHandshakeCompleted && error instanceof IOException) {
                reportIoFailure("bajada", (IOException) error);
            }
        } finally {
            try { if (channel != null) channel.disconnect(); } catch (Throwable ignored) { }
            try { client.close(); } catch (Throwable ignored) { }
            clients.remove(client);
        }
    }

    private void reportIoFailure(String direction, IOException error) {
        if (!running.get() || !firstIoFailureReported.compareAndSet(false, true)) return;
        status("[SOCKS] WARN · cierre de " + direction + " · " + safe(error));
    }

    private static void sendReply(OutputStream output, int code) throws IOException {
        output.write(new byte[]{0x05, (byte) code, 0x00, 0x01, 0, 0, 0, 0, 0, 0});
        output.flush();
    }

    private static void readFully(InputStream input, byte[] buffer) throws IOException {
        int offset = 0;
        while (offset < buffer.length) {
            int count = input.read(buffer, offset, buffer.length - offset);
            if (count < 0) throw new IOException("Conexión SOCKS cerrada");
            offset += count;
        }
    }

    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) return;
        try { if (serverSocket != null) serverSocket.close(); } catch (Throwable ignored) { }
        serverSocket = null;
        List<Socket> activeClients = new ArrayList<>(clients);
        for (Socket client : activeClients) {
            try { client.close(); } catch (Throwable ignored) { }
        }
        clients.clear();
        executor.shutdownNow();
    }

    private void status(String message) { onStatus.invoke(message); }

    private static String safe(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().trim().isEmpty()) {
                String clean = current.getMessage().replace('\n', ' ').trim();
                return clean.length() <= 160 ? clean : clean.substring(0, 160);
            }
            current = current.getCause();
        }
        return error.getClass().getSimpleName();
    }
}

/** Deterministic byte forwarding helpers for the SSH SOCKS bridge. */
final class SshIoBridge {
    private SshIoBridge() { }

    static long copyToSshChannel(
            InputStream input,
            OutputStream output,
            int bufferSize,
            Runnable onFirstFlush
    ) throws IOException {
        if (bufferSize <= 0) throw new IllegalArgumentException("bufferSize must be positive");
        byte[] buffer = new byte[bufferSize];
        long copied = 0L;
        boolean firstFlushPending = true;
        while (true) {
            int count = input.read(buffer);
            if (count < 0) break;
            if (count == 0) continue;
            output.write(buffer, 0, count);
            output.flush();
            copied += count;
            if (firstFlushPending) {
                firstFlushPending = false;
                if (onFirstFlush != null) onFirstFlush.run();
            }
        }
        return copied;
    }

    static long copyClientToSshAndHalfClose(
            InputStream input,
            OutputStream output,
            int bufferSize,
            Runnable onFirstFlush
    ) throws IOException {
        try {
            return copyToSshChannel(input, output, bufferSize, onFirstFlush);
        } finally {
            output.close();
        }
    }

    static long copyFromSshChannel(
            InputStream input,
            OutputStream output,
            int bufferSize,
            Runnable onFirstFlush
    ) throws IOException {
        if (bufferSize <= 0) throw new IllegalArgumentException("bufferSize must be positive");
        byte[] buffer = new byte[bufferSize];
        long copied = 0L;
        boolean firstFlushPending = true;
        while (true) {
            int count = input.read(buffer);
            if (count < 0) break;
            if (count == 0) continue;
            output.write(buffer, 0, count);
            output.flush();
            copied += count;
            if (firstFlushPending) {
                firstFlushPending = false;
                if (onFirstFlush != null) onFirstFlush.run();
            }
        }
        return copied;
    }
}

final class ProfileUserInfo implements UserInfo {
    private final String password;
    private final Function1<? super String, Unit> onStatus;
    private final AtomicBoolean serverMessageShown = new AtomicBoolean(false);

    ProfileUserInfo(String password) {
        this(password, ignored -> Unit.INSTANCE);
    }

    ProfileUserInfo(String password, Function1<? super String, Unit> onStatus) {
        this.password = password;
        this.onStatus = onStatus != null ? onStatus : ignored -> Unit.INSTANCE;
    }

    @Override public String getPassword() { return password; }

    @Override
    public boolean promptYesNo(String message) {
        String text = message != null ? message.toLowerCase(Locale.US) : "";
        return !text.contains("changed")
                && !text.contains("man-in-the-middle")
                && !text.contains("warning: remote host identification");
    }

    @Override public String getPassphrase() { return null; }
    @Override public boolean promptPassphrase(String message) { return false; }
    @Override public boolean promptPassword(String message) { return password != null && !password.isEmpty(); }
    @Override public void showMessage(String message) { publishServerMessage(message); }

    boolean hasServerMessage() { return serverMessageShown.get(); }

    void publishServerMessage(String message) {
        String normalized = SshTunnelEngine.normalizeServerMessage(message);
        if (normalized.isEmpty()) return;
        if (!serverMessageShown.compareAndSet(false, true)) return;
        onStatus.invoke("[SSH] Mensaje del servidor · " + normalized);
    }
}
