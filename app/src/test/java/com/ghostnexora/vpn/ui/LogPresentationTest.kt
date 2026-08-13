package com.ghostnexora.vpn.ui

import com.ghostnexora.vpn.data.model.LogEntry
import com.ghostnexora.vpn.data.model.LogLevel
import com.ghostnexora.vpn.ui.components.LogPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogPresentationTest {
    @Test
    fun extractsOnlyTheExplicitSshServerBanner() {
        val banner = LogPresentation.serverMessageBody(
            "[SSH] Mensaje del servidor · <b><font color=\"#fff\">Hola</font></b>"
        )

        assertEquals("<b><font color=\"#fff\">Hola</font></b>", banner)
        assertNull(LogPresentation.serverMessageBody("Welcome to Ubuntu 24.04 LTS"))
    }

    @Test
    fun sanitizesRichBannerAndKeepsSafeColors() {
        val html = LogPresentation.serverMessageHtml(
            "[SSH] Mensaje del servidor · " +
                "<b><font color=\"#00FFFF\">HEX</font></b><script>bad()</script>"
        )

        assertNotNull(html)
        assertTrue(html!!.contains("color=\"#00FFFF\""))
        assertTrue(html.contains(">HEX<"))
        assertFalse(html.contains("script", ignoreCase = true))
        assertFalse(html.contains("bad()"))
    }

    @Test
    fun defaultSummaryKeepsMilestonesBannerAndProblems() {
        val milestone = LogEntry(level = LogLevel.INFO, tag = "VPN", message = "Iniciando SSH + SSL")
        val banner = LogEntry(
            level = LogLevel.INFO,
            tag = "SSH",
            message = "[SSH] Mensaje del servidor · Bienvenido"
        )
        val verbose = LogEntry(
            level = LogLevel.INFO,
            tag = "SSH",
            message = "[SSH] INFO · server proposal: ciphers c2s"
        )
        val error = LogEntry(level = LogLevel.ERROR, tag = "VPN", message = "Falló")
        val qualified = LogEntry(
            level = LogLevel.INFO,
            tag = "NETWORK",
            message = "Ruta de datos bidireccional verificada · 123 ms"
        )

        assertTrue(LogPresentation.belongsToSummary(milestone))
        assertTrue(LogPresentation.belongsToSummary(banner))
        assertTrue(LogPresentation.belongsToSummary(error))
        assertTrue(LogPresentation.belongsToSummary(qualified))
        assertFalse(LogPresentation.belongsToSummary(verbose))
    }

    @Test
    fun recognizesTheLegacyUbuntuMotdWithoutHidingNormalBanners() {
        val motd = "[SSH] Mensaje del servidor · Welcome to Ubuntu 24.04 LTS " +
            "System information as of today Usage of /: 12% System restart required"

        assertTrue(LogPresentation.isLegacyVpsMotd(motd))
        assertFalse(
            LogPresentation.isLegacyVpsMotd(
                "[SSH] Mensaje del servidor · <b>Bienvenido a mi servidor</b>"
            )
        )
    }
}
