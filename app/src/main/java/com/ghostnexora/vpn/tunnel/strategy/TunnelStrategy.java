package com.ghostnexora.vpn.tunnel.strategy;

import com.ghostnexora.vpn.data.model.ConnectionMode;
import com.ghostnexora.vpn.data.model.VpnProfile;
import com.jcraft.jsch.Session;

/** Java contract for SSH-backed tunnel strategies. */
public interface TunnelStrategy {
    boolean supports(ConnectionMode mode);
    Session connect(VpnProfile profile);
    void disconnect(Session session);
}
