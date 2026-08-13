package com.ghostnexora.vpn.tunnel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guards the Java VPN socket boundary and Injector-compatible SSH/SNI routing. */
class PhysicalNetworkSocketArchitectureTest {
    @Test
    fun javaConnectorProtectsThenBindsEveryAttemptToANonVpnNetwork() {
        val source = sourceFile("src/main/java/com/ghostnexora/vpn/tunnel/PhysicalNetworkSocketConnector.java")
        val firstSocketCreation = source.indexOf("Socket socket = configuredAndProtectedSocket()")
        val firstPhysicalBind = source.indexOf("network.bindSocket(socket)")
        val protectionHelper = source.substringAfter("private Socket configuredAndProtectedSocket()")
            .substringBefore("private static Socket configuredSocket()")
        assertTrue(source.contains("NetworkCapabilities.NET_CAPABILITY_NOT_VPN"))
        assertTrue(source.contains("connectivityManager.registerNetworkCallback"))
        assertTrue(source.contains("network.getAllByName(host)"))
        assertTrue(firstSocketCreation >= 0 && firstPhysicalBind >= 0 && firstSocketCreation < firstPhysicalBind)
        assertTrue(protectionHelper.contains("OutboundSocketProtection.protect(socket)"))
        assertTrue(protectionHelper.contains("[VPN-LOOP-001]"))
        assertTrue(source.contains("[TCP-ALL-FAILED]"))
        assertFalse(source.contains("manager.allNetworks"))
    }

    @Test
    fun javaVpnServiceInstallsProtectionWithoutOpeningProbeSockets() {
        val source = sourceFile("src/main/java/com/ghostnexora/vpn/service/GhostVpnService.java")
        assertTrue(source.contains("OutboundSocketProtection.install(this::protect)"))
        assertTrue(source.contains("OutboundSocketProtection.clear()"))
        assertFalse(source.contains("measureTcpLatency"))
        assertFalse(source.contains("new Socket"))
        assertTrue(source.contains("setUnderlyingNetworks(new Network[]{network})"))
        assertTrue(source.contains("extends VpnService"))
    }

    @Test
    fun injectorCompatibilityConnectsTcpToSshHostAndUsesSniOnlyForTls() {
        val source = sourceFile("src/main/java/com/ghostnexora/vpn/tunnel/SshTunnelEngine.java")
        val createSocketSection = source.substringAfter("public Socket createSocket").substringBefore("public InputStream getInputStream")
        val transportIndex = createSocketSection.indexOf("socket = connectDirect(targetHost, targetPort)")
        val tlsIndex = createSocketSection.indexOf("TlsTransport.upgrade(")
        assertTrue(source.contains("jsch.getSession(profile.getUsername().trim(), transportHost, transportPort)"))
        assertFalse(createSocketSection.contains("tlsEndpointHost"))
        assertTrue(transportIndex >= 0)
        assertTrue(tlsIndex >= 0)
        assertTrue(transportIndex < tlsIndex)
        assertTrue(createSocketSection.contains("targetHost,"))
        assertTrue(createSocketSection.contains("sniHost,"))
        assertTrue(createSocketSection.contains("extremo TCP/SSH"))
        assertTrue(createSocketSection.contains("SNI TLS"))
    }

    @Test
    fun proxiesAlsoTunnelToTheRealSshHostBeforeTls() {
        val source = sourceFile("src/main/java/com/ghostnexora/vpn/tunnel/SshTunnelEngine.java")
        val createSocketSection = source.substringAfter("public Socket createSocket").substringBefore("public InputStream getInputStream")
        assertTrue(createSocketSection.contains("performSocks5Handshake(socket, targetHost, targetPort)"))
        assertTrue(createSocketSection.contains("performHttpConnectHandshake(socket, targetHost, targetPort)"))
    }

    private fun sourceFile(relativePath: String): String {
        val candidates = listOf(File(relativePath), File("app/$relativePath"), File("../app/$relativePath"))
        val file = candidates.firstOrNull(File::isFile) ?: error("Unable to locate source file: $relativePath")
        return file.readText()
    }
}
