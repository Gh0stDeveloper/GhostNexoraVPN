package com.ghostnexora.vpn.tunnel

import com.ghostnexora.vpn.data.model.ConnectionMode
import com.ghostnexora.vpn.data.model.TlsVerificationMode
import com.ghostnexora.vpn.data.model.VpnProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

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

    private val injectorProfile = sshProfile.copy(
        tlsVerificationMode = TlsVerificationMode.CUSTOM_SNI.id
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
            injectorProfile
        )

        assertEquals("TLS-004", failure.code)
        assertTrue(failure.solution.contains("modo compatible", ignoreCase = true))
        assertTrue(failure.solution.contains("SNI/SAN"))
    }

    @Test
    fun connectionRefusedInStrictModeKeepsConfiguredHostGuidance() {
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
        assertTrue(failure.solution.contains("host", ignoreCase = true))
        assertTrue(failure.solution.contains("puerto", ignoreCase = true))
    }

    @Test
    fun exhaustedInjectorEndpointIsTcpFailureInsteadOfTlsFailure() {
        val failure = ConnectionErrorCatalog.classify(
            IOException(
                "Session.connect: [TCP-ALL-FAILED] No fue posible conectar con ninguna IP " +
                    "de analytics.twitter.com:443 tras 2 intento(s).",
                IOException("failed to connect after 8000ms: ECONNREFUSED")
            ),
            injectorProfile
        )

        assertEquals("TCP-003", failure.code)
        assertEquals("TCP", failure.stage)
        assertTrue(failure.solution.contains("SNI", ignoreCase = true))
        assertTrue(failure.solution.contains("HTTP Injector", ignoreCase = true))
    }

    @Test
    fun directInjectorRefusalExplainsThatSniIsTheTlsEndpoint() {
        val failure = ConnectionErrorCatalog.classify(
            IllegalStateException("analytics.twitter.com:443 ECONNREFUSED Connection refused"),
            injectorProfile
        )

        assertEquals("TCP-002", failure.code)
        assertEquals("TCP", failure.stage)
        assertTrue(failure.solution.contains("SNI como extremo TCP/TLS", ignoreCase = true))
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
