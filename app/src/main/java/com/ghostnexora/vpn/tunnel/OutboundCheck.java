package com.ghostnexora.vpn.tunnel;

/** Public immutable result for an active/preflight VPN outbound check. */
public final class OutboundCheck {
    private final long latencyMs;
    private final String endpoint;

    public OutboundCheck(long latencyMs, String endpoint) {
        this.latencyMs = latencyMs;
        this.endpoint = endpoint != null ? endpoint : "";
    }

    public long getLatencyMs() { return latencyMs; }
    public String getEndpoint() { return endpoint; }
}
