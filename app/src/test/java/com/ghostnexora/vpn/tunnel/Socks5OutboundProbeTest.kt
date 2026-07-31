package com.ghostnexora.vpn.tunnel

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class Socks5OutboundProbeTest {
    @Test
    fun negotiatesNoAuthenticationAndBuildsIpv4ConnectRequest() {
        val greetingOutput = ByteArrayOutputStream()
        Socks5OutboundProbe.negotiateNoAuthentication(
            ByteArrayInputStream(byteArrayOf(0x05, 0x00)),
            greetingOutput
        )
        assertArrayEquals(byteArrayOf(0x05, 0x01, 0x00), greetingOutput.toByteArray())

        val connectOutput = ByteArrayOutputStream()
        Socks5OutboundProbe.requestIpv4Connect(
            input = ByteArrayInputStream(
                byteArrayOf(0x05, 0x00, 0x00, 0x01, 127, 0, 0, 1, 0x2A, 0x2A)
            ),
            output = connectOutput,
            address = byteArrayOf(1, 1, 1, 1),
            port = 443
        )
        assertArrayEquals(
            byteArrayOf(0x05, 0x01, 0x00, 0x01, 1, 1, 1, 1, 0x01, 0xBB.toByte()),
            connectOutput.toByteArray()
        )
    }

    @Test
    fun consumesVariableLengthDomainReply() {
        val domain = "localhost".toByteArray()
        val response = ByteArrayOutputStream().apply {
            write(byteArrayOf(0x05, 0x00, 0x00, 0x03, domain.size.toByte()))
            write(domain)
            write(byteArrayOf(0x1F, 0x90.toByte()))
        }.toByteArray()

        val input = ByteArrayInputStream(response)
        Socks5OutboundProbe.readConnectReply(input)

        assertEquals(0, input.available())
    }

    @Test
    fun preservesConcreteSocksFailureCode() {
        val error = runCatching {
            Socks5OutboundProbe.readConnectReply(
                ByteArrayInputStream(byteArrayOf(0x05, 0x05, 0x00, 0x01))
            )
        }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("connection refused"))
    }
}
