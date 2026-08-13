package com.ghostnexora.vpn.tunnel;

/** Public immutable result for an active/preflight VPN outbound check. */
public final class OutboundCheck {
    private final long latencyMs;
    private final String endpoint;
    private final int statusCode;

    public OutboundCheck(long latencyMs, String endpoint) {
        this(latencyMs, endpoint, 0);
    }

    public OutboundCheck(long latencyMs, String endpoint, int statusCode) {
        this.latencyMs = latencyMs;
        this.endpoint = endpoint != null ? endpoint : "";
        this.statusCode = Math.max(0, statusCode);
    }

    public long getLatencyMs() { return latencyMs; }
    public String getEndpoint() { return endpoint; }
    public int getStatusCode() { return statusCode; }
}
