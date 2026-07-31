package com.ghostnexora.vpn.tunnel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guards the injector-compatible distinction between transport host and TLS SNI. */
class PhysicalNetworkSocketArchitectureTest {
    @Test
    fun connectorResolvesAndBindsEveryAttemptToANonVpnNetwork() {
        val source = sourceFile(
            "src/main/java/com/ghostnexora/vpn/tunnel/PhysicalNetworkSocketConnector.kt"
        )

        assertTrue(source.contains("NetworkCapabilities.NET_CAPABILITY_NOT_VPN"))
        assertTrue(source.contains("network.getAllByName(host)"))
        assertTrue(source.contains("network.bindSocket(socket)"))
        assertTrue(source.contains("for ((addressIndex, address) in addresses.withIndex())"))
        assertTrue(source.contains("sortedBy { if (it is Inet4Address) 0 else 1 }"))
    }

    @Test
    fun sshConnectsToTransportHostBeforeSendingIndependentSni() {
        val source = sourceFile(
            "src/main/java/com/ghostnexora/vpn/tunnel/SshTunnelEngine.kt"
        )
        val socketIndex = source.indexOf("socketConnector.connect(host, port, 20_000)")
        val tlsIndex = source.indexOf("TlsTransport.upgrade(")

        assertTrue("The physical transport connector is missing", socketIndex >= 0)
        assertTrue("TLS wrapping is missing", tlsIndex >= 0)
        assertTrue("TCP transport must be established before TLS/SNI", socketIndex < tlsIndex)
        assertTrue(source.contains("targetHost = targetHost"))
        assertTrue(source.contains("sniHost = sniHost"))
        assertFalse(
            "The SNI must never silently replace the configured TCP destination",
            source.contains("connectDirect(sniHost") || source.contains("connect(sniHost, targetPort")
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
