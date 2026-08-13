package com.ghostnexora.vpn.tunnel;

import com.ghostnexora.vpn.data.model.ConnectionMode;
import com.jcraft.jsch.Session;

/** Public handle owned by GhostVpnService for one active Java tunnel runtime. */
public final class TunnelRuntime {
    private final ConnectionMode mode;
    private final SshTunnelHandle sshHandle;
    private final long verifiedLatencyMs;

    TunnelRuntime(ConnectionMode mode, SshTunnelHandle sshHandle, long verifiedLatencyMs) {
        this.mode = mode;
        this.sshHandle = sshHandle;
        this.verifiedLatencyMs = verifiedLatencyMs;
    }

    public ConnectionMode getMode() { return mode; }
    public long getVerifiedLatencyMs() { return verifiedLatencyMs; }

    SshTunnelHandle sshHandle() { return sshHandle; }

    public boolean hasConnectedSshSession() {
        if (sshHandle == null) return true;
        Session session = sshHandle.getSession();
        return session != null && session.isConnected();
    }

    void closeSsh() {
        if (sshHandle != null) sshHandle.close();
    }
}
