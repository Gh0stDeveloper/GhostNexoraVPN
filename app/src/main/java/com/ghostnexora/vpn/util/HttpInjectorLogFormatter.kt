package com.ghostnexora.vpn.util

import com.ghostnexora.vpn.data.model.LogEntry
import com.ghostnexora.vpn.data.model.LogLevel
import com.ghostnexora.vpn.security.LogSanitizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object HttpInjectorLogFormatter {
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun format(entry: LogEntry): String {
        val tag = entry.tag.ifBlank { "VPN" }.uppercase(Locale.getDefault()).take(12)
        return "[${timeFormat.format(Date(entry.timestamp))}] [$tag] [${renderTag(entry.level)}] ${LogSanitizer.sanitize(entry.message).trim()}"
    }

    fun format(level: LogLevel, message: String, timestamp: Long = System.currentTimeMillis()): String =
        "[${timeFormat.format(Date(timestamp))}] [VPN] [${renderTag(level)}] ${LogSanitizer.sanitize(message).trim()}"

    fun formatForExport(entries: List<LogEntry>): String =
        entries.joinToString("\n") { format(it) }

    fun renderTag(level: LogLevel): String = when (level) {
        LogLevel.DEBUG -> "DEBUG"
        LogLevel.INFO -> "INFO"
        LogLevel.SUCCESS -> "OK"
        LogLevel.WARNING -> "WARN"
        LogLevel.ERROR -> "ERROR"
    }
}

fun LogEntry.httpInjectorLine(): String = HttpInjectorLogFormatter.format(this)
