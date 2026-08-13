package com.ghostnexora.vpn.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostnexora.vpn.data.model.LogEntry
import com.ghostnexora.vpn.data.model.LogLevel
import com.ghostnexora.vpn.ui.theme.BorderNormal
import com.ghostnexora.vpn.ui.theme.BorderSubtle
import com.ghostnexora.vpn.ui.theme.Dimens
import com.ghostnexora.vpn.ui.theme.NeonAmber
import com.ghostnexora.vpn.ui.theme.NeonBlue
import com.ghostnexora.vpn.ui.theme.NeonCyan
import com.ghostnexora.vpn.ui.theme.NeonGreen
import com.ghostnexora.vpn.ui.theme.NeonPurple
import com.ghostnexora.vpn.ui.theme.NeonRed
import com.ghostnexora.vpn.ui.theme.SurfaceDark
import com.ghostnexora.vpn.ui.theme.SurfaceElevated
import com.ghostnexora.vpn.ui.theme.TextPrimary
import com.ghostnexora.vpn.ui.theme.TextSecondary
import com.ghostnexora.vpn.ui.theme.TextTertiary

/** One accessible, filterable connection console shared by the dashboard and Logs screen. */
@Composable
fun HttpInjectorLogConsole(
    logs: List<LogEntry>,
    modifier: Modifier = Modifier,
    maxHeight: Dp? = 460.dp
) {
    val listState = rememberLazyListState()
    var filter by remember { mutableStateOf(LogFilter.SUMMARY) }
    var selectedLogId by remember { mutableStateOf<Long?>(null) }
    var followTail by remember { mutableStateOf(true) }
    val orderedLogs = remember(logs, filter) {
        logs.sortedWith(compareBy<LogEntry> { it.timestamp }.thenBy { it.id })
            .filterNot { LogPresentation.isLegacyVpsMotd(it.message) }
            .filter(filter::matches)
    }
    val isAtBottom by remember(orderedLogs, filter) {
        derivedStateOf {
            orderedLogs.isEmpty() ||
                (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >=
                orderedLogs.lastIndex - 1
        }
    }

    LaunchedEffect(listState.isScrollInProgress, isAtBottom) {
        if (listState.isScrollInProgress) followTail = isAtBottom
        else if (isAtBottom) followTail = true
    }
    LaunchedEffect(orderedLogs.size, filter, followTail) {
        if (followTail && orderedLogs.isNotEmpty()) listState.scrollToItem(orderedLogs.lastIndex)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)
        ) {
            LogFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = {
                        filter = option
                        followTail = true
                        selectedLogId = null
                    },
                    label = {
                        Text(
                            option.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (filter == option) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = SurfaceDark,
                        labelColor = TextSecondary,
                        selectedContainerColor = NeonCyan.copy(alpha = 0.14f),
                        selectedLabelColor = NeonCyan
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = filter == option,
                        borderColor = BorderNormal,
                        selectedBorderColor = NeonCyan.copy(alpha = 0.75f)
                    )
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = if (orderedLogs.isEmpty()) "Sin eventos" else "${orderedLogs.size} eventos",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Text(
                    text = if (followTail) "Actualización automática activa" else "Vista pausada",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (followTail) NeonGreen else NeonAmber
                )
            }
            IconButton(
                onClick = {
                    followTail = true
                    selectedLogId = null
                },
                enabled = orderedLogs.isNotEmpty()
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    "Seguir los eventos más recientes",
                    tint = if (orderedLogs.isNotEmpty()) NeonCyan else TextTertiary
                )
            }
        }

        val consoleModifier = if (maxHeight == null) {
            Modifier.fillMaxWidth().weight(1f)
        } else {
            Modifier.fillMaxWidth().heightIn(min = 180.dp, max = maxHeight)
        }
        Surface(
            modifier = consoleModifier,
            shape = RoundedCornerShape(18.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, BorderSubtle),
            tonalElevation = 0.dp
        ) {
            if (orderedLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(Dimens.SpaceXL),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No hay eventos para este filtro.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = Dimens.SpaceXS)
                ) {
                    items(orderedLogs, key = { it.id }) { entry ->
                        if (LogPresentation.serverMessageBody(entry.message) != null) {
                            ServerMessageLogEntry(entry)
                        } else {
                            HttpLogLine(
                                entry = entry,
                                isSelected = entry.id == selectedLogId,
                                onClick = {
                                    selectedLogId = if (selectedLogId == entry.id) null else entry.id
                                    followTail = false
                                }
                            )
                        }
                    }
                    item { Spacer(Modifier.height(Dimens.SpaceXS)) }
                }
            }
        }
    }
}

