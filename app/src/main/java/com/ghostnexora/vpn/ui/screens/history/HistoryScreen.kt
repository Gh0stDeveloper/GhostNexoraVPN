package com.ghostnexora.vpn.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ghostnexora.vpn.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .verticalScroll(rememberScrollState())
            .padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceLG),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.History,
            contentDescription = null,
            tint = NeonCyan,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            text = "Historial de conexión",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary
        )
        GhostCard(
            backgroundColor = SurfaceVariant,
            borderColor = BorderNormal,
            glowColor = NeonCyan,
            contentPadding = PaddingValues(Dimens.CardPadding)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Esta pantalla queda lista para registrar sesiones, reconexiones y eventos por perfil.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "En la V20 se puede conectar con Room o con un feed de logs filtrado por perfil.",
                    color = TextTertiary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        onBack?.let {
            TextButton(onClick = it) { Text("Volver") }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
