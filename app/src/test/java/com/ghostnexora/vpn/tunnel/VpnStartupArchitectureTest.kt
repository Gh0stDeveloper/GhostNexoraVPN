package com.ghostnexora.vpn.tunnel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guards startup ordering across the Java VpnService and native runtime. */
class VpnStartupArchitectureTest {
    @Test
    fun tunnelStartDefersQualificationUntilTheExistingRuntimeIsActive() {
        val manager = sourceFile("src/main/java/com/ghostnexora/vpn/tunnel/TunnelManager.java")
        val startSection = manager.substringAfter("public synchronized TunnelRuntime start")
            .substringBefore("private TunnelRuntime startCore")
        val qualificationSection = manager.substringAfter(
            "public OutboundCheck verifyActiveDataPlane(Network vpnNetwork)"
        )
            .substringBefore("public synchronized void stop")

        assertFalse(startSection.contains("verifyActiveDataPlane"))
        assertTrue(manager.substringAfter("private TunnelRuntime startCore").contains("xrayEngine.start"))
        assertTrue(qualificationSection.contains("AndroidVpnDataPlaneProbe.verify(vpnNetwork)"))
        assertFalse(qualificationSection.contains("sshEngine.connectWithSocks"))
    }

    @Test
    fun javaServicePublishesConnectedOnlyAfterAndroidRegistersOwnedVpn() {
        val source = sourceFile("src/main/java/com/ghostnexora/vpn/service/GhostVpnService.java")
        val connectSection = source.substringAfter("private void handleConnect").substringBefore("private void handleDisconnect")
        val qualificationIndex = connectSection.indexOf("beginDataPlaneVerification(")
        val registrationIndex = connectSection.indexOf("awaitAndroidVpnRegistration(profile)")
        assertTrue(qualificationIndex >= 0)
        assertTrue(registrationIndex >= 0)
        assertTrue(registrationIndex < qualificationIndex)
        assertFalse(connectSection.contains("publishState(connected)"))

        val completionSection = source.substringAfter("private void completeDataPlaneVerification")
            .substringBefore("private void failDataPlaneVerification")
        assertTrue(completionSection.contains("claimDataPlaneVerification"))
        assertTrue(
            completionSection.indexOf("claimDataPlaneVerification") <
                completionSection.indexOf("publishState(connected)")
        )
        assertTrue(source.contains("NetworkCapabilities.TRANSPORT_VPN"))
        assertTrue(source.contains("capabilities.getOwnerUid() != Process.myUid()"))
        assertTrue(source.contains("\"10.20.0.2\".equals"))
        assertTrue(source.contains("route.isDefaultRoute()"))
        assertTrue(source.contains(".setConfigureIntent(PendingIntent.getActivity("))
        assertTrue(source.contains("builder.setUnderlyingNetworks(new Network[]{physicalNetwork})"))
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
    fun normalVpnSessionRunsOneQualificationAndKeepsPeriodicHealthPassive() {
        val managerSource = sourceFile("src/main/java/com/ghostnexora/vpn/tunnel/TunnelManager.java")
        assertFalse(managerSource.contains("healthCheckPort"))
        assertTrue(
            managerSource.contains("public OutboundCheck verifyActiveDataPlane(Network vpnNetwork)")
        )

        val probeSource = sourceFile("src/main/java/com/ghostnexora/vpn/tunnel/AndroidVpnDataPlaneProbe.java")
        assertTrue(probeSource.contains("vpnNetwork.bindSocket(transport)"))
        assertTrue(probeSource.contains("sendSingleRequest"))
        assertFalse(probeSource.contains("OutboundSocketProtection.protect"))
        assertFalse(probeSource.contains("for ("))

        val serviceSource = sourceFile("src/main/java/com/ghostnexora/vpn/service/GhostVpnService.java")
        assertFalse(serviceSource.contains("startInitialOutboundVerification"))
        assertFalse(serviceSource.contains("measureTcpLatency"))
        val healthMonitor = serviceSource.substringAfter("private void startHealthMonitor")
            .substringBefore("private void startStatsTicker")
        assertFalse(healthMonitor.contains("verifyActiveDataPlane"))
        assertTrue(serviceSource.contains("DATA_PLANE_VERIFICATION_TIMEOUT_MS"))
        assertTrue(serviceSource.contains("tunnelManager.verifyActiveDataPlane(verificationNetwork)"))
        assertTrue(serviceSource.contains("beginDataPlaneVerification(profile, tunnelRuntime, false, 0)"))
        val failureSection = serviceSource.substringAfter("private void failDataPlaneVerification")
            .substringBefore("private boolean isCurrentDataPlaneVerification")
        assertTrue(failureSection.contains("cleanupTunnel(true)"))
        assertTrue(failureSection.contains("repositoryBridge.setVpnDesiredConnected(false)"))
        assertTrue(failureSection.contains("[ROUTE-DATA-204]"))
        assertFalse(failureSection.contains("publishState(connected)"))

        val configSource = sourceFile("src/main/java/com/ghostnexora/vpn/tunnel/StableXrayConfigFactory.kt")
        assertFalse(configSource.contains("health-check"))
    }

    @Test
    fun normalConnectionDoesNotOpenAShellToReadVpsMotd() {
        val source = sourceFile("src/main/java/com/ghostnexora/vpn/tunnel/SshTunnelEngine.java")
        val connect = source.substringAfter("public Session connect")
            .substringBefore("public SshTunnelHandle connectWithSocks")
        assertFalse(source.contains("capturePostLoginMessage"))
        assertFalse(source.contains("ChannelShell"))
        assertFalse(connect.contains("openChannel(\"shell\")"))
        assertTrue(source.contains("showMessage(String message) { publishServerMessage(message); }"))
        assertTrue(source.contains("serverMessageShown.compareAndSet(false, true)"))
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
        assertTrue(onCreate.contains("TunnelLogEventParser.INSTANCE.parse(status)"))
        assertTrue(onCreate.contains("log(event.getLevel(), status"))
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
