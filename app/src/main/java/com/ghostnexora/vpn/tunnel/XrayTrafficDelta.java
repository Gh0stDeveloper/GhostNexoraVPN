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

    static XrayTrafficDelta fromOutboundStats(String snapshot) {
        if (snapshot == null || snapshot.isEmpty()) return new XrayTrafficDelta();
        long received = 0L;
        long sent = 0L;
        for (String row : snapshot.split(";")) {
            String[] fields = row.split(",", 3);
            if (fields.length != 3 || !"proxy".equals(fields[0])) continue;
            try {
                long bytes = Long.parseLong(fields[2]);
                switch (fields[1]) {
                    case "downlink":
                        received = bytes;
                        break;
                    case "uplink":
                        sent = bytes;
                        break;
                    default:
                        break;
                }
            } catch (NumberFormatException ignored) {
                // Keep the other direction if just one entry is malformed.
            }
        }
        return new XrayTrafficDelta(received, sent);
    }

    public long getReceivedBytes() { return receivedBytes; }
    public long getSentBytes() { return sentBytes; }
}
