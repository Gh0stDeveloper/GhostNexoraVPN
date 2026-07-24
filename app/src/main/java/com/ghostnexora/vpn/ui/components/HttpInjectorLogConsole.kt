package com.ghostnexora.vpn.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ghostnexora.vpn.data.model.LogEntry
import com.ghostnexora.vpn.data.model.LogLevel
import com.ghostnexora.vpn.ui.theme.Dimens
import com.ghostnexora.vpn.ui.theme.NeonAmber
import com.ghostnexora.vpn.ui.theme.NeonCyan
import com.ghostnexora.vpn.ui.theme.NeonGreen
import com.ghostnexora.vpn.ui.theme.NeonRed
import com.ghostnexora.vpn.ui.theme.TextSecondary
import com.ghostnexora.vpn.ui.theme.TextTertiary
import com.ghostnexora.vpn.util.httpInjectorLine

/**
 * Contenido de la consola de conexión.
 *
 * No crea una tarjeta propia: el dashboard aporta el único contenedor visual,
 * evitando el antiguo efecto de "log dentro de otro log".
 */
@Composable
fun HttpInjectorLogConsole(
    logs: List<LogEntry>,
    modifier: Modifier = Modifier,
    maxHeight: Int = 460
) {
    val listState = rememberLazyListState()
    val orderedLogs = remember(logs) { logs.sortedBy { it.timestamp } }
    var selectedLogId by remember { mutableStateOf<Long?>(null) }
    var followTail by remember { mutableStateOf(true) }

    val isAtBottom by remember {
        derivedStateOf {
            if (orderedLogs.isEmpty()) return@derivedStateOf true
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= orderedLogs.lastIndex - 1
        }
    }

    LaunchedEffect(isAtBottom) {
        if (isAtBottom) followTail = true
    }

    LaunchedEffect(orderedLogs.size, followTail) {
        if (followTail && orderedLogs.isNotEmpty()) {
            listState.animateScrollToItem(orderedLogs.lastIndex)
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (orderedLogs.isEmpty()) {
                    "Sin eventos"
                } else {
                    "${orderedLogs.size} eventos · ${if (followTail) "seguimiento activo" else "seguimiento pausado"}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary
            )

            IconButton(
                onClick = {
                    followTail = true
                    selectedLogId = null
                },
                enabled = orderedLogs.isNotEmpty()
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Ir al final",
                    tint = if (orderedLogs.isNotEmpty()) NeonCyan else TextTertiary
                )
            }
        }

        if (orderedLogs.isEmpty()) {
            Text(
                text = "Los eventos del túnel aparecerán aquí cuando inicies una conexión.",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = TextTertiary,
                modifier = Modifier.padding(vertical = Dimens.SpaceLG)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = maxHeight.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(bottom = Dimens.SpaceSM)
            ) {
                items(orderedLogs, key = { it.id }) { entry ->
                    HttpLogLine(
                        entry = entry,
                        isSelected = entry.id == selectedLogId,
                        onClick = {
                            selectedLogId = if (selectedLogId == entry.id) null else entry.id
                            followTail = false
                        }
                    )
                }
                item { Spacer(modifier = Modifier.height(2.dp)) }
            }
        }
    }
}

@Composable
private fun HttpLogLine(
    entry: LogEntry,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val color = when {
        entry.level == LogLevel.ERROR -> NeonRed
        entry.level == LogLevel.WARNING -> NeonAmber
        entry.level == LogLevel.SUCCESS -> NeonGreen
        entry.message.contains("Conexión VPN establecida", ignoreCase = true) -> NeonGreen
        entry.message.contains("desconectada", ignoreCase = true) -> TextTertiary
        else -> TextSecondary
    }

    Text(
        text = entry.httpInjectorLine(),
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = if (isSelected) NeonCyan else color,
        softWrap = true,
        overflow = TextOverflow.Clip,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp, horizontal = 6.dp)
    )
}
