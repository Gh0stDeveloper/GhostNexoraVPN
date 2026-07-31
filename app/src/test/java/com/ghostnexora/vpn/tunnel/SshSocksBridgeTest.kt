package com.ghostnexora.vpn.tunnel

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream

class SshSocksBridgeTest {
    @Test
    fun flushesEveryChunkBeforeTheClientClosesItsInput() {
        val payload = "GET /generate_204 HTTP/1.1\r\nHost: example.com\r\n\r\n"
            .toByteArray()
        val output = FlushCountingOutputStream()
        var firstFlushSignals = 0

        val copied = copyToSshChannel(
            input = ByteArrayInputStream(payload),
            output = output,
            bufferSize = 7,
            onFirstFlush = { firstFlushSignals += 1 }
        )

        assertEquals(payload.size.toLong(), copied)
        assertArrayEquals(payload, output.toByteArray())
        assertEquals((payload.size + 6) / 7, output.flushCount)
        assertEquals(1, firstFlushSignals)
    }

    @Test
    fun halfClosesOnlyTheSshUplinkAfterClientEof() {
        val payload = "request".toByteArray()
        val output = CloseTrackingOutputStream()

        val copied = copyClientToSshAndHalfClose(
            input = ByteArrayInputStream(payload),
            output = output,
            bufferSize = 3
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

        val copied = copyFromSshChannel(
            input = ByteArrayInputStream(response),
            output = output,
            bufferSize = 5,
            onFirstFlush = { firstDownlinkSignals += 1 }
        )

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

        override fun write(value: Int) {
            bytes.write(value)
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            bytes.write(buffer, offset, length)
        }

        override fun close() {
            closeCount += 1
        }
    }
}
