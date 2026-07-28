package com.ghostnexora.vpn.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PayloadEngineTest {
    @Test
    fun rendersVariablesSegmentsAndDelaysDeterministically() {
        val plan = PayloadEngine.compile(
            raw = "CONNECT [host_port] HTTP/1.1[crlf]Host: [sni][crlf][crlf][split][delay=250]X-[random]",
            context = PayloadContext(
                host = "vpn.example.com",
                port = 443,
                sni = "cdn.example.com",
                proxyHost = "proxy.example.com",
                proxyPort = 8080
            ),
            deterministicSeed = 7L
        )

        assertEquals(2, plan.segmentCount)
        assertEquals(250L, plan.totalDelayMs)
        assertTrue(plan.rendered.startsWith("CONNECT vpn.example.com:443 HTTP/1.1\r\nHost: cdn.example.com"))
        assertTrue(plan.actions.any { it is PayloadAction.Delay && it.millis == 250L })
        assertFalse(plan.rendered.contains("[random]"))
    }

    @Test
    fun visiblePreviewDoesNotDoubleEncodeCrLf() {
        assertEquals("A␍␊\nB␊\nC␍D", PayloadEngine.toVisiblePreview("A\r\nB\nC\rD"))
    }

    @Test
    fun rejectsUnknownVariablesAndUnsafeDelay() {
        val validation = PayloadEngine.validate("GET /[crlf][unknown][delay=9000]")
        assertFalse(validation.isValid)
        assertTrue(validation.errors.any { it.contains("Variables no reconocidas") })
        assertTrue(validation.errors.any { it.contains("retardo", ignoreCase = true) })
    }

    @Test
    fun templatesProduceValidPayloads() {
        PayloadTemplate.entries.forEach { template ->
            assertTrue("Template ${template.name}", PayloadEngine.validate(PayloadEngine.template(template)).isValid)
        }
    }
}