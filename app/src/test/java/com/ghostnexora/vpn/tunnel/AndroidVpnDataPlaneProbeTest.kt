package com.ghostnexora.vpn.tunnel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class AndroidVpnDataPlaneProbeTest {
    @Test
    fun acceptsAValidHttpResponseAsBidirectionalEvidence() {
        val response = "HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n"

        assertEquals(
            204,
            AndroidVpnDataPlaneProbe.readHttpStatus(
                ByteArrayInputStream(response.toByteArray(Charsets.US_ASCII))
            )
        )
    }

    @Test
    fun rejectsNonHttpData() {
        val response = "SSH-2.0-OpenSSH_9.6\r\n"

        assertThrows(IOException::class.java) {
            AndroidVpnDataPlaneProbe.readHttpStatus(
                ByteArrayInputStream(response.toByteArray(Charsets.US_ASCII))
            )
        }
    }
}
