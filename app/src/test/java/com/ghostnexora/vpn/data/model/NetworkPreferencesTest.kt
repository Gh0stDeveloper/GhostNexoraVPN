package com.ghostnexora.vpn.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkPreferencesTest {
    @Test
    fun clampsMtuAndReconnectAttempts() {
        val preferences = NetworkPreferences(mtu = 900, reconnectMaxAttempts = 99)
        assertEquals(NetworkPreferences.MIN_MTU, preferences.validatedMtu)
        assertEquals(12, preferences.validatedReconnectAttempts)
    }

    @Test
    fun ipv4OnlyDoesNotCaptureIpv6() {
        assertFalse(IpMode.IPV4_ONLY.capturesIpv6)
        assertTrue(IpMode.DUAL_STACK.capturesIpv6)
        assertEquals("UseIP", IpMode.DUAL_STACK.xrayQueryStrategy)
    }

    @Test
    fun customDnsRemovesBlanksAndDuplicates() {
        val preferences = NetworkPreferences(
            dnsMode = DnsMode.CUSTOM,
            customDnsPrimary = " 9.9.9.9 ",
            customDnsSecondary = "9.9.9.9"
        )
        assertEquals(listOf("9.9.9.9"), preferences.dnsServers())
    }
}