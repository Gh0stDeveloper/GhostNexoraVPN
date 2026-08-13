package com.ghostnexora.vpn.tunnel.strategy;

import android.content.Context;

import com.ghostnexora.vpn.data.model.ConnectionMode;
import com.ghostnexora.vpn.data.model.VpnProfile;
import com.ghostnexora.vpn.tunnel.SshTunnelEngine;
import com.jcraft.jsch.Session;

/** Java strategy wrapper for all SSH-based connection modes. */
public final class SshTunnelStrategy implements TunnelStrategy {
    private final SshTunnelEngine engine;

    public SshTunnelStrategy() {
        this(new SshTunnelEngine((Context) null));
    }

    public SshTunnelStrategy(SshTunnelEngine engine) {
        if (engine == null) throw new IllegalArgumentException("engine == null");
        this.engine = engine;
    }

    @Override
    public boolean supports(ConnectionMode mode) {
        if (mode == null) return false;
        return mode == ConnectionMode.SSH_DIRECT
                || mode == ConnectionMode.SSL_SNI
                || mode == ConnectionMode.SSH_PROXY
                || mode == ConnectionMode.SSH_PAYLOAD
                || mode == ConnectionMode.SSH_PAYLOAD_SSL
                || mode == ConnectionMode.SSH_PAYLOAD_PROXY
                || mode == ConnectionMode.SSH_PAYLOAD_PROXY_SSL;
    }

    @Override
    public Session connect(VpnProfile profile) {
        return engine.connect(profile);
    }

    @Override
    public void disconnect(Session session) {
        engine.disconnect(session);
    }
}
