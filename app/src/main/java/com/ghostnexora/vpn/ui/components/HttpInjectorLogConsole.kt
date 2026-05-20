package com.ghostnexora.vpn.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ghostnexora.vpn.data.model.LogEntry
import com.ghostnexora.vpn.data.model.LogLevel
import com.ghostnexora.vpn.ui.theme.BorderSubtle
import com.ghostnexora.vpn.ui.theme.Dimens
import com.ghostnexora.vpn.ui.theme.GhostCard
import com.ghostnexora.vpn.ui.theme.NeonAmber
import com.ghostnexora.vpn.ui.theme.NeonCyan
import com.ghostnexora.vpn.ui.theme.NeonGreen
import com.ghostnexora.vpn.ui.theme.NeonRed
import com.ghostnexora.vpn.ui.theme.SurfaceVariant
import com.ghostnexora.vpn.ui.theme.TextPrimary
import com.ghostnexora.vpn.ui.theme.TextSecondary
import com.ghostnexora.vpn.ui.theme.TextTertiary
import com.ghostnexora.vpn.util.httpInjectorLine

@Composable
fun HttpInjectorLogConsole(
    logs: List<LogEntry>,
    modifier: Modifier = Modifier,
    maxHeight: Int = 320
) {
    val listState = rememberLazyListState()
    val orderedLogs = remember(logs) { logs.sortedBy { it.timestamp } }

    LaunchedEffect(orderedLogs.size) {
        if (orderedLogs.isNotEmpty()) {
            listState.animateScrollToItem(orderedLogs.lastIndex)
        }
    }

    GhostCard(
        modifier = modifier,
        backgroundColor = SurfaceVariant,
        borderColor = BorderSubtle,
        contentPadding = PaddingValues(Dimens.SpaceMD)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart
            ) {
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)
                ) {
                    Icon(Icons.Filled.Terminal, contentDescription = null, tint = NeonCyan)
                    Text(
                        text = "Registro de conexión",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = maxHeight.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0B1220))
                    .padding(horizontal = Dimens.SpaceMD, vertical = Dimens.SpaceSM)
            ) {
                if (orderedLogs.isEmpty()) {
                    Text(
                        text = "Sin eventos todavía",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = TextTertiary
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(orderedLogs) { index, entry ->
                            HttpLogLine(
                                entry = entry,
                                isLast = index == orderedLogs.lastIndex
                            )
                        }
                        item { Spacer(modifier = Modifier.heightIn(min = 4.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HttpLogLine(
    entry: LogEntry,
    isLast: Boolean
) {
    val color = when {
        entry.level == LogLevel.ERROR -> NeonRed
        entry.level == LogLevel.WARNING -> NeonAmber
        entry.level == LogLevel.SUCCESS -> NeonGreen
        entry.message.contains("[START]", ignoreCase = true) -> NeonCyan
        entry.message.contains("[STOP]", ignoreCase = true) -> NeonAmber
        entry.message.contains("Conectado", ignoreCase = true) -> NeonGreen
        entry.message.contains("Desconectado", ignoreCase = true) -> TextTertiary
        entry.message.contains("Error", ignoreCase = true) -> NeonRed
        else -> TextSecondary
    }

    Text(
        text = entry.httpInjectorLine(),
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = color,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis
    )
}
