package com.ghostnexora.vpn

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ghostnexora.vpn.data.model.VpnProfile
import com.ghostnexora.vpn.security.AppManagedConfigKeyProvider
import com.ghostnexora.vpn.security.DeviceBoundConfigCodec
import com.ghostnexora.vpn.security.Gnx3ProtectionMode
import com.ghostnexora.vpn.security.LockedProfileVault
import com.ghostnexora.vpn.util.BundleExportOptions
import com.ghostnexora.vpn.util.BundleProtection
import com.ghostnexora.vpn.util.ImportResult
import com.ghostnexora.vpn.util.IndividualExportOptions
import com.ghostnexora.vpn.util.JsonManager
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileBundleExportTest {
    private fun assertMultilayerGnx4(bytes: ByteArray) {
        assertEquals("GNX4GNX-MLKEM768-X25", bytes.copyOfRange(0, 20).toString(Charsets.US_ASCII))
    }

    @Test
    fun roundTripsSingleAndMultipleEncryptedProfilesWithOptionalPassword() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = JsonManager(context, AppManagedConfigKeyProvider(context), LockedProfileVault(), DeviceBoundConfigCodec())
        val profiles = listOf(
            VpnProfile(id = "first", name = "First", host = "first.example", port = 22),
            VpnProfile(id = "second", name = "Second", host = "second.example", port = 443)
        )
        val file = File.createTempFile("bundle", ".gnx", context.cacheDir)
        val uri = Uri.fromFile(file)
        val password = "fixture-password-123".toCharArray()
        try {
            for (selection in listOf(profiles.take(1), profiles)) {
                assertTrue(manager.exportToUri(uri, selection, BundleExportOptions(BundleProtection.APP_MANAGED)))
                val exported = file.readBytes()
                assertMultilayerGnx4(exported)
                assertFalse(exported.toString(Charsets.ISO_8859_1).contains("first.example"))
                val imported = manager.importFromUri(uri) as ImportResult.Success
                assertEquals(selection.map { it.host }, imported.profiles.map { it.host })
                assertTrue(imported.profiles.none { profile -> selection.any { it.id == profile.id } })
            }

            assertTrue(manager.exportToUri(uri, profiles, BundleExportOptions(BundleProtection.PASSWORD, password)))
            val protectedFile = file.readBytes()
            assertMultilayerGnx4(protectedFile)
            assertTrue(manager.importFromUri(uri) is ImportResult.PasswordRequired)
            assertTrue(manager.importFromUri(uri, "wrong-password".toCharArray()) is ImportResult.Error)
            val protected = manager.importFromUri(uri, password) as ImportResult.Success
            assertEquals(profiles.map { it.host }, protected.profiles.map { it.host })

            // Change each KEM component, the outer tag, and the encrypted payload.
            for (offset in listOf(56, 1144, 1176, protectedFile.lastIndex)) {
                val altered = protectedFile.copyOf()
                altered[offset] = (altered[offset].toInt() xor 1).toByte()
                file.writeBytes(altered)
                assertTrue(manager.importFromUri(uri, password) is ImportResult.Error)
            }

            val deviceBoundExport = runCatching {
                manager.exportToUri(uri, profiles, BundleExportOptions(BundleProtection.DEVICE_BOUND))
            }
            val deviceBoundError = deviceBoundExport.exceptionOrNull()
            if (deviceBoundError == null) {
                assertTrue(deviceBoundExport.getOrThrow())
                assertMultilayerGnx4(file.readBytes())
                val deviceBound = manager.importFromUri(uri) as ImportResult.Success
                assertEquals(profiles.map { it.host }, deviceBound.profiles.map { it.host })
            } else {
                assertEquals("Android Keystore hardware is required for this backup", deviceBoundError.message)
            }

            val single = manager.exportIndividualToCache(
                profiles.first(),
                IndividualExportOptions(
                    locked = false, noteHtml = "", protectionMode = Gnx3ProtectionMode.APP_MANAGED
                )
            )
            try {
                assertMultilayerGnx4(single.readBytes())
                val imported = manager.importFromUri(Uri.fromFile(single)) as ImportResult.Success
                assertEquals(profiles.first().host, imported.profiles.single().host)
            } finally {
                single.delete()
            }
        } finally {
            password.fill('\u0000')
            file.delete()
        }
    }
}
