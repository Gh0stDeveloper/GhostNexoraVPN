package com.ghostnexora.vpn.tunnel

import com.ghostnexora.vpn.data.model.ConnectionMode
import com.ghostnexora.vpn.data.model.TlsVerificationMode
import com.ghostnexora.vpn.data.model.VpnProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionErrorCatalogTest {
    private val sshProfile = VpnProfile(
        name = "SSH",
        host = "example.com",
        port = 443,
        username = "user",
        password = "secret",
        connectionMode = ConnectionMode.SSL_SNI.id,
        sni = "example.com"
    )

    @Test
    fun mapsJschClassLoadingFailure() {
        val failure = ConnectionErrorCatalog.classify(
            ClassNotFoundException("com.jcraft.jsch.jce.Random"),
            sshProfile
        )
        assertEquals("SSH-500", failure.code)
        assertTrue(failure.userMessage().contains("SSH-500"))
    }

    @Test
    fun mapsTlsHostnameFailure() {
        val failure = ConnectionErrorCatalog.classify(
            IllegalStateException("Hostname verification failed for SNI"),
            sshProfile
        )
        assertEquals("TLS-004", failure.code)
        assertEquals("TLS", failure.stage)
    }

    @Test
    fun customSniFailurePointsToCertificateTrustInsteadOfHostname() {
        val failure = ConnectionErrorCatalog.classify(
            IllegalStateException("certificate trust anchor failed"),
            sshProfile.copy(tlsVerificationMode = TlsVerificationMode.CUSTOM_SNI.id)
        )

        assertEquals("TLS-004", failure.code)
        assertTrue(failure.solution.contains("already allows an SNI/SAN mismatch"))
    }

    @Test
    fun mapsOutboundFailure() {
        val failure = ConnectionErrorCatalog.classify(
            IllegalStateException("outbound could not deliver generate_204"),
            sshProfile
        )
        assertEquals("ROUTE-204", failure.code)
    }
}
