package com.ghostnexora.vpn.tunnel.strategy

import com.ghostnexora.vpn.data.model.ConnectionMode
import com.ghostnexora.vpn.data.model.VpnProfile
import com.ghostnexora.vpn.tunnel.SshTunnelEngine
import com.jcraft.jsch.Session

/**
 * Estrategia única para los modos basados en SSH.
 * Los modos soportados pueden compartir el mismo motor mientras el core de datos se completa.
 */
class SshTunnelStrategy(
    private val engine: SshTunnelEngine = SshTunnelEngine()
) : TunnelStrategy {

    override fun supports(mode: ConnectionMode): Boolean = when (mode) {
        ConnectionMode.SSH_DIRECT,
        ConnectionMode.SSL_SNI,
        ConnectionMode.SSH_PROXY,
        ConnectionMode.SSH_PAYLOAD,
        ConnectionMode.SSH_PAYLOAD_SSL,
        ConnectionMode.SSH_PAYLOAD_PROXY -> true

        else -> false
    }

    override fun connect(profile: VpnProfile): Session = engine.connect(profile)

    override fun disconnect(session: Session?) = engine.disconnect(session)
}
