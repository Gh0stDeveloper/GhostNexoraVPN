package com.ghostnexora.vpn.tunnel

import android.content.Context
import com.ghostnexora.vpn.data.model.ConnectionMode
import com.ghostnexora.vpn.data.model.VpnProfile

/**
 * Orquesta el transporte seleccionado y Xray TUN.
 *
 * - SSH: sesión cifrada -> SOCKS5 local -> Xray TUN.
 * - V2Ray/Trojan/UDP: Xray TUN -> outbound nativo.
 */
class TunnelManager(
    context: Context,
    private val onCoreStatus: (String) -> Unit = {}
) {
    private val sshEngine = SshTunnelEngine(context.applicationContext)
    private val xrayEngine = XrayCoreEngine(context.applicationContext, onCoreStatus)

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
                TunnelRuntime(profile.selectedMode, sshHandle)
            } catch (error: Throwable) {
                sshHandle.close()
                throw error
            }
        } else {
            val config = XrayConfigFactory.build(profile)
            xrayEngine.start(config, tunFd)
            TunnelRuntime(profile.selectedMode, null)
        }
    }

    @Synchronized
    fun stop(runtime: TunnelRuntime?) {
        runCatching { xrayEngine.stop() }
        runCatching { runtime?.sshHandle?.close() }
    }

    fun coreVersion(): String = xrayEngine.version()

    fun isSupported(mode: ConnectionMode): Boolean = mode.supported
}

data class TunnelRuntime(
    val mode: ConnectionMode,
    val sshHandle: SshTunnelHandle?
)
