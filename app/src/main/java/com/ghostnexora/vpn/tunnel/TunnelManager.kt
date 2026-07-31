package com.ghostnexora.vpn.tunnel

import android.content.Context
import com.ghostnexora.vpn.data.model.ConnectionMode
import com.ghostnexora.vpn.data.model.NetworkPreferences
import com.ghostnexora.vpn.data.model.VpnProfile

/**
 * Coordinates SSH/Xray transports and emits stage-oriented operational logs.
 *
 * SSH modes follow the same high-level pipeline used by mature injector-style
 * clients: transport socket -> optional proxy -> optional TLS/SNI -> optional
 * HTTP payload -> SSH authentication -> local SOCKS -> Xray TUN.
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

    @Synchronized
    fun start(
        profile: VpnProfile,
        tunFd: Int,
        preferences: NetworkPreferences = NetworkPreferences()
    ): TunnelRuntime {
        require(profile.selectedMode.supported) {
            "Mode ${profile.connectionModeLabel} is not enabled"
        }
        onCoreStatus("[TUN] Adjuntando descriptor Android al core")

        return if (profile.selectedMode.isSsh) {
            prepareSshRuntime(profile)
            val sshHandle = sshEngine.connectWithSocks(profile)
            try {
                onCoreStatus("[SSH] Sesión autenticada y cifrada")
                onCoreStatus("[SOCKS] Bridge SSH activo · 127.0.0.1:${sshHandle.socksPort}")
                val config = StableXrayConfigFactory.build(profile, sshHandle.socksPort, preferences)
                startAndVerify(profile, config, tunFd, sshHandle, preferences)
            } catch (error: Throwable) {
                xrayEngine.stop()
                sshHandle.close()
                onCoreStatus("[ERROR] Cadena SSH/TUN detenida · ${error.message.orEmpty().take(180)}")
                throw error
            }
        } else {
            val config = StableXrayConfigFactory.build(profile, preferences = preferences)
            try {
                startAndVerify(profile, config, tunFd, null, preferences)
            } catch (error: Throwable) {
                xrayEngine.stop()
                onCoreStatus("[ERROR] Xray/TUN detenido · ${error.message.orEmpty().take(180)}")
                throw error
            }
        }
    }

    private fun startAndVerify(
        profile: VpnProfile,
        config: String,
        tunFd: Int,
        sshHandle: SshTunnelHandle?,
        preferences: NetworkPreferences
    ): TunnelRuntime {
        onCoreStatus("[XRAY] ${StableXrayConfigFactory.summary(profile, preferences)}")
        onCoreStatus("[DNS] ${preferences.dnsMode.label} · ${preferences.dnsServers().joinToString()}")
        onCoreStatus("[ROUTING] Regla explícita TUN → proxy · TCP/UDP")
        xrayEngine.start(config, tunFd)
        onCoreStatus("[TUN] Xray Core conectado a la interfaz Android")
        val outbound = xrayEngine.verifyActiveOutbound()
        onCoreStatus("[NETWORK] Internet validado por el outbound · ${outbound.latencyMs} ms")
        return TunnelRuntime(profile.selectedMode, sshHandle, outbound.latencyMs)
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

    /** Checks the already-running core without changing Android routes. */
    @Synchronized
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
    fun isSupported(mode: ConnectionMode): Boolean = mode.supported
}

data class TunnelRuntime(
    val mode: ConnectionMode,
    val sshHandle: SshTunnelHandle?,
    val verifiedLatencyMs: Long = 0L
)
