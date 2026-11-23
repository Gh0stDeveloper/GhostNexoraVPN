package com.ghostnexora.vpn

import android.accessibilityservice.AccessibilityServiceInfo
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ghostnexora.vpn.data.model.VpnProfile
import com.ghostnexora.vpn.ui.MainActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VpnLaunchTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val uiAutomation = instrumentation.uiAutomation

    @Test
    fun coldLaunchStaysIdleAndConnectRequestsVpnConsent() {
        val app = instrumentation.targetContext.applicationContext as GhostNexoraApp
        val repository = app.repository
        val plainNote = "Soporte de prueba & red\nLínea 2 < 3"
        val profile = VpnProfile(
            name = "Emulator SSH fixture",
            host = "127.0.0.1",
            port = 22,
            noteHtml = plainNote
        )
        runBlocking {
            repository.saveProfile(profile)
            repository.setActiveProfileId(profile.id)
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { activity ->
                compose.waitUntil(10_000) {
                    compose.onAllNodesWithText(profile.name).fetchSemanticsNodes().isNotEmpty()
                }
                compose.waitUntil(10_000) { runBlocking { !repository.isFirstLaunch.first() } }
                assertEquals(app.packageName, activeWindowPackage())
                assertNoVpnService()
                activity.moveToState(Lifecycle.State.CREATED)
                activity.moveToState(Lifecycle.State.RESUMED)
                assertNoVpnService()

                compose.onNodeWithText(plainNote).performScrollTo().assertIsDisplayed()
                compose.onNodeWithTag("profile-note-html").assertDoesNotExist()

                runBlocking { repository.updateProfile(profile.copy(noteHtml = "<p><strong>Soporte HTML</strong></p>")) }
                compose.waitUntil(10_000) {
                    compose.onAllNodes(hasTestTag("profile-note-html"))
                        .fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithTag("profile-note-html").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText(profile.name).performScrollTo().assertIsDisplayed()

                compose.onNodeWithContentDescription(app.getString(R.string.vpn_dns_enabled))
                    .performScrollTo().performClick()
                compose.waitUntil(10_000) { runBlocking { !repository.networkPreferences.first().vpnDnsEnabled } }

                val expandLogs = app.getString(R.string.dashboard_logs_expand)
                compose.onNodeWithTag("dashboard-log-tab").performSemanticsAction(SemanticsActions.OnClick)
                compose.waitUntil(10_000) {
                    compose.onAllNodes(hasContentDescription(expandLogs)).fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithContentDescription(expandLogs).performClick()
                compose.onNodeWithContentDescription(app.getString(R.string.dashboard_logs_shrink)).assertIsDisplayed()
                compose.onNodeWithContentDescription(app.getString(R.string.dashboard_logs_shrink)).performClick()
                compose.onNodeWithTag("dashboard-home-tab").performSemanticsAction(SemanticsActions.OnClick)

                val connect = app.getString(R.string.btn_connect)
                compose.waitUntil(10_000) {
                    compose.onAllNodes(hasText(connect)).fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText(connect).performScrollTo().performClick()
                compose.waitUntil(10_000) { activeWindowPackage() == "com.android.vpndialogs" }

                val serviceInfo = uiAutomation.serviceInfo
                serviceInfo.flags = serviceInfo.flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                uiAutomation.serviceInfo = serviceInfo
                val cancel = uiAutomation.rootInActiveWindow
                    ?.findAccessibilityNodeInfosByViewId("android:id/button2")
                    ?.singleOrNull()
                assertTrue("The system VPN dialog must allow Cancel",
                    cancel?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
                compose.waitUntil(10_000) {
                    compose.onAllNodesWithText("Permiso VPN requerido para conectar")
                        .fetchSemanticsNodes().isNotEmpty()
                }
                assertNoVpnService()
            }
        } finally {
            runBlocking {
                repository.setVpnDnsEnabled(true)
                repository.setActiveProfileId("")
                repository.deleteProfile(profile)
            }
        }
    }

    private fun activeWindowPackage(): String? =
        uiAutomation.rootInActiveWindow?.packageName?.toString()

    private fun assertNoVpnService() {
        val services = shell("dumpsys activity services com.ghostnexora.vpn")
        assertFalse(services, services.contains(".service.GhostVpnService"))
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand(command))
            .bufferedReader().use { it.readText() }
}
