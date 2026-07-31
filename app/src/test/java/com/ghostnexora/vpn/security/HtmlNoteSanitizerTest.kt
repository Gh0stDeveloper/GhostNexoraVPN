package com.ghostnexora.vpn.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HtmlNoteSanitizerTest {
    @Test
    fun removesExecutableElementsEventsAndActiveLinks() {
        val sanitized = HtmlNoteSanitizer.sanitize(
            """
            <div onclick="steal()">
              Contacto
              <script>alert(document.cookie)</script>
              <iframe src="https://evil.example"></iframe>
              <a href="javascript:alert(1)" onmouseover="steal()">abrir</a>
            </div>
            """.trimIndent()
        )

        assertTrue(sanitized.contains("Contacto"))
        assertFalse(sanitized.contains("<script", ignoreCase = true))
        assertFalse(sanitized.contains("<iframe", ignoreCase = true))
        assertFalse(sanitized.contains("onclick", ignoreCase = true))
        assertFalse(sanitized.contains("onmouseover", ignoreCase = true))
        assertFalse(sanitized.contains("javascript:", ignoreCase = true))
        assertTrue(sanitized.contains("href=\"#\""))
    }

    @Test
    fun keepsSafeFormattingAndContactLinks() {
        val sanitized = HtmlNoteSanitizer.sanitize(
            """
            <style>.card { color: #00ffee; background: #102126; }</style>
            <div class="card"><b>Servidor</b>
              <a href="https://t.me/example">Telegram</a>
            </div>
            """.trimIndent()
        )

        assertTrue(sanitized.contains("color: #00ffee"))
        assertTrue(sanitized.contains("<b>Servidor</b>"))
        assertTrue(sanitized.contains("https://t.me/example"))
        assertTrue(sanitized.contains("noopener"))
    }

    @Test
    fun keepsModernPresentationCssAndLocalAnimations() {
        val sanitized = HtmlNoteSanitizer.sanitize(
            """
            <style>
              @media (max-width: 500px) {
                .card { display: grid; grid-template-columns: 1fr 1fr;
                        background: linear-gradient(90deg, #111, #333);
                        transform: scale(0.98); animation: pulse 2s infinite; }
              }
              @keyframes pulse {
                from { opacity: .7; }
                to { opacity: 1; }
              }
            </style>
            <div class="card">Estilo</div>
            """.trimIndent()
        )

        assertTrue(sanitized.contains("@media"))
        assertTrue(sanitized.contains("@keyframes"))
        assertTrue(sanitized.contains("display: grid"))
        assertTrue(sanitized.contains("linear-gradient"))
        assertTrue(sanitized.contains("animation: pulse"))
        assertTrue(sanitized.contains("transform: scale"))
    }

    @Test
    fun supportsLegacyInjectorFormattingAndEmbeddedImages() {
        val sanitized = HtmlNoteSanitizer.sanitize(
            """
            <font color="#00ffee" face="sans-serif" size="4">Contacto</font>
            <marquee direction="left" behavior="alternate" scrollamount="3">Servidor activo</marquee>
            <table border="1" cellpadding="4" bgcolor="#101820"><tr><td>Dato</td></tr></table>
            <img alt="logo" width="120px" src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAAB">
            """.trimIndent()
        )

        assertTrue(sanitized.contains("<font"))
        assertTrue(sanitized.contains("color=\"#00ffee\""))
        assertTrue(sanitized.contains("<marquee"))
        assertTrue(sanitized.contains("scrollamount=\"3\""))
        assertTrue(sanitized.contains("cellpadding=\"4\""))
        assertTrue(sanitized.contains("data:image/png;base64"))
        assertTrue(sanitized.contains("loading=\"lazy\""))
    }

    @Test
    fun removesRemoteCssAndOverlayProperties() {
        val sanitized = HtmlNoteSanitizer.sanitize(
            """
            <style>
              @import url(https://evil.example/a.css);
              @font-face { font-family: remote; src: url(https://evil.example/font); }
              .x { background-image: url(https://evil.example/pixel);
                   position: fixed; z-index: 999; color: red; }
            </style>
            <p style="behavior:url(x); color: blue">Seguro</p>
            """.trimIndent()
        )

        assertFalse(sanitized.contains("@import", ignoreCase = true))
        assertFalse(sanitized.contains("@font-face", ignoreCase = true))
        assertFalse(sanitized.contains("url(", ignoreCase = true))
        assertFalse(sanitized.contains("position:", ignoreCase = true))
        assertFalse(sanitized.contains("z-index", ignoreCase = true))
        assertFalse(sanitized.contains("behavior", ignoreCase = true))
        assertTrue(sanitized.contains("color: red"))
        assertTrue(sanitized.contains("color: blue"))
        assertTrue(sanitized.contains("Seguro"))
    }

    @Test
    fun sourceDoesNotContainTheAndroidIcuBracePatternThatCrashedRelease() {
        val candidates = listOf(
            File("src/main/java/com/ghostnexora/vpn/security/HtmlNoteSanitizer.kt"),
            File("app/src/main/java/com/ghostnexora/vpn/security/HtmlNoteSanitizer.kt"),
            File("../app/src/main/java/com/ghostnexora/vpn/security/HtmlNoteSanitizer.kt")
        )
        val source = candidates.firstOrNull(File::isFile)?.readText()
            ?: error("HtmlNoteSanitizer.kt not found")

        assertFalse(source.contains("(?:;|\\{.*?})"))
        assertFalse(source.contains("@[^;{]+(?:;|\\{.*?})"))
        assertTrue(source.contains("compileRegex"))
    }
}
