package com.ghostnexora.vpn.tunnel;

import android.content.Context;

import com.ghostnexora.vpn.data.model.ConnectionMode;
import com.ghostnexora.vpn.data.model.NetworkPreferences;
import com.ghostnexora.vpn.data.model.VpnProfile;

import java.net.InetAddress;
import java.net.ServerSocket;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

/** Coordinates the Java SSH/Xray VPN runtime and Android TUN attachment. */
public final class TunnelManager {
    private final Function1<? super String, Unit> onCoreStatus;
    private final SshTunnelEngine sshEngine;
    private final XrayCoreEngine xrayEngine;

    public TunnelManager(Context context, Function1<? super String, Unit> onCoreStatus) {
        if (context == null) throw new IllegalArgumentException("context == null");
        this.onCoreStatus = onCoreStatus != null ? onCoreStatus : ignored -> Unit.INSTANCE;
        Context appContext = context.getApplicationContext();
        this.sshEngine = new SshTunnelEngine(appContext, this.onCoreStatus);
        this.xrayEngine = new XrayCoreEngine(appContext, this.onCoreStatus);
    }

    public TunnelManager(Context context) {
        this(context, ignored -> Unit.INSTANCE);
    }

    public synchronized OutboundCheck verify(VpnProfile profile, NetworkPreferences preferences) {
        requireSupported(profile);
        reportRuntimePlan(profile.getSelectedMode());
        status("[NETWORK] Preflight iniciado · " + profile.getHost() + ":" + profile.getPort());
        status("[SETTINGS] " + preferences.getIpMode().getLabel() + " · MTU " +
                preferences.getValidatedMtu() + " · " + preferences.getDnsMode().getLabel());
        if (profile.getSelectedMode().isSsh()) {
            prepareSshRuntime(profile);
            SshTunnelHandle sshHandle = sshEngine.connectWithSocks(profile);
            try {
                status("[SSH] Autenticación completada");
                status("[SOCKS] Bridge local listo · 127.0.0.1:" + sshHandle.getSocksPort());
                String config = StableXrayConfigFactory.INSTANCE.build(profile, sshHandle.getSocksPort(), preferences, null);
                status("[XRAY] Configuración preflight · " + StableXrayConfigFactory.INSTANCE.summary(profile, preferences));
                OutboundCheck result = xrayEngine.verifyOutbound(config);
                status("[NETWORK] Salida remota verificada · " + result.getLatencyMs() + " ms");
                return result;
            } finally {
                sshHandle.close();
                status("[SSH] Sesión preflight cerrada");
            }
        }
        String config = StableXrayConfigFactory.INSTANCE.build(profile, null, preferences, null);
        status("[XRAY] Configuración preflight · " + StableXrayConfigFactory.INSTANCE.summary(profile, preferences));
        OutboundCheck result = xrayEngine.verifyOutbound(config);
        status("[NETWORK] Salida remota verificada · " + result.getLatencyMs() + " ms");
        return result;
    }

    public synchronized TunnelRuntime start(VpnProfile profile, int tunFd, NetworkPreferences preferences) {
        requireSupported(profile);
        reportRuntimePlan(profile.getSelectedMode());
        status("[TUN] Adjuntando descriptor Android al core");
        if (profile.getSelectedMode().isSsh()) {
            prepareSshRuntime(profile);
            SshTunnelHandle sshHandle = sshEngine.connectWithSocks(profile);
            try {
                status("[SSH] Sesión autenticada y cifrada");
                status("[SOCKS] Bridge SSH activo · 127.0.0.1:" + sshHandle.getSocksPort());
                int healthCheckPort = reserveLoopbackPort();
                String config = StableXrayConfigFactory.INSTANCE.build(profile, sshHandle.getSocksPort(), preferences, healthCheckPort);
                return startCore(profile, config, tunFd, sshHandle, preferences, healthCheckPort);
            } catch (Throwable error) {
                xrayEngine.stop();
                sshHandle.close();
                status("[ERROR] Cadena SSH/TUN detenida · " + shortMessage(error, 180));
                throw rethrow(error);
            }
        }
        String config = StableXrayConfigFactory.INSTANCE.build(profile, null, preferences, null);
        try {
            return startCore(profile, config, tunFd, null, preferences, null);
        } catch (Throwable error) {
            xrayEngine.stop();
            status("[ERROR] Xray/TUN detenido · " + shortMessage(error, 180));
            throw rethrow(error);
        }
    }

