package com.ghostnexora.vpn.tunnel;

/** Public immutable traffic delta emitted by the native VPN core. */
public final class VpnTrafficDelta {
    private final long receivedBytes;
    private final long sentBytes;

    public VpnTrafficDelta() { this(0L, 0L); }

    public VpnTrafficDelta(long receivedBytes, long sentBytes) {
        this.receivedBytes = Math.max(0L, receivedBytes);
        this.sentBytes = Math.max(0L, sentBytes);
    }

    public long getReceivedBytes() { return receivedBytes; }
    public long getSentBytes() { return sentBytes; }
}
