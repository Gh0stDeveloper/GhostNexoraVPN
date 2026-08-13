package com.ghostnexora.vpn.tunnel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guards startup ordering across the Java VpnService and native runtime. */
class VpnStartupArchitectureTest {
    @Test
    fun tunnelStartDoesNotRunAnActiveOutboundProbe() {
        val source = sourceFile("src/main/java/com/ghostnexora/vpn/tunnel/TunnelManager.java")
        val startSection = source.substringAfter("public synchronized TunnelRuntime start").substringBefore("private TunnelRuntime startCore")
        assertFalse(startSection.contains("verifyActiveOutbound") || startSection.contains("verifyActive()"))
        assertTrue(source.substringAfter("private TunnelRuntime startCore").contains("xrayEngine.start"))
    }

    @Test
    fun javaServicePublishesConnectedBeforeSchedulingInternetVerification() {
        val source = sourceFile("src/main/java/com/ghostnexora/vpn/service/GhostVpnService.java")
        val connectSection = source.substringAfter("private void handleConnect").substringBefore("private void handleDisconnect")
        val connectedIndex = connectSection.indexOf("publishState(connected)")
        val verificationIndex = connectSection.indexOf("startInitialOutboundVerification(profile)")
        assertTrue(connectedIndex >= 0)
        assertTrue(verificationIndex >= 0)
        assertTrue(connectedIndex < verificationIndex)
    }

    @Test
    fun javaServiceOwnsTunAndMandatorySelfBypass() {
        val source = sourceFile("src/main/java/com/ghostnexora/vpn/service/GhostVpnService.java")
        val routingSection = source.substringAfter("private void applyAppRouting").substringBefore("private void registerPhysicalNetworkCallback")
        assertTrue(source.contains("extends VpnService"))
        assertTrue(source.contains("Builder builder = new Builder()"))
        assertTrue(routingSection.contains("builder.addDisallowedApplication(getPackageName())"))
        assertFalse(routingSection.contains("try {\n                builder.addDisallowedApplication(getPackageName())"))
    }

    @Test
    fun activeProbeDoesNotHoldTheManagerOrCoreMonitorDuringRemoteIo() {
        val managerSource = sourceFile("src/main/java/com/ghostnexora/vpn/tunnel/TunnelManager.java")
        assertFalse(managerSource.contains("public synchronized OutboundCheck verifyActive()"))

        val coreSource = sourceFile("src/main/java/com/ghostnexora/vpn/tunnel/XrayCoreEngine.java")
        val coreProbe = coreSource.substringAfter("public OutboundCheck verifyActiveOutbound()").substringBefore("public synchronized XrayTrafficDelta drainProxyTraffic")
        assertTrue(coreProbe.contains("synchronized (this)"))
        assertTrue(coreProbe.indexOf("synchronized (this)") < coreProbe.indexOf("measureAcrossEndpoints"))
        assertFalse(coreSource.contains("public synchronized OutboundCheck verifyActiveOutbound()"))
    }

    @Test
    fun nativeCallbacksQueuePersistenceAwayFromTheStartupThread() {
        val source = sourceFile("src/main/java/com/ghostnexora/vpn/service/GhostVpnService.java")
        val onCreate = source.substringAfter("public void onCreate()")
            .substringBefore("public IBinder onBind")
        val logSection = source.substringAfter("private void log(")
            .substringBefore("private String friendlyConnectionError")
        val enqueueIndex = logSection.indexOf("logExecutor.execute")
        val persistenceIndex = logSection.indexOf("repositoryBridge.log")

        assertTrue(source.contains("Executors.newSingleThreadExecutor"))
        assertTrue(source.contains("ghost-vpn-log-writer"))
        assertTrue(onCreate.contains("log(LogLevel.INFO, status"))
        assertTrue(enqueueIndex >= 0)
        assertTrue(persistenceIndex > enqueueIndex)
        assertFalse(onCreate.contains("repositoryBridge.log"))
    }

    private fun sourceFile(relativePath: String): String {
        val candidates = listOf(File(relativePath), File("app/$relativePath"), File("../app/$relativePath"))
        val file = candidates.firstOrNull(File::isFile) ?: error("Unable to locate source file: $relativePath")
        return file.readText()
    }
}
