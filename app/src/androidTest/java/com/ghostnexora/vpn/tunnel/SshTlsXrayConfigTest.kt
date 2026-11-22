package com.ghostnexora.vpn.tunnel

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ghostnexora.vpn.data.model.ConnectionMode
import com.ghostnexora.vpn.data.model.VpnProfile
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