@Composable
private fun ServerMessageLogEntry(entry: LogEntry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.SpaceSM, vertical = Dimens.SpaceXS)
            .clip(RoundedCornerShape(14.dp))
            .background(NeonCyan.copy(alpha = 0.07f))
            .padding(Dimens.SpaceMD),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(NeonCyan.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Security,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "Mensaje del servidor",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "SSH · ${entry.timeFormatted}",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            StatusBadge("SERVIDOR", NeonCyan)
        }
        HorizontalDivider(color = NeonCyan.copy(alpha = 0.18f))
        SshServerMessageView(
            message = entry.message,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun HttpLogLine(entry: LogEntry, isSelected: Boolean, onClick: () -> Unit) {
    val tone = eventColor(entry)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) SurfaceElevated else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.SpaceMD, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 3.dp)
                .width(3.dp)
                .height(38.dp)
                .clip(RoundedCornerShape(50))
                .background(tone)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    entry.timeFormatted,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = TextTertiary
                )
                StatusBadge(entry.tag.ifBlank { "VPN" }.uppercase(), tagColor(entry.tag))
                Text(
                    levelLabel(entry.level),
                    style = MaterialTheme.typography.labelSmall,
                    color = tone,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = LogPresentation.displayMessage(entry),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = tone,
                softWrap = true,
                overflow = TextOverflow.Clip
            )
        }
    }
}

@Composable
private fun StatusBadge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = color,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

private fun eventColor(entry: LogEntry): Color {
    if (entry.level == LogLevel.ERROR) return NeonRed
    if (entry.level == LogLevel.WARNING) return NeonAmber
    if (entry.level == LogLevel.SUCCESS) return NeonGreen
    val uppercase = entry.message.uppercase()
    val isProgress = listOf(
        "SOLICITUD DE CONEXIÓN",
        "INICIANDO",
        "ABRIENDO",
        "HANDSHAKE",
        "NEGOCIANDO",
        "ESPERANDO",
        "PREPARANDO",
        "ADJUNTANDO",
        "ENTREGANDO LA INTERFAZ",
        "CREANDO CONTROLADOR"
    ).any(uppercase::contains)
    return if (isProgress) NeonAmber else TextPrimary
}

private fun levelLabel(level: LogLevel): String = when (level) {
    LogLevel.SUCCESS -> "OK"
    LogLevel.WARNING -> "AVISO"
    LogLevel.ERROR -> "ERROR"
    LogLevel.DEBUG -> "DEBUG"
    LogLevel.INFO -> "INFO"
}

private fun tagColor(tag: String): Color = when (tag.uppercase()) {
    "VPN", "SECURITY" -> NeonGreen
    "SSH", "SOCKS" -> NeonCyan
    "TLS" -> NeonPurple
    "NETWORK", "DNS", "ROUTING", "TUN" -> NeonBlue
    "ERROR" -> NeonRed
    else -> TextSecondary
}

private enum class LogFilter(val label: String) {
    SUMMARY("Resumen"),
    ALL("Todos"),
    NETWORK("Red"),
    SSH("SSH"),
    TLS("TLS"),
    ERRORS("Alertas");

    fun matches(entry: LogEntry): Boolean {
        val tag = entry.tag.uppercase()
        val message = entry.message.uppercase()
        return when (this) {
            SUMMARY -> LogPresentation.belongsToSummary(entry)
            ALL -> true
            NETWORK -> tag in setOf("NETWORK", "DNS", "ROUTING", "TUN") ||
                message.contains("RED") || message.contains("TUN")
            SSH -> tag == "SSH" || tag == "SOCKS" ||
                message.contains("SSH") || message.contains("SOCKS")
            TLS -> tag == "TLS" || message.contains("TLS") || message.contains("CERTIFIC")
            ERRORS -> entry.level == LogLevel.ERROR || entry.level == LogLevel.WARNING
        }
    }
}
