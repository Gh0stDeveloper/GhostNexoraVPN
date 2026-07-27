package com.ghostnexora.vpn.tunnel

import android.content.Context
import com.ghostnexora.vpn.data.model.ConnectionMode
import com.ghostnexora.vpn.data.model.VpnProfile

/**
 * Orquesta el transporte seleccionado y Xray TUN.
 *
 * - SSH: sesión cifrada -> SOCKS5 local -> Xray TUN.
 * - V2Ray/Trojan/Hysteria2: Xray TUN -> outbound nativo.
 *
 * El perfil se puede validar antes de crear el TUN. De esta manera una
 * configuración inválida no captura todo el tráfico del teléfono ni aparenta
 * que Android perdió la conexión de datos móviles.
 */
class TunnelManager(
    context: Context,
    private val onCoreStatus: (String) -> Unit = {}
) {
    private val sshEngine = SshTunnelEngine(context.applicationContext)
    private val xrayEngine = XrayCoreEngine(context.applicationContext, onCoreStatus)

    /** Comprueba el servidor y el outbound real sin activar la VPN del sistema. */
    @Synchronized
    fun verify(profile: VpnProfile): OutboundCheck {
        require(profile.selectedMode.supported) {
            "El modo ${profile.connectionModeLabel} no está habilitado"
        }

        return if (profile.selectedMode.isSsh) {
            val sshHandle = sshEngine.connectWithSocks(profile)
            try {
                val config = XrayConfigFactory.build(profile, sshHandle.socksPort)
                xrayEngine.verifyOutbound(config)
            } finally {
                sshHandle.close()
            }
        } else {
            xrayEngine.verifyOutbound(XrayConfigFactory.build(profile))
        }
    }

    @Synchronized
    fun start(profile: VpnProfile, tunFd: Int): TunnelRuntime {
        require(profile.selectedMode.supported) {
            "El modo ${profile.connectionModeLabel} no está habilitado"
        }

        return if (profile.selectedMode.isSsh) {
            val sshHandle = sshEngine.connectWithSocks(profile)
            try {
                val config = XrayConfigFactory.build(profile, sshHandle.socksPort)
                xrayEngine.start(config, tunFd)
                val outbound = xrayEngine.verifyActiveOutbound()
                onCoreStatus("Salida de Internet validada · ${outbound.latencyMs} ms")
                TunnelRuntime(profile.selectedMode, sshHandle, outbound.latencyMs)
            } catch (error: Throwable) {
                xrayEngine.stop()
                sshHandle.close()
                throw error
            }
        } else {
            try {
                val config = XrayConfigFactory.build(profile)
                xrayEngine.start(config, tunFd)
                val outbound = xrayEngine.verifyActiveOutbound()
                onCoreStatus("Salida de Internet validada · ${outbound.latencyMs} ms")
                TunnelRuntime(profile.selectedMode, null, outbound.latencyMs)
            } catch (error: Throwable) {
                xrayEngine.stop()
                throw error
            }
        }
    }

    @Synchronized
    fun stop(runtime: TunnelRuntime?) {
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
