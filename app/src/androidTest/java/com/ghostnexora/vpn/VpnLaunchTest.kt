package com.ghostnexora.vpn

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
        val profile = VpnProfile(name = "Emulator SSH fixture", host = "127.0.0.1", port = 22)
        runBlocking {
            repository.saveProfile(profile)
            repository.setActiveProfileId(profile.id)
            repository.setVpnDesiredConnected(true)
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.waitUntil(10_000) {
                    compose.onAllNodesWithText(profile.name).fetchSemanticsNodes().isNotEmpty()
                }
                compose.waitUntil(10_000) { runBlocking { !repository.isFirstLaunch.first() } }
                assertEquals(app.packageName, activeWindowPackage())
                assertNoVpnService()
                assertTrue(runBlocking { repository.vpnDesiredConnected.first() })

                compose.onNodeWithText("Conectar").performClick()
                compose.waitUntil(10_000) { activeWindowPackage() == "com.android.vpndialogs" }

                shell("input keyevent KEYCODE_BACK")
                compose.waitUntil(10_000) { activeWindowPackage() == app.packageName }
                assertNoVpnService()
            }
        } finally {
            runBlocking {
                repository.setVpnDesiredConnected(false)
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
