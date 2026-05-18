package com.ghostnexora.vpn.tunnel.strategy

import com.ghostnexora.vpn.data.model.ConnectionMode
import com.ghostnexora.vpn.data.model.VpnProfile
import com.jcraft.jsch.Session

interface TunnelStrategy {
    fun supports(mode: ConnectionMode): Boolean
    fun connect(profile: VpnProfile): Session
    fun disconnect(session: Session?)
}
