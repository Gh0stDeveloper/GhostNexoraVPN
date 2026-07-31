package com.ghostnexora.vpn.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun removesRemoteCssAndOverlayProperties() {
        val sanitized = HtmlNoteSanitizer.sanitize(
            """
            <style>
              @import url(https://evil.example/a.css);
              .x { background-image: url(https://evil.example/pixel);
                   position: fixed; z-index: 999; color: red; }
            </style>
            <p style="behavior:url(x); color: blue">Seguro</p>
            """.trimIndent()
        )

        assertFalse(sanitized.contains("@import", ignoreCase = true))
        assertFalse(sanitized.contains("url(", ignoreCase = true))
        assertFalse(sanitized.contains("position:", ignoreCase = true))
        assertFalse(sanitized.contains("z-index", ignoreCase = true))
        assertFalse(sanitized.contains("behavior", ignoreCase = true))
        assertTrue(sanitized.contains("color: red"))
        assertTrue(sanitized.contains("color: blue"))
        assertTrue(sanitized.contains("Seguro"))
    }
}
