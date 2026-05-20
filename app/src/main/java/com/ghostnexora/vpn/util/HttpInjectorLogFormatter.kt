package com.ghostnexora.vpn.util

import com.ghostnexora.vpn.data.model.LogEntry
import com.ghostnexora.vpn.data.model.LogLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Formato de línea compatible con el estilo de consola de HTTP Injector.
 *
 * La UI usa una sola línea por evento:
 * [yyyy-MM-dd HH:mm:ss] mensaje
 */
object HttpInjectorLogFormatter {

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun format(entry: LogEntry): String {
        return "[${timeFormat.format(Date(entry.timestamp))}] ${entry.message.trim()}"
    }

    fun format(level: LogLevel, message: String, timestamp: Long = System.currentTimeMillis()): String {
        return "[${timeFormat.format(Date(timestamp))}] ${message.trim()}"
    }

    fun formatForExport(entries: List<LogEntry>): String = buildString {
        entries.forEach { entry ->
            appendLine(format(entry))
        }
    }

    fun renderTag(level: LogLevel): String = when (level) {
        LogLevel.DEBUG -> "DEBUG"
        LogLevel.INFO -> "INFO"
        LogLevel.SUCCESS -> "OK"
        LogLevel.WARNING -> "WARN"
        LogLevel.ERROR -> "ERROR"
    }
}

fun LogEntry.httpInjectorLine(): String = HttpInjectorLogFormatter.format(this)
