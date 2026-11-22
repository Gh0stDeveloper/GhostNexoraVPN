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
            "public OutboundCheck verifyActiveDataPlane()"
        )
            .substringBefore("public synchronized void stop")

        assertFalse(startSection.contains("verifyActiveDataPlane"))
        assertTrue(manager.substringAfter("private TunnelRuntime startCore").contains("xrayEngine.start"))
        assertTrue(qualificationSection.contains("xrayEngine.verifyActiveOutbound()"))
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
            managerSource.contains("public OutboundCheck verifyActiveDataPlane()")
        )

        val engine = sourceFile("src/main/java/com/ghostnexora/vpn/tunnel/XrayCoreEngine.java")
        val qualification = engine.substringAfter("OutboundCheck verifyActiveOutbound(String endpoint)")
            .substringBefore("public synchronized void start")
        assertTrue(qualification.contains("current.measureDelay(endpoint)"))
        assertFalse(qualification.contains("bindSocket"))
        assertFalse(qualification.contains("measureOutboundDelay"))

        val serviceSource = sourceFile("src/main/java/com/ghostnexora/vpn/service/GhostVpnService.java")
        assertFalse(serviceSource.contains("startInitialOutboundVerification"))
        assertFalse(serviceSource.contains("measureTcpLatency"))
        val healthMonitor = serviceSource.substringAfter("private void startHealthMonitor")
            .substringBefore("private void startStatsTicker")
        assertFalse(healthMonitor.contains("verifyActiveDataPlane"))
        assertTrue(serviceSource.contains("DATA_PLANE_VERIFICATION_TIMEOUT_MS"))
        assertTrue(serviceSource.contains("tunnelManager.verifyActiveDataPlane()"))
        assertTrue(serviceSource.contains("beginDataPlaneVerification(profile, tunnelRuntime, false, 0)"))
        val failureSection = serviceSource.substringAfter("private void failDataPlaneVerification")
            .substringBefore("private boolean isCurrentDataPlaneVerification")
        assertTrue(failureSection.contains("cleanupTunnel()"))
        assertTrue(failureSection.contains("stopAfterCommand()"))
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

    @Test
    fun closingAnOlderCommandCannotStopANewerVpnStart() {
        val service = sourceFile("src/main/java/com/ghostnexora/vpn/service/GhostVpnService.java")
        val command = service.substringAfter("public int onStartCommand")
            .substringBefore("public void onRevoke")
        val stop = service.substringAfter("private void stopAfterCommand()")
            .substringBefore("private void submit(")
        val disconnect = service.substringAfter("private void handleDisconnect()")
            .substringBefore("private void triggerReconnect(")

        assertTrue(command.contains("commandStartId = startId;"))
        assertTrue(disconnect.contains("cleanupTunnel()"))
        assertTrue(disconnect.indexOf("cleanupTunnel()") < disconnect.indexOf("stopAfterCommand()"))
        assertFalse(service.contains("stopSelf();"))
        assertTrue(stop.contains("int finishedStartId = commandStartId;"))
        assertTrue(stop.contains("mainHandler.post("))
        assertTrue(stop.contains("if (!destroyed && stopSelfResult(finishedStartId))"))
        assertTrue(stop.indexOf("stopSelfResult(") < stop.indexOf("stopForeground("))
    }

    @Test
    fun runtimeStopsWithoutRestartingWhenAndroidEndsTheService() {
        val service = sourceFile("src/main/java/com/ghostnexora/vpn/service/GhostVpnService.java")
        val overlay = sourceFile("src/main/java/com/ghostnexora/vpn/service/FloatingWindowService.kt")
        val manifest = sourceFile("src/main/AndroidManifest.xml")
        val dashboard = sourceFile("src/main/java/com/ghostnexora/vpn/ui/screens/dashboard/DashboardViewModel.kt")

        assertTrue(service.contains("return START_NOT_STICKY"))
        assertTrue(overlay.contains("return START_NOT_STICKY"))
        assertFalse(service.contains("handleSystemRestart"))
        assertFalse(manifest.contains("android.intent.action.BOOT_COMPLETED"))
        assertTrue(manifest.contains("android:stopWithTask=\"false\""))
        assertTrue(manifest.contains("android.net.VpnService.SUPPORTS_ALWAYS_ON"))
        assertTrue(dashboard.contains("lifetime.linkToDeath"))
    }

    private fun sourceFile(relativePath: String): String {
        val candidates = listOf(File(relativePath), File("app/$relativePath"), File("../app/$relativePath"))
        val file = candidates.firstOrNull(File::isFile) ?: error("Unable to locate source file: $relativePath")
        return file.readText()
    }
}
