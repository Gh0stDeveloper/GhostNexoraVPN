package com.ghostnexora.vpn.tunnel

import com.ghostnexora.vpn.data.model.LogLevel
import java.util.Locale

data class TunnelLogEvent(
    val level: LogLevel,
    val tag: String,
    val message: String
)

/**
 * Converts the stage markers emitted by JSch and AndroidLibXrayLite into the
 * same structured log model used by the UI. Parsing is deliberately bounded:
 * arbitrary native text never becomes a database tag.
 */
object TunnelLogEventParser {
    private val prefix = Regex("^\\[([A-Z0-9_-]{2,16})]\\s*")
    private val successWords = listOf(
        "ACTIVO",
        "AUTENTICADA",
        "AUTENTICACIÓN COMPLETADA",
        "CONECTADO",
        "COMPLETADO",
        "LISTO",
        "VERIFICADO"
    )

    fun parse(raw: String): TunnelLogEvent? {
        val normalized = raw.replace('\n', ' ').trim().take(1_024)
        if (normalized.isBlank()) return null

        val marker = prefix.find(normalized)?.groupValues?.getOrNull(1).orEmpty()
        val message = normalized.replaceFirst(prefix, "").trim().ifBlank { normalized }
        val uppercase = message.uppercase(Locale.ROOT)
        val level = when {
            marker == "ERROR" || uppercase.startsWith("ERROR ·") || uppercase.contains("[ERROR]") ->
                LogLevel.ERROR
            marker == "WARN" || marker == "WARNING" || uppercase.startsWith("WARN ·") ->
                LogLevel.WARNING
            successWords.any(uppercase::contains) -> LogLevel.SUCCESS
            else -> LogLevel.INFO
        }

        return TunnelLogEvent(
            level = level,
            tag = normalizeTag(marker, uppercase),
            message = message
        )
    }

    private fun normalizeTag(marker: String, message: String): String = when (marker) {
        "TLS" -> "TLS"
        "SSH", "SOCKS", "PAYLOAD", "PROXY" -> "SSH"
        "XRAY", "CORE" -> "CORE"
        "NETWORK", "DNS", "ROUTING", "TUN" -> "NETWORK"
        "SETTINGS" -> "SETTINGS"
        "ERROR" -> when {
            message.contains("TLS") || message.contains("CERTIFIC") -> "TLS"
            message.contains("SSH") || message.contains("PAYLOAD") || message.contains("SOCKS") -> "SSH"
            message.contains("TUN") || message.contains("RED") || message.contains("NETWORK") -> "NETWORK"
            else -> "CORE"
        }
        else -> "CORE"
    }
}
