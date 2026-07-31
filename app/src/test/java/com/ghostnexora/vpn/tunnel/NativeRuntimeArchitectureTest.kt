package com.ghostnexora.vpn.tunnel

import com.ghostnexora.vpn.data.model.ConnectionMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeRuntimeArchitectureTest {
    @Test
    fun everyEnabledModeHasAnExplicitRuntimePlan() {
        ConnectionMode.entries
            .filter(ConnectionMode::supported)
            .forEach { mode ->
                val plan = NativeRuntimeArchitecture.plan(mode)
                assertTrue(plan.tunAdapter.contains("Xray TUN", ignoreCase = true))
                assertTrue(plan.protocolCore.isNotBlank())
                assertTrue(NativeRuntimeArchitecture.statusLine(mode).isNotBlank())
            }
    }

    @Test
    fun sshUsesLocalSocksAndDoesNotClaimGenericUdp() {
        val plan = NativeRuntimeArchitecture.plan(ConnectionMode.SSH_DIRECT)

        assertTrue(plan.protocolCore.contains("JSch"))
        assertTrue(plan.localHop?.contains("SOCKS5") == true)
        assertTrue(plan.carriesTcp)
        assertFalse(plan.carriesUdp)
        assertTrue(plan.limitations.contains("BadVPN", ignoreCase = true))
    }

    @Test
    fun udpModeIsHysteria2InsideXray() {
        val plan = NativeRuntimeArchitecture.plan(ConnectionMode.UDP)

        assertTrue(plan.protocolCore.contains("Hysteria2", ignoreCase = true))
        assertTrue(plan.protocolCore.contains("Xray", ignoreCase = true))
        assertTrue(plan.carriesUdp)
        assertTrue(plan.limitations.contains("Hysteria v1", ignoreCase = true))
    }
}
