@file:OptIn(ExperimentalMaterial3Api::class)

package com.ghostnexora.vpn.ui.screens.documentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ghostnexora.vpn.data.model.ConnectionMode
import com.ghostnexora.vpn.ui.theme.BackgroundDark
import com.ghostnexora.vpn.ui.theme.BorderSubtle
import com.ghostnexora.vpn.ui.theme.Dimens
import com.ghostnexora.vpn.ui.theme.GhostCard
import com.ghostnexora.vpn.ui.theme.NeonAmber
import com.ghostnexora.vpn.ui.theme.NeonCyan
import com.ghostnexora.vpn.ui.theme.TextPrimary
import com.ghostnexora.vpn.ui.theme.TextSecondary

@Composable
fun DocumentationScreen() {
    val scrollState = rememberScrollState()
    val supported = ConnectionMode.entries.filter { it.supported }
    val planned = ConnectionMode.entries.filterNot { it.supported }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Documentación de métodos") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceLG)
        ) {
            GhostCard(borderColor = BorderSubtle, contentPadding = PaddingValues(Dimens.SpaceMD)) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
                    Text("Qué hace esta sección", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(
                        "Aquí se explican los métodos de conexión, los campos que requiere cada uno y cuáles están activos dentro del motor actual.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            SectionBlock(
                title = "Métodos activos",
                subtitle = "Son los que el motor actual puede leer y usar en la conexión.",
                color = NeonCyan,
                modes = supported
            )

            SectionBlock(
                title = "Métodos documentados para futuras integraciones",
                subtitle = "Aparecen en la app como referencia, pero todavía necesitan un core dedicado.",
                color = NeonAmber,
                modes = planned
            )

            GhostCard(borderColor = BorderSubtle, contentPadding = PaddingValues(Dimens.SpaceMD)) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
                    Text("Flujo recomendado", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text("1. Crea un perfil.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Text("2. Elige el método correcto según tu servidor.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Text("3. Completa solo los campos que el método requiera.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Text("4. Selecciona ese perfil antes de conectar.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }

            Spacer(Modifier.height(Dimens.Space3XL))
        }
    }
}

@Composable
private fun SectionBlock(
    title: String,
    subtitle: String,
    color: Color,
    modes: List<ConnectionMode>
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)) {
        GhostCard(borderColor = color.copy(alpha = 0.3f), contentPadding = PaddingValues(Dimens.SpaceMD)) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXS)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = color)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }

        modes.forEach { mode ->
            GhostCard(borderColor = BorderSubtle, contentPadding = PaddingValues(Dimens.SpaceMD)) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXS)) {
                    Text(mode.label, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text(mode.description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Text(
                        "Campos: ${mode.requiredFields.joinToString(" · ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    if (!mode.supported) {
                        Text(
                            "Estado: pendiente de motor dedicado",
                            style = MaterialTheme.typography.bodySmall,
                            color = NeonAmber
                        )
                    }
                }
            }
        }
    }
}
