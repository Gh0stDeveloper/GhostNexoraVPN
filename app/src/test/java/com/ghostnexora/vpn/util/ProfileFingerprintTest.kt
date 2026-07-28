package com.ghostnexora.vpn.util

import com.ghostnexora.vpn.data.model.ConnectionMode
import com.ghostnexora.vpn.data.model.VpnProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ProfileFingerprintTest {
    private val base = VpnProfile(
        name = "Primary",
        host = "VPN.EXAMPLE.COM",
        port = 443,
        username = "user",
        password = "secret",
        connectionMode = ConnectionMode.SSL_SNI.id,
        sslEnabled = true,
        sni = "cdn.example.com"
    )

    @Test
    fun ignoresCosmeticNameAndIdChanges() {
        val copy = base.copy(id = "other-id", name = "Different label")
        assertEquals(ProfileFingerprint.of(base), ProfileFingerprint.of(copy))
    }

    @Test
    fun detectsSecurityRelevantChanges() {
        assertNotEquals(ProfileFingerprint.of(base), ProfileFingerprint.of(base.copy(password = "other")))
        assertNotEquals(ProfileFingerprint.of(base), ProfileFingerprint.of(base.copy(sni = "other.example.com")))
    }

    @Test
    fun mergeKeepsOnlyUniqueProfiles() {
        val unique = base.copy(id = "unique", host = "other.example.com")
        val (profiles, skipped) = ProfileFingerprint.uniqueAgainst(
            imported = listOf(base.copy(id = "new-duplicate"), unique, unique.copy(id = "duplicate-inside-import")),
            existing = listOf(base)
        )
        assertEquals(1, profiles.size)
        assertEquals("other.example.com", profiles.single().host)
        assertEquals(2, skipped)
    }
}