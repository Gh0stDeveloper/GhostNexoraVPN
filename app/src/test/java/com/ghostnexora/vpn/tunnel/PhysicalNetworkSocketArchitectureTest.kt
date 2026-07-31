package com.ghostnexora.vpn.tunnel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guards the Injector-compatible distinction between SSH identity and TLS endpoint. */
class PhysicalNetworkSocketArchitectureTest {
    @Test
    fun connectorResolvesAndBindsEveryAttemptToANonVpnNetwork() {
        val source = sourceFile(
            "src/main/java/com/ghostnexora/vpn/tunnel/PhysicalNetworkSocketConnector.kt"
        )

        assertTrue(source.contains("NetworkCapabilities.NET_CAPABILITY_NOT_VPN"))
        assertTrue(source.contains("manager.registerNetworkCallback"))
        assertTrue(source.contains("network.getAllByName(host)"))
        assertTrue(source.contains("network.bindSocket(socket)"))
        assertTrue(source.contains("for ((addressIndex, address) in addresses.withIndex())"))
        assertTrue(source.contains("sortedBy { if (it is Inet4Address) 0 else 1 }"))
        assertTrue(source.contains("[TCP-ALL-FAILED]"))
        assertFalse(
            "Deprecated global network enumeration must not return",
            source.contains("manager.allNetworks")
        )
    }

    @Test
    fun injectorCompatibilityUsesSniAsTcpTlsEndpointAndKeepsSshIdentity() {
        val source = sourceFile(
            "src/main/java/com/ghostnexora/vpn/tunnel/SshTunnelEngine.kt"
        )
        val createSocketSection = source
            .substringAfter("override fun createSocket")
            .substringBefore("override fun getInputStream")
        val transportIndex = createSocketSection.indexOf(
            "socket = connectDirect(tlsEndpointHost, targetPort)"
        )
        val tlsIndex = createSocketSection.indexOf("TlsTransport.upgrade(")

        assertTrue(source.contains("TlsVerificationMode.CUSTOM_SNI"))
        assertTrue(source.contains("jsch.getSession(profile.username.trim(), transportHost, transportPort)"))
        assertTrue(createSocketSection.contains("val tlsEndpointHost = if ("))
        assertTrue(createSocketSection.contains("verificationMode == TlsVerificationMode.CUSTOM_SNI"))
        assertTrue(createSocketSection.contains("sniHost"))
        assertTrue("The selected TLS endpoint is not opened", transportIndex >= 0)
        assertTrue("TLS wrapping is missing", tlsIndex >= 0)
        assertTrue("TCP transport must be established before TLS", transportIndex < tlsIndex)
        assertTrue(createSocketSection.contains("targetHost = tlsEndpointHost"))
        assertTrue(createSocketSection.contains("sniHost = sniHost"))
        assertTrue(createSocketSection.contains("destino SSH lógico"))
    }

    @Test
    fun strictModeFallsBackToConfiguredSshHostAsTlsEndpoint() {
        val source = sourceFile(
            "src/main/java/com/ghostnexora/vpn/tunnel/SshTunnelEngine.kt"
        )
        val createSocketSection = source
            .substringAfter("override fun createSocket")
            .substringBefore("override fun getInputStream")

        assertTrue(
            createSocketSection.contains(
                "mode.usesTls && verificationMode == TlsVerificationMode.CUSTOM_SNI"
            )
        )
        assertTrue(
            "Strict TLS must retain the configured SSH host as the physical endpoint",
            createSocketSection.contains("else {\n            targetHost\n        }")
        )
    }

    private fun sourceFile(relativePath: String): String {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../app/$relativePath")
        )
        val file = candidates.firstOrNull(File::isFile)
            ?: error("Unable to locate source file: $relativePath")
        return file.readText()
    }
}
