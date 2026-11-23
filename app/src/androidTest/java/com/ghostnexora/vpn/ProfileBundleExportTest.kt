package com.ghostnexora.vpn

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ghostnexora.vpn.data.model.VpnProfile
import com.ghostnexora.vpn.security.AppManagedConfigKeyProvider
import com.ghostnexora.vpn.security.DeviceBoundConfigCodec
import com.ghostnexora.vpn.security.LockedProfileVault
import com.ghostnexora.vpn.util.BundleExportOptions
import com.ghostnexora.vpn.util.BundleProtection
import com.ghostnexora.vpn.util.ImportResult
import com.ghostnexora.vpn.util.JsonManager
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileBundleExportTest {
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
                assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains("first.example"))
                val imported = manager.importFromUri(uri) as ImportResult.Success
                assertEquals(selection.map { it.host }, imported.profiles.map { it.host })
                assertTrue(imported.profiles.none { profile -> selection.any { it.id == profile.id } })
            }

            assertTrue(manager.exportToUri(uri, profiles, BundleExportOptions(BundleProtection.PASSWORD, password)))
            assertTrue(manager.importFromUri(uri) is ImportResult.PasswordRequired)
            assertTrue(manager.importFromUri(uri, "wrong-password".toCharArray()) is ImportResult.Error)
            val protected = manager.importFromUri(uri, password) as ImportResult.Success
            assertEquals(profiles.map { it.host }, protected.profiles.map { it.host })
        } finally {
            password.fill('\u0000')
            file.delete()
        }
    }
}