    private TunnelRuntime startCore(VpnProfile profile, String config, int tunFd, SshTunnelHandle sshHandle,
                                    NetworkPreferences preferences, Integer healthCheckPort) {
        status("[XRAY] " + StableXrayConfigFactory.INSTANCE.summary(profile, preferences));
        status("[DNS] " + preferences.getDnsMode().getLabel() + " · " + String.join(", ", preferences.dnsServers()));
        status("[ROUTING] Regla explícita TUN → proxy · TCP/UDP según capacidad del runtime");
        xrayEngine.start(config, tunFd, healthCheckPort);
        status("[TUN] Xray Core conectado a la interfaz Android");
        status("[NETWORK] Core activo · verificación de salida programada en segundo plano");
        return new TunnelRuntime(profile.getSelectedMode(), sshHandle, 0L);
    }

    private void prepareSshRuntime(VpnProfile profile) {
        JschRuntime.install(onCoreStatus);
        status("[NETWORK] Transporte TCP · " + profile.getHost() + ":" + profile.getPort());
        if (profile.getSelectedMode().getRequiresProxy()) {
            status("[PROXY] " + profile.getProxy().getType().toUpperCase() + " · " + profile.getProxy().getHost() + ":" + profile.getProxy().getPort());
        }
        if (profile.getSelectedMode().getUsesTls()) {
            String sni = profile.getSni().trim().isEmpty() ? profile.getHost() : profile.getSni();
            status("[TLS] Handshake SNI · " + sni + " · " + profile.getSelectedTlsVerificationMode().getLabel());
        }
        if (profile.getSelectedMode().getRequiresPayload()) status("[PAYLOAD] Inyección HTTP preparada · contenido protegido");
        status("[SSH] Iniciando intercambio de claves y autenticación");
    }

    private void reportRuntimePlan(ConnectionMode mode) {
        NativeRuntimePlan plan = NativeRuntimeArchitecture.plan(mode);
        status("[CORE] " + NativeRuntimeArchitecture.statusLine(mode));
        status("[ROUTING] " + plan.getLimitations());
    }

    private int reserveLoopbackPort() {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByAddress(new byte[]{127, 0, 0, 1}))) {
            return server.getLocalPort();
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo reservar el puerto de comprobación local", error);
        }
    }

    public OutboundCheck verifyActive() { return xrayEngine.verifyActiveOutbound(); }
    public XrayTrafficDelta drainTraffic() { return xrayEngine.drainProxyTraffic(); }

    public synchronized void stop(TunnelRuntime runtime) {
        if (runtime == null && !xrayEngine.isRunning()) return;
        status("[TUN] Deteniendo core y liberando transporte");
        try { xrayEngine.stop(); } catch (Throwable ignored) { }
        try { if (runtime != null) runtime.closeSsh(); } catch (Throwable ignored) { }
    }

    public boolean isAlive(TunnelRuntime runtime) {
        return runtime != null && xrayEngine.isRunning() && runtime.hasConnectedSshSession();
    }

    public String coreVersion() { return xrayEngine.version(); }

    public boolean isSupported(ConnectionMode mode) {
        if (mode == null || !mode.getSupported()) return false;
        try { NativeRuntimeArchitecture.plan(mode); return true; } catch (Throwable ignored) { return false; }
    }

    private static void requireSupported(VpnProfile profile) {
        if (profile == null) throw new IllegalArgumentException("profile == null");
        if (!profile.getSelectedMode().getSupported()) throw new IllegalArgumentException("Mode " + profile.getConnectionModeLabel() + " is not enabled");
    }

    private void status(String message) { onCoreStatus.invoke(message); }

    private static RuntimeException rethrow(Throwable error) {
        return error instanceof RuntimeException ? (RuntimeException) error : new IllegalStateException(shortMessage(error, 180), error);
    }

    private static String shortMessage(Throwable error, int max) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && !message.trim().isEmpty()) {
                String clean = message.replace('\n', ' ').trim();
                return clean.length() <= max ? clean : clean.substring(0, max);
            }
            current = current.getCause();
        }
        return error != null ? error.getClass().getSimpleName() : "sin detalle";
    }
}
