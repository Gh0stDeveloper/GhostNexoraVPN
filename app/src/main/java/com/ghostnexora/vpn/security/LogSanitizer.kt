package com.ghostnexora.vpn.security

/**
 * Redacta datos sensibles antes de persistir o copiar registros.
 *
 * La aplicación nunca debe escribir contraseñas, tokens, cabeceras de
 * autorización o credenciales embebidas en URI dentro de Room/logcat/UI.
 */
object LogSanitizer {
    private val keyValueSecret = Regex(
        pattern = "(?i)(password|passwd|pass|auth|token|secret|authorization|proxy-authorization|api[_-]?key)\\s*([:=])\\s*([^\\s|;,]+)",
        options = setOf(RegexOption.MULTILINE)
    )
    private val bearer = Regex("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]{8,}")
    private val uriUserInfo = Regex("(?i)([a-z][a-z0-9+.-]{1,20}://)([^/@\\s]+)@")
    private val basicAuth = Regex("(?i)Basic\\s+[A-Za-z0-9+/=]{8,}")
    private val privateKeyBlock = Regex(
        "-----BEGIN [^-]*PRIVATE KEY-----[\\s\\S]*?-----END [^-]*PRIVATE KEY-----",
        RegexOption.IGNORE_CASE
    )
    private val longOpaqueToken = Regex("(?<![A-Za-z0-9])[A-Za-z0-9_-]{48,}(?![A-Za-z0-9])")

    fun sanitize(message: String): String {
        if (message.isBlank()) return message
        return message
            .replace(privateKeyBlock, "[CLAVE_PRIVADA_OCULTA]")
            .replace(uriUserInfo) { result -> "${result.groupValues[1]}***@" }
            .replace(bearer, "Bearer [OCULTO]")
            .replace(basicAuth, "Basic [OCULTO]")
            .replace(keyValueSecret) { result ->
                "${result.groupValues[1]}${result.groupValues[2]}[OCULTO]"
            }
            .replace(longOpaqueToken, "[TOKEN_OCULTO]")
            .take(MAX_LOG_MESSAGE)
    }

    private const val MAX_LOG_MESSAGE = 8_192
}
