package com.ghostnexora.vpn.tunnel

import com.ghostnexora.vpn.data.model.ConnectionMode
import com.ghostnexora.vpn.data.model.VpnProfile
import com.ghostnexora.vpn.tunnel.strategy.SshTunnelStrategy
import com.ghostnexora.vpn.tunnel.strategy.TunnelStrategy
import com.jcraft.jsch.Session

/**
 * Resuelve la estrategia de conexión a partir del modo del perfil.
 *
 * Mantiene el punto de entrada único para el servicio VPN.
 */
class TunnelManager(
    private val strategies: List<TunnelStrategy> = listOf(
        SshTunnelStrategy()
    )
) {

    fun connect(profile: VpnProfile): Session {
        val strategy = strategies.firstOrNull { it.supports(profile.selectedMode) }
            ?: error("No hay estrategia disponible para el modo ${profile.selectedMode.label}")
        return strategy.connect(profile)
    }

    fun disconnect(session: Session?) {
        runCatching { session?.disconnect() }
    }

    fun isSupported(mode: ConnectionMode): Boolean = strategies.any { it.supports(mode) }
}
