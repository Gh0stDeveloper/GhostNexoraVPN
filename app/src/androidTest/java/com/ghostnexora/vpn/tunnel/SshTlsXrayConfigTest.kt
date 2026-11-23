package com.ghostnexora.vpn.tunnel

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ghostnexora.vpn.data.model.ConnectionMode
import com.ghostnexora.vpn.data.model.VpnProfile
import com.ghostnexora.vpn.data.model.ShadowsocksCipher
import java.util.Base64
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SshTlsXrayConfigTest {
    @Test
    fun nativeCoreStartsWithGeneratedSshTlsConfiguration() {
        val profile = VpnProfile(
            name = "SSH + SSL startup fixture",
            host = "ssh.example.invalid",
            connectionMode = ConnectionMode.SSL_SNI.id,
            sslEnabled = true,
            sni = "ssh.example.invalid"
        )
        val config = StableXrayConfigFactory.build(profile, sshSocksPort = 10808)
        assertNativeCoreStarts(config)
    }

    @Test
    fun nativeCoreStartsWithEachShadowsocksCipher() {
        for (cipher in ShadowsocksCipher.entries) {
            val password = if (cipher.id.startsWith("2022-")) {
                val size = if (cipher == ShadowsocksCipher.AES_128_GCM_2022) 16 else 32
                Base64.getEncoder().encodeToString(ByteArray(size) { 7 })
            } else {
                "test-startup-fixture"
            }
            val profile = VpnProfile(
                name = "Shadowsocks startup fixture",
                host = "ss.example.invalid",
                port = 8388,
                connectionMode = ConnectionMode.SHADOWSOCKS.id,
                payload = cipher.id,
                password = password
            )
            assertNativeCoreStarts(StableXrayConfigFactory.build(profile))
        }
    }

    private fun assertNativeCoreStarts(config: String) {
        val engine = XrayCoreEngine(InstrumentationRegistry.getInstrumentation().targetContext)
        val (coreFd, peerFd) = ParcelFileDescriptor.createSocketPair()
        try {
            coreFd.use { descriptor ->
                peerFd.use {
                    engine.start(config, descriptor.fd)
                    assertTrue(engine.isRunning)
                }
            }
        } finally {
            engine.stop()
        }
    }
}
