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
        sni = "www.twitter.com"
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
        assertTrue(failure.solution.contains("modo compatible", ignoreCase = true))
        assertTrue(failure.solution.contains("SNI/SAN"))
    }

    @Test
    fun connectionRefusedExplainsThatSniIsNotAnAlternateEndpoint() {
        val failure = ConnectionErrorCatalog.classify(
            IllegalStateException(
                "Session.connect: java.net.ConnectException: failed to connect to " +
                    "example.com/192.0.2.1 (port 443): ECONNREFUSED (Connection refused)"
            ),
            sshProfile
        )

        assertEquals("TCP-002", failure.code)
        assertEquals("TCP", failure.stage)
        assertTrue(failure.title.contains("rechazó", ignoreCase = true))
        assertTrue(failure.solution.contains("alcanzó la IP", ignoreCase = true))
        assertTrue(failure.solution.contains("SNI"))
        assertTrue(failure.solution.contains("no sustituye", ignoreCase = true))
    }

    @Test
    fun mapsSshOutboundFailureWithoutSuggestingOtherProtocols() {
        val failure = ConnectionErrorCatalog.classify(
            IllegalStateException("outbound could not deliver generate_204"),
            sshProfile
        )
        assertEquals("SSH-ROUTE-204", failure.code)
        assertEquals("SSH", failure.stage)
        assertTrue(failure.title.contains("SSH + SSL"))
        assertTrue(!failure.solution.contains("V2Ray", ignoreCase = true))
        assertTrue(!failure.solution.contains("Trojan", ignoreCase = true))
        assertTrue(!failure.solution.contains("UUID", ignoreCase = true))
        assertTrue(!failure.solution.contains("path", ignoreCase = true))
        assertTrue(!failure.solution.contains("service name", ignoreCase = true))
    }

    @Test
    fun mapsClosedPipeToSshBridgeInsteadOfGenericXrayFields() {
        val failure = ConnectionErrorCatalog.classify(
            IllegalStateException(
                "El servidor o la configuración no entregan acceso a Internet: io: read/write on closed pipe"
            ),
            sshProfile
        )

        assertEquals("SSH-BRIDGE-502", failure.code)
        assertEquals("SSH", failure.stage)
        assertTrue(!failure.solution.contains("UUID", ignoreCase = true))
        assertTrue(!failure.solution.contains("path", ignoreCase = true))
        assertTrue(!failure.solution.contains("service name", ignoreCase = true))
    }

    @Test
    fun mapsLoopbackHealthRouteFailureToSshOnly() {
        val failure = ConnectionErrorCatalog.classify(
            IllegalStateException(
                "La ruta Xray → SOCKS → SSH no completó el handshake TLS remoto: connection refused"
            ),
            sshProfile
        )

        assertEquals("SSH-ROUTE-204", failure.code)
        assertEquals("SSH", failure.stage)
        assertTrue(failure.title.contains("SSH + SSL"))
        assertTrue(!failure.solution.contains("UUID", ignoreCase = true))
        assertTrue(!failure.solution.contains("path", ignoreCase = true))
    }
}
