package com.ghostnexora.vpn.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogSanitizerTest {
    @Test
    fun redactsKeyValueSecrets() {
        val result = LogSanitizer.sanitize("password=my-secret auth:token-value host=vpn.example.com")
        assertFalse(result.contains("my-secret"))
        assertFalse(result.contains("token-value"))
        assertTrue(result.contains("[OCULTO]"))
    }

    @Test
    fun redactsUriCredentials() {
        val result = LogSanitizer.sanitize("Conectando a trojan://super-secret@vpn.example.com:443")
        assertFalse(result.contains("super-secret"))
        assertTrue(result.contains("trojan://***@"))
    }

    @Test
    fun redactsAuthorizationHeaders() {
        val result = LogSanitizer.sanitize("Authorization: Bearer abcdefghijklmnopqrstuvwxyz123456")
        assertFalse(result.contains("abcdefghijklmnopqrstuvwxyz123456"))
    }
}
