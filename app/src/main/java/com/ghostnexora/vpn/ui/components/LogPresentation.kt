package com.ghostnexora.vpn.ui.components

import com.ghostnexora.vpn.data.model.LogEntry
import com.ghostnexora.vpn.data.model.LogLevel
import com.ghostnexora.vpn.security.HtmlNoteSanitizer
import java.util.Locale

/** Presentation-only rules shared by the compact and full connection logs. */
object LogPresentation {
    private const val SERVER_MESSAGE_MARKER = "Mensaje del servidor ·"
    private val layoutTag = Regex(
        pattern = "<\\s*(br|p|div|pre|li|ul|ol|table|tr|h[1-6]|center|hr)\\b",
        option = RegexOption.IGNORE_CASE
    )

    private val summaryPhrases = listOf(
        "SOLICITUD DE CONEXIÓN",
        "INICIANDO ",
        "TRANSPORTE TCP",
        "HANDSHAKE",
        "SOCKET TCP FÍSICO CONECTADO",
        "CONNECTION ESTABLISHED",
        "AUTENTICACIÓN COMPLETADA",
        "AUTHENTICATION SUCCEEDED",
        "SESIÓN AUTENTICADA",
        "BRIDGE SSH ACTIVO",
        "CORE ACTIVO",
        "STARTED SUCCESSFULLY",
        "XRAY CORE ACTIVO",
        "TRANSPORT_VPN",
        "ESTADO CONECTADO",
        "VPN DESCONECTADA",
        "RECONECT"
    )

    private val vpsMotdPhrases = listOf(
        "SYSTEM INFORMATION AS OF",
        "USAGE OF /:",
        "EXPANDED SECURITY MAINTENANCE",
        "UPDATES CAN BE APPLIED IMMEDIATELY",
        "SYSTEM RESTART REQUIRED",
        "UBUNTU COMES WITH ABSOLUTELY NO WARRANTY"
    )

    fun serverMessageBody(message: String): String? {
        val markerIndex = message.indexOf(SERVER_MESSAGE_MARKER, ignoreCase = true)
        if (markerIndex < 0) return null
        return message.substring(markerIndex + SERVER_MESSAGE_MARKER.length)
            .trim()
            .takeIf(String::isNotEmpty)
    }

    /** Hides MOTD rows persisted by 1.0.51 before shell-based collection was removed. */
    fun isLegacyVpsMotd(message: String): Boolean {
        val markerIndex = message.indexOf(SERVER_MESSAGE_MARKER, ignoreCase = true)
        if (markerIndex < 0) return false
        val uppercase = message.substring(markerIndex + SERVER_MESSAGE_MARKER.length)
            .uppercase(Locale.ROOT)
        return uppercase.contains("WELCOME TO UBUNTU") &&
            vpsMotdPhrases.count(uppercase::contains) >= 2
    }

    fun serverMessageHtml(message: String): String? {
        val body = serverMessageBody(message) ?: return null
        val withVisibleLineBreaks = if (layoutTag.containsMatchIn(body)) {
            body
        } else {
            body.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace("\n", "<br>")
        }
        return HtmlNoteSanitizer.sanitize(withVisibleLineBreaks)
            .takeIf(String::isNotBlank)
    }

    fun displayMessage(entry: LogEntry): String {
        val trimmed = entry.message.trim()
        val stagePrefix = "[${entry.tag.uppercase(Locale.ROOT)}]"
        return if (trimmed.startsWith(stagePrefix, ignoreCase = true)) {
            trimmed.substring(stagePrefix.length).trim()
        } else {
            trimmed
        }
    }

    fun belongsToSummary(entry: LogEntry): Boolean {
        if (isLegacyVpsMotd(entry.message)) return false
        if (serverMessageBody(entry.message) != null) return true
        if (entry.level == LogLevel.ERROR ||
            entry.level == LogLevel.WARNING ||
            entry.level == LogLevel.SUCCESS
        ) return true
        val uppercase = entry.message.uppercase(Locale.ROOT)
        return summaryPhrases.any(uppercase::contains)
    }
}
