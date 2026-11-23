package com.ghostnexora.vpn.tunnel;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class XrayTrafficDeltaTest {
    @Test
    public void readsOnlyVpnProxyCounters() {
        XrayTrafficDelta traffic = XrayTrafficDelta.fromOutboundStats(
                "direct,uplink,23;dns-out,downlink,55;proxy,downlink,607;proxy,uplink,18;");
        assertEquals(607L, traffic.getReceivedBytes());
        assertEquals(18L, traffic.getSentBytes());
    }

    @Test
    public void missingAndMalformedCountersDoNotDiscardValidTraffic() {
        XrayTrafficDelta traffic = XrayTrafficDelta.fromOutboundStats(
                "proxy,uplink,invalid;proxy,downlink,8;");
        assertEquals(8L, traffic.getReceivedBytes());
        assertEquals(0L, traffic.getSentBytes());
        assertEquals(0L, XrayTrafficDelta.fromOutboundStats("").getReceivedBytes());
    }
}
