package com.ghostnexora.vpn.tunnel

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

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

    private class FlushCountingOutputStream : ByteArrayOutputStream() {
        var flushCount: Int = 0
            private set

        override fun flush() {
            flushCount += 1
            super.flush()
        }
    }
}
