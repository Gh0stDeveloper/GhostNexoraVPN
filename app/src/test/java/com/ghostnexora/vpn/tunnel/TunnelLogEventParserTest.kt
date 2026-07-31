package com.ghostnexora.vpn.tunnel

import com.ghostnexora.vpn.data.model.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelLogEventParserTest {
    @Test
    fun mapsRealTransportStagesToVisibleLogCategories() {
        val ssh = requireNotNull(
            TunnelLogEventParser.parse("[SSH] Autenticación completada · sesión cifrada activa")
        )
        val network = requireNotNull(
            TunnelLogEventParser.parse("[NETWORK] Abriendo socket TCP · vpn.example.com:443")
        )
        val core = requireNotNull(
            TunnelLogEventParser.parse("[XRAY] Configuración TUN preparada")
        )
        val socks = requireNotNull(
            TunnelLogEventParser.parse(
                "[SOCKS] Canal direct-tcpip verificado · datos reenviados por SSH"
            )
        )

        assertEquals(LogLevel.SUCCESS, ssh.level)
        assertEquals("SSH", ssh.tag)
        assertEquals(LogLevel.SUCCESS, socks.level)
        assertEquals("SOCKS", socks.tag)
        assertEquals(LogLevel.INFO, network.level)
        assertEquals("NETWORK", network.tag)
        assertEquals("CORE", core.tag)
    }

    @Test
    fun keepsFailuresVisibleWithoutExposingArbitraryDatabaseTags() {
        val warning = requireNotNull(
            TunnelLogEventParser.parse("[NETWORK] WARN · prueba 1 falló · timeout")
        )
        val error = requireNotNull(
            TunnelLogEventParser.parse("[ERROR] Cadena SSH/TUN detenida · auth fail")
        )
        val unknown = requireNotNull(
            TunnelLogEventParser.parse("[UNTRUSTED] texto del core")
        )

        assertEquals(LogLevel.WARNING, warning.level)
        assertEquals(LogLevel.ERROR, error.level)
        assertEquals("SSH", error.tag)
        assertEquals("CORE", unknown.tag)
    }

    @Test
    fun normalizesMultilineNativeMessagesAndBoundsTheirSize() {
        val event = requireNotNull(
            TunnelLogEventParser.parse("[CORE] línea uno\n${"x".repeat(2_000)}")
        )

        assertTrue('\n' !in event.message)
        assertTrue(event.message.length <= 1_024)
        assertNull(TunnelLogEventParser.parse(" \n "))
    }
}
