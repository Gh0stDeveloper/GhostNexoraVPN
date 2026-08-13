package com.ghostnexora.vpn.tunnel

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream

class SshSocksBridgeTest {
    @Test
    fun forwardsAndSanitizesTheSshServerMessage() {
        val statuses = mutableListOf<String>()
        val userInfo = ProfileUserInfo("secret") { message ->
            statuses += message
            kotlin.Unit
        }

        assertFalse(userInfo.hasServerMessage())
        userInfo.showMessage("\u001B[32mBienvenido\u001B[0m\r\nGhost VPS\u0000")

        assertTrue(userInfo.hasServerMessage())
        assertEquals(
            listOf("[SSH] Mensaje del servidor · Bienvenido Ghost VPS"),
            statuses
        )
    }

    @Test
    fun keepsLongServerMessagesBeyondTheOldLogPreviewLimit() {
        val longMessage = "M".repeat(4_096)
        assertEquals(longMessage, SshTunnelEngine.normalizeServerMessage(longMessage))
    }

    @Test
    fun flushesEveryChunkBeforeTheClientClosesItsInput() {
        val payload = "GET /generate_204 HTTP/1.1\r\nHost: example.com\r\n\r\n".toByteArray()
        val output = FlushCountingOutputStream()
        var firstFlushSignals = 0
        val copied = SshIoBridge.copyToSshChannel(
            ByteArrayInputStream(payload), output, 7
        ) { firstFlushSignals += 1 }
        assertEquals(payload.size.toLong(), copied)
        assertArrayEquals(payload, output.toByteArray())
        assertEquals((payload.size + 6) / 7, output.flushCount)
        assertEquals(1, firstFlushSignals)
    }

    @Test
    fun halfClosesOnlyTheSshUplinkAfterClientEof() {
        val payload = "request".toByteArray()
        val output = CloseTrackingOutputStream()
        val copied = SshIoBridge.copyClientToSshAndHalfClose(
            ByteArrayInputStream(payload), output, 3, null
        )
        assertEquals(payload.size.toLong(), copied)
        assertArrayEquals(payload, output.bytes.toByteArray())
        assertEquals(1, output.closeCount)
    }

    @Test
    fun flushesRemoteResponseBlocksBackToTheSocksClient() {
        val response = "HTTP/1.1 204 No Content\r\n\r\n".toByteArray()
        val output = FlushCountingOutputStream()
        var firstDownlinkSignals = 0
        val copied = SshIoBridge.copyFromSshChannel(
            ByteArrayInputStream(response), output, 5
        ) { firstDownlinkSignals += 1 }
        assertEquals(response.size.toLong(), copied)
        assertArrayEquals(response, output.toByteArray())
        assertEquals((response.size + 4) / 5, output.flushCount)
        assertEquals(1, firstDownlinkSignals)
    }

    private class FlushCountingOutputStream : ByteArrayOutputStream() {
        var flushCount: Int = 0
            private set
        override fun flush() {
            flushCount += 1
            super.flush()
        }
    }

    private class CloseTrackingOutputStream : OutputStream() {
        val bytes = ByteArrayOutputStream()
        var closeCount: Int = 0
            private set
        override fun write(value: Int) { bytes.write(value) }
        override fun write(buffer: ByteArray, offset: Int, length: Int) { bytes.write(buffer, offset, length) }
        override fun close() { closeCount += 1 }
    }
}
