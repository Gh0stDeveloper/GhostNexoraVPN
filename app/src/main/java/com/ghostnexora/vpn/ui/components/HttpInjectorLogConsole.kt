package com.ghostnexora.vpn.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.flow.collectLatest

/**
 * Consola interactiva estilo HTTP Injector.
 *
 * - No trunca el contenido de la línea.
 * - Permite desplazar hacia arriba/abajo libremente.
 * - Mantiene auto-scroll solo mientras el usuario está en el borde inferior.
 * - Permite seleccionar una entrada para verla con más calma.
 */
@Composable
fun HttpInjectorLogConsole(
    logs: List<LogEntry>,
    modifier: Modifier = Modifier,
    maxHeight: Int = 360
) {
    val listState = rememberLazyListState()
    val orderedLogs = remember(logs) { logs.sortedBy { it.timestamp } }
    var selectedLogId by rememberSaveable { mutableStateOf<Long?>(null) }
    var followTail by rememberSaveable { mutableStateOf(true) }

    val selectedEntry = remember(orderedLogs, selectedLogId) {
        orderedLogs.firstOrNull { it.id == selectedLogId }
    }

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

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to isAtBottom }
            .collectLatest { (_, atBottom) ->
                if (!atBottom) followTail = false
            }
    }

    GhostCard(
        modifier = modifier,
        backgroundColor = SurfaceVariant,
        borderColor = BorderSubtle,
        contentPadding = PaddingValues(Dimens.SpaceMD)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
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

                IconButton(onClick = {
                    selectedLogId = orderedLogs.lastOrNull()?.id
                    followTail = true
                }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Ir al final", tint = NeonCyan)
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
                    SelectionContainer {
                        LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            itemsIndexed(orderedLogs, key = { _, entry -> entry.id }) { index, entry ->
                                HttpLogLine(
                                    entry = entry,
                                    isLast = index == orderedLogs.lastIndex,
                                    isSelected = entry.id == selectedLogId,
                                    onClick = {
                                        selectedLogId = if (selectedLogId == entry.id) null else entry.id
                                    }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(4.dp)) }
                        }
                    }
                }
            }

            if (selectedEntry != null) {
                SelectedLogPreview(
                    entry = selectedEntry,
                    onDismiss = { selectedLogId = null }
                )
            }
        }
    }
}

@Composable
private fun HttpLogLine(
    entry: LogEntry,
    isLast: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
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
        color = if (isSelected) NeonCyan else color,
        softWrap = true,
        overflow = TextOverflow.Clip,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp, horizontal = 4.dp)
    )
}

@Composable
private fun SelectedLogPreview(
    entry: LogEntry,
    onDismiss: () -> Unit
) {
    GhostCard(
        backgroundColor = Color(0xFF0B1220),
        borderColor = NeonCyan.copy(alpha = 0.5f),
        contentPadding = PaddingValues(Dimens.SpaceMD)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Detalle del registro",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
                Text(
                    text = "Cerrar",
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonCyan,
                    modifier = Modifier.clickable(onClick = onDismiss)
                )
            }

            Text(
                text = entry.httpInjectorLine(),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = TextSecondary,
                softWrap = true
            )
        }
    }
}
