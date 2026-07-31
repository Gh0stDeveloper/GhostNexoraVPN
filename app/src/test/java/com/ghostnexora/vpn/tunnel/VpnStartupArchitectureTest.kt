package com.ghostnexora.vpn.tunnel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the startup ordering that prevents the VPN from routing its own
 * transport back into the TUN and blocking the UI on a remote health probe.
 *
 * These are source-level architecture assertions because the Android
 * VpnService.Builder and the native AndroidLibXrayLite controller cannot be
 * instantiated reliably in a local JVM unit test.
 */
class VpnStartupArchitectureTest {
    @Test
    fun tunnelStartDoesNotRunAnActiveOutboundProbe() {
        val source = sourceFile(
            "src/main/java/com/ghostnexora/vpn/tunnel/TunnelManager.kt"
        )
        val startSection = source
            .substringAfter("fun start(")
            .substringBefore("private fun prepareSshRuntime")

        assertFalse(
            "TunnelManager.start must not wait for active outbound verification",
            startSection.contains("verifyActiveOutbound") ||
                startSection.contains("verifyActive()")
        )
        assertTrue(
            "TunnelManager.start must still start the Xray core",
            startSection.contains("xrayEngine.start")
        )
    }

    @Test
    fun servicePublishesConnectedBeforeSchedulingInternetVerification() {
        val source = sourceFile(
            "src/main/java/com/ghostnexora/vpn/service/GhostVpnService.kt"
        )
        val connectSection = source
            .substringAfter("private suspend fun handleConnect")
            .substringBefore("private suspend fun handleDisconnect")
        val connectedIndex = connectSection.indexOf("publishState(connected)")
        val verificationIndex = connectSection.indexOf(
            "startInitialOutboundVerification(profile)"
        )

        assertTrue("Connected state publication is missing", connectedIndex >= 0)
        assertTrue("Background startup verification is missing", verificationIndex >= 0)
        assertTrue(
            "Connected must be published before the Internet verification starts",
            connectedIndex < verificationIndex
        )
    }

    @Test
    fun fullDeviceRoutingUsesAMandatorySelfBypass() {
        val source = sourceFile(
            "src/main/java/com/ghostnexora/vpn/service/GhostVpnService.kt"
        )
        val routingSection = source
            .substringAfter("private fun applyAppRouting")
            .substringBefore("private suspend fun logTransportReady")

        assertTrue(
            "The VPN package must be excluded from its own TUN",
            routingSection.contains("builder.addDisallowedApplication(packageName)")
        )
        assertFalse(
            "A self-bypass failure must not be swallowed",
            routingSection.contains(
                "runCatching { builder.addDisallowedApplication(packageName) }"
            )
        )
    }

    @Test
    fun activeProbeDoesNotHoldTheManagerOrCoreMonitorDuringRemoteIo() {
        val managerSource = sourceFile(
            "src/main/java/com/ghostnexora/vpn/tunnel/TunnelManager.kt"
        )
        val managerProbe = managerSource
            .substringAfter("fun verifyActive()")
            .substringBefore("fun drainTraffic")
        assertFalse(
            "TunnelManager.verifyActive must remain independently cancellable",
            managerProbe.contains("@Synchronized")
        )

        val coreSource = sourceFile(
            "src/main/java/com/ghostnexora/vpn/tunnel/XrayCoreEngine.kt"
        )
        val coreProbe = coreSource
            .substringAfter("fun verifyActiveOutbound()")
            .substringBefore("fun drainProxyTraffic")
        assertTrue(
            "The core probe must take only a short synchronized snapshot",
            coreProbe.contains("synchronized(this)")
        )
        assertTrue(
            "Remote measurement must happen after the synchronized snapshot",
            coreProbe.indexOf("synchronized(this)") <
                coreProbe.indexOf("measureAcrossEndpoints")
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
