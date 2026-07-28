@file:OptIn(ExperimentalMaterial3Api::class)

package com.ghostnexora.vpn.ui.screens.documentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
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
import com.ghostnexora.vpn.data.model.ConnectionMode
import com.ghostnexora.vpn.ui.theme.BackgroundDark
import com.ghostnexora.vpn.ui.theme.BorderSubtle
import com.ghostnexora.vpn.ui.theme.Dimens
import com.ghostnexora.vpn.ui.theme.GhostCard
import com.ghostnexora.vpn.ui.theme.NeonAmber
import com.ghostnexora.vpn.ui.theme.NeonCyan
import com.ghostnexora.vpn.ui.theme.NeonGreen
import com.ghostnexora.vpn.ui.theme.TextPrimary
import com.ghostnexora.vpn.ui.theme.TextSecondary

@Composable
fun DocumentationScreen() {
    val supported = ConnectionMode.entries.filter(ConnectionMode::supported)
    val planned = ConnectionMode.entries.filterNot(ConnectionMode::supported)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manual de Ghost Nexora VPN") },
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
                .verticalScroll(rememberScrollState())
                .padding(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceLG)
        ) {
            InfoBlock(
                "Estado real de compatibilidad",
                "La aplicación separa CI verificado, prueba física pendiente y experimental. Un core iniciado no demuestra que el servidor entregue Internet.",
                NeonCyan
            )

            InfoBlock(
                "Conexión aceptada",
                "El flujo valida perfil, red física, DNS/TCP/TLS y salida remota antes del TUN. Después inicia SSH/Xray, verifica Internet de nuevo y recién entonces muestra Conectado.",
                NeonGreen
            )

            InfoBlock(
                "Diagnóstico",
                "Ajustes > Motor de conexión ejecuta pruebas independientes sin cambiar las rutas normales del teléfono. Cada fallo incluye código y solución. Exporta el reporte desde Logs.",
                NeonCyan
            )

            InfoBlock(
                "Importación",
                "Admite GNX2, JSON legado, JSON Xray, vmess://, vless://, trojan://, hysteria2://, hy2:// y ssh://. QR, archivo y portapapeles muestran protocolo, transporte, seguridad, SNI, Host y path antes de guardar.",
                NeonCyan
            )

            InfoBlock(
                "Payload avanzado",
                "Incluye plantillas CONNECT, GET, POST, HEAD y WebSocket. Variables, CRLF visible, [split] y [delay=N] tienen límites estrictos y se validan antes de guardar o transmitir.",
                NeonAmber
            )

            InfoBlock(
                "Aplicaciones por VPN",
                "Puedes enrutar todas las apps, solo una selección o excluir una selección. El modo solo seleccionadas falla de forma segura si la lista está vacía o las apps fueron desinstaladas.",
                NeonCyan
            )

            InfoBlock(
                "Privacidad y seguridad",
                "Perfiles cifrados con Android Keystore, exportaciones GNX2 autenticadas, TLS estricto, fingerprints SSH, logs saneados y bloqueo de capturas en pantallas de credenciales. No existe Trust All global.",
                NeonGreen
            )

            SectionBlock(
                title = "Métodos disponibles en el motor",
                subtitle = "La implementación existe; consulta Compatibilidad para saber qué combinaciones todavía requieren servidor y dispositivo real.",
                color = NeonCyan,
                modes = supported
            )

            SectionBlock(
                title = "Métodos pendientes de integración",
                subtitle = "No se presentan como disponibles hasta tener motor, validación, UI y pruebas.",
                color = NeonAmber,
                modes = planned
            )

            InfoBlock(
                "Prueba recomendada",
                "1. Selecciona perfil. 2. Ejecuta Diagnóstico. 3. Revisa código de error. 4. Conecta. 5. Comprueba subida y bajada. 6. Exporta logs si falla. Empieza con IPv4, MTU 1400 y DNS automático.",
                NeonCyan
            )

            InfoBlock(
                "Documentación completa",
                "El repositorio contiene arquitectura, modos, SSH, payloads, Xray, DNS, split tunneling, seguridad, formatos, importación, troubleshooting, matriz de pruebas, build, privacidad, changelog y roadmap.",
                NeonGreen
            )

            Spacer(Modifier.height(Dimens.Space3XL))
        }
    }
}

@Composable
private fun InfoBlock(title: String, text: String, color: Color) {
    GhostCard(borderColor = color.copy(alpha = 0.35f), contentPadding = PaddingValues(Dimens.SpaceMD)) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = color)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
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
        InfoBlock(title, subtitle, color)
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
                    Text(
                        if (mode.supported) "Estado: implementado; evidencia física según combinación" else "Estado: no implementado",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (mode.supported) NeonCyan else NeonAmber
                    )
                }
            }
        }
    }
}