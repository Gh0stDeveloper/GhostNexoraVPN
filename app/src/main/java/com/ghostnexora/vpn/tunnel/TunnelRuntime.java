package com.ghostnexora.vpn.tunnel;

import com.ghostnexora.vpn.data.model.ConnectionMode;
import com.jcraft.jsch.Session;

/** Public handle owned by GhostVpnService for one active Java tunnel runtime. */
public final class TunnelRuntime {
    private final ConnectionMode mode;
    private final SshTunnelHandle sshHandle;
    private final DnsTunnelProcess dnsTransport;

    TunnelRuntime(ConnectionMode mode, SshTunnelHandle sshHandle) {
        this(mode, sshHandle, null);
    }

    TunnelRuntime(ConnectionMode mode, SshTunnelHandle sshHandle, DnsTunnelProcess dnsTransport) {
        this.mode = mode;
        this.dnsTransport = dnsTransport;
        this.sshHandle = sshHandle;
    }

    public ConnectionMode getMode() { return mode; }

    SshTunnelHandle sshHandle() { return sshHandle; }

    public boolean hasConnectedSshSession() {
        if (sshHandle == null) return true;
        Session session = sshHandle.getSession();
        return session != null && session.isConnected();
    }

    boolean hasLiveDnsTransport() {
        return dnsTransport == null || dnsTransport.isAlive();
    }

    void closeTransport() {
        if (sshHandle != null) sshHandle.close();
        if (dnsTransport != null) dnsTransport.close();
    }
}
