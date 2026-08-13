package com.ghostnexora.vpn.tunnel;

/** Public immutable traffic delta emitted by the native Xray VPN core. */
public final class XrayTrafficDelta {
    private final long receivedBytes;
    private final long sentBytes;

    public XrayTrafficDelta() { this(0L, 0L); }

    public XrayTrafficDelta(long receivedBytes, long sentBytes) {
        this.receivedBytes = Math.max(0L, receivedBytes);
        this.sentBytes = Math.max(0L, sentBytes);
    }

    public long getReceivedBytes() { return receivedBytes; }
    public long getSentBytes() { return sentBytes; }
}
