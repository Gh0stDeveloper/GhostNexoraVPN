package com.ghostnexora.vpn.tunnel

import android.content.Context
import com.ghostnexora.vpn.data.model.ConnectionMode
import com.ghostnexora.vpn.data.model.NetworkPreferences
import com.ghostnexora.vpn.data.model.VpnProfile
import java.net.InetAddress
import java.net.ServerSocket

/**
 * Coordinates SSH/Xray transports and emits stage-oriented operational logs.
 *
 * SSH modes follow the same high-level pipeline used by mature injector-style
 * clients: transport socket -> optional proxy -> optional TLS/SNI -> optional
 * HTTP payload -> SSH authentication -> local SOCKS -> Xray TUN.
 *
 * Starting the runtime and proving Internet reachability are intentionally
 * separate operations. [start] returns as soon as the SSH/Xray/TUN chain is
 * alive; [verifyActive] is executed by the service on a background coroutine.
 * This prevents an outbound probe from blocking Connected-state publication.
 */
class TunnelManager(
    context: Context,
    private val onCoreStatus: (String) -> Unit = {}
) {
    private val sshEngine = SshTunnelEngine(context.applicationContext, onCoreStatus)
    private val xrayEngine = XrayCoreEngine(context.applicationContext, onCoreStatus)

    /** Validates the real outbound before Android creates a full-device route. */
    @Synchronized
    fun verify(
        profile: VpnProfile,
        preferences: NetworkPreferences = NetworkPreferences()
    ): OutboundCheck {
        require(profile.selectedMode.supported) {
            "Mode ${profile.connectionModeLabel} is not enabled"
        }
        reportRuntimePlan(profile.selectedMode)
        onCoreStatus("[NETWORK] Preflight iniciado · ${profile.host}:${profile.port}")
        onCoreStatus("[SETTINGS] ${preferences.ipMode.label} · MTU ${preferences.validatedMtu} · ${preferences.dnsMode.label}")

        return if (profile.selectedMode.isSsh) {
            prepareSshRuntime(profile)
            val sshHandle = sshEngine.connectWithSocks(profile)
            try {
                onCoreStatus("[SSH] Autenticación completada")
                onCoreStatus("[SOCKS] Bridge local listo · 127.0.0.1:${sshHandle.socksPort}")
                val config = StableXrayConfigFactory.build(profile, sshHandle.socksPort, preferences)
                onCoreStatus("[XRAY] Configuración preflight · ${StableXrayConfigFactory.summary(profile, preferences)}")
                val result = xrayEngine.verifyOutbound(config)
                onCoreStatus("[NETWORK] Salida remota verificada · ${result.latencyMs} ms")
                result
            } finally {
                sshHandle.close()
                onCoreStatus("[SSH] Sesión preflight cerrada")
            }
        } else {
            val config = StableXrayConfigFactory.build(profile, preferences = preferences)
            onCoreStatus("[XRAY] Configuración preflight · ${StableXrayConfigFactory.summary(profile, preferences)}")
            val result = xrayEngine.verifyOutbound(config)
            onCoreStatus("[NETWORK] Salida remota verificada · ${result.latencyMs} ms")
            result
        }
    }

    /**
     * Starts the transport and native core only.
     *
     * No Internet, ping, TLS or SOCKS probe runs in this call. The caller can
     * therefore publish Connected immediately after the native core reports a
     * successful start and schedule [verifyActive] independently.
     */
    @Synchronized
    fun start(
        profile: VpnProfile,
        tunFd: Int,
        preferences: NetworkPreferences = NetworkPreferences()
    ): TunnelRuntime {
        require(profile.selectedMode.supported) {
            "Mode ${profile.connectionModeLabel} is not enabled"
        }
        reportRuntimePlan(profile.selectedMode)
        onCoreStatus("[TUN] Adjuntando descriptor Android al core")

        return if (profile.selectedMode.isSsh) {
            prepareSshRuntime(profile)
            val sshHandle = sshEngine.connectWithSocks(profile)
            try {
                onCoreStatus("[SSH] Sesión autenticada y cifrada")
                onCoreStatus("[SOCKS] Bridge SSH activo · 127.0.0.1:${sshHandle.socksPort}")
                val healthCheckPort = reserveLoopbackPort()
                val config = StableXrayConfigFactory.build(
                    profile = profile,
                    sshSocksPort = sshHandle.socksPort,
                    preferences = preferences,
                    healthCheckPort = healthCheckPort
                )
                startCore(
                    profile = profile,
                    config = config,
                    tunFd = tunFd,
                    sshHandle = sshHandle,
                    preferences = preferences,
                    healthCheckPort = healthCheckPort
                )
            } catch (error: Throwable) {
                xrayEngine.stop()
                sshHandle.close()
                onCoreStatus("[ERROR] Cadena SSH/TUN detenida · ${error.message.orEmpty().take(180)}")
                throw error
            }
        } else {
            val config = StableXrayConfigFactory.build(profile, preferences = preferences)
            try {
                startCore(
                    profile = profile,
                    config = config,
                    tunFd = tunFd,
                    sshHandle = null,
                    preferences = preferences,
                    healthCheckPort = null
                )
            } catch (error: Throwable) {
                xrayEngine.stop()
                onCoreStatus("[ERROR] Xray/TUN detenido · ${error.message.orEmpty().take(180)}")
                throw error
            }
        }
    }

    private fun startCore(
        profile: VpnProfile,
        config: String,
        tunFd: Int,
        sshHandle: SshTunnelHandle?,
        preferences: NetworkPreferences,
        healthCheckPort: Int?
    ): TunnelRuntime {
        onCoreStatus("[XRAY] ${StableXrayConfigFactory.summary(profile, preferences)}")
        onCoreStatus("[DNS] ${preferences.dnsMode.label} · ${preferences.dnsServers().joinToString()}")
        onCoreStatus("[ROUTING] Regla explícita TUN → proxy · TCP/UDP según capacidad del runtime")
        xrayEngine.start(config, tunFd, healthCheckPort)
        onCoreStatus("[TUN] Xray Core conectado a la interfaz Android")
        onCoreStatus("[NETWORK] Core activo · verificación de salida programada en segundo plano")
        return TunnelRuntime(profile.selectedMode, sshHandle)
    }

    private fun prepareSshRuntime(profile: VpnProfile) {
        JschRuntime.install(onCoreStatus)
        onCoreStatus("[NETWORK] Transporte TCP · ${profile.host}:${profile.port}")
        if (profile.selectedMode.requiresProxy) {
            onCoreStatus("[PROXY] ${profile.proxy.type.uppercase()} · ${profile.proxy.host}:${profile.proxy.port}")
        }
        if (profile.selectedMode.usesTls) {
            onCoreStatus(
                "[TLS] Handshake SNI · ${profile.sni.ifBlank { profile.host }} · " +
                    profile.selectedTlsVerificationMode.label
            )
        }
        if (profile.selectedMode.requiresPayload) {
            onCoreStatus("[PAYLOAD] Inyección HTTP preparada · contenido protegido")
        }
        onCoreStatus("[SSH] Iniciando intercambio de claves y autenticación")
    }

    private fun reportRuntimePlan(mode: ConnectionMode) {
        val plan = NativeRuntimeArchitecture.plan(mode)
        onCoreStatus("[CORE] ${NativeRuntimeArchitecture.statusLine(mode)}")
        onCoreStatus("[ROUTING] ${plan.limitations}")
    }

    /**
     * Xray requires a concrete port for the loopback health-check inbound.
     * The reservation is released immediately before Xray binds it; binding
     * to IPv4 loopback keeps the probe private to this device.
     */
    private fun reserveLoopbackPort(): Int = ServerSocket(
        0,
        1,
        InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
    ).use { it.localPort }

    /**
     * Checks the already-running core without changing Android routes.
     *
     * This method deliberately does not synchronize on [TunnelManager]. A
     * slow network probe must never prevent [stop] from tearing down the core.
     */
    fun verifyActive(): OutboundCheck = xrayEngine.verifyActiveOutbound()

    fun drainTraffic(): XrayTrafficDelta = xrayEngine.drainProxyTraffic()

    @Synchronized
    fun stop(runtime: TunnelRuntime?) {
        if (runtime == null && !xrayEngine.isRunning) return
        onCoreStatus("[TUN] Deteniendo core y liberando transporte")
        runCatching { xrayEngine.stop() }
        runCatching { runtime?.sshHandle?.close() }
    }

    fun isAlive(runtime: TunnelRuntime?): Boolean {
        if (runtime == null || !xrayEngine.isRunning) return false
        val ssh = runtime.sshHandle?.session
        return ssh == null || ssh.isConnected
    }

    fun coreVersion(): String = xrayEngine.version()
    fun isSupported(mode: ConnectionMode): Boolean =
        mode.supported && runCatching { NativeRuntimeArchitecture.plan(mode) }.isSuccess
}

data class TunnelRuntime(
    val mode: ConnectionMode,
    val sshHandle: SshTunnelHandle?,
    val verifiedLatencyMs: Long = 0L
)
