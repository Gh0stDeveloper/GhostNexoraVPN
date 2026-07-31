package com.ghostnexora.vpn.tunnel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guards the Injector-compatible distinction between SSH/TCP identity and TLS SNI. */
class PhysicalNetworkSocketArchitectureTest {
    @Test
    fun connectorProtectsThenBindsEveryAttemptToANonVpnNetwork() {
        val source = sourceFile(
            "src/main/java/com/ghostnexora/vpn/tunnel/PhysicalNetworkSocketConnector.kt"
        )
        val protectIndex = source.indexOf("OutboundSocketProtection.protect(socket)")
        val bindIndex = source.indexOf("network.bindSocket(socket)")

        assertTrue(source.contains("NetworkCapabilities.NET_CAPABILITY_NOT_VPN"))
        assertTrue(source.contains("manager.registerNetworkCallback"))
        assertTrue(source.contains("network.getAllByName(host)"))
        assertTrue(protectIndex >= 0)
        assertTrue(bindIndex >= 0)
        assertTrue("VpnService.protect must run before Network.bindSocket", protectIndex < bindIndex)
        assertTrue(source.contains("[VPN-LOOP-001]"))
        assertTrue(source.contains("for ((addressIndex, address) in addresses.withIndex())"))
        assertTrue(source.contains("sortedBy { if (it is Inet4Address) 0 else 1 }"))
        assertTrue(source.contains("[TCP-ALL-FAILED]"))
        assertFalse(
            "Deprecated global network enumeration must not return",
            source.contains("manager.allNetworks")
        )
    }

    @Test
    fun vpnServiceOwnsTheSocketProtectionLifecycle() {
        val source = sourceFile(
            "src/main/java/com/ghostnexora/vpn/service/GhostVpnService.kt"
        )

        assertTrue(source.contains("OutboundSocketProtection.install { socket -> protect(socket) }"))
        assertTrue(source.contains("cleanupTunnel(closeTun = true)\n        OutboundSocketProtection.clear()"))
        assertTrue(source.contains("check(protect(socket))"))
        assertTrue(source.contains("underlyingNetwork?.bindSocket(socket)"))
    }

    @Test
    fun injectorCompatibilityConnectsTcpToSshHostAndUsesSniOnlyForTls() {
        val source = sourceFile(
            "src/main/java/com/ghostnexora/vpn/tunnel/SshTunnelEngine.kt"
        )
        val createSocketSection = source
            .substringAfter("override fun createSocket")
            .substringBefore("override fun getInputStream")
        val transportIndex = createSocketSection.indexOf(
            "socket = connectDirect(targetHost, targetPort)"
        )
        val tlsIndex = createSocketSection.indexOf("TlsTransport.upgrade(")

        assertTrue(source.contains("jsch.getSession(profile.username.trim(), transportHost, transportPort)"))
        assertFalse(
            "The SNI must never replace the real SSH endpoint",
            createSocketSection.contains("tlsEndpointHost")
        )
        assertTrue("The real SSH endpoint is not opened", transportIndex >= 0)
        assertTrue("TLS wrapping is missing", tlsIndex >= 0)
        assertTrue("TCP transport must be established before TLS", transportIndex < tlsIndex)
        assertTrue(createSocketSection.contains("targetHost = targetHost"))
        assertTrue(createSocketSection.contains("sniHost = sniHost"))
        assertTrue(createSocketSection.contains("extremo TCP/SSH"))
        assertTrue(createSocketSection.contains("SNI TLS"))
    }

    @Test
    fun proxiesAlsoTunnelToTheRealSshHostBeforeTls() {
        val source = sourceFile(
            "src/main/java/com/ghostnexora/vpn/tunnel/SshTunnelEngine.kt"
        )
        val createSocketSection = source
            .substringAfter("override fun createSocket")
            .substringBefore("override fun getInputStream")

        assertTrue(
            createSocketSection.contains(
                "performSocks5Handshake(socket, targetHost, targetPort)"
            )
        )
        assertTrue(
            createSocketSection.contains(
                "performHttpConnectHandshake(socket, targetHost, targetPort)"
            )
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
