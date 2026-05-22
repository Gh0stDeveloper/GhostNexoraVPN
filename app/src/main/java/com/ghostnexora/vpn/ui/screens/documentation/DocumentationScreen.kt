
@file:OptIn(ExperimentalMaterial3Api::class)
import androidx.compose.foundation.layout.width
package com.ghostnexora.vpn.ui.screens.documentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Troubleshoot
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostnexora.vpn.BuildConfig
import com.ghostnexora.vpn.data.model.ConnectionMode
import com.ghostnexora.vpn.ui.theme.BackgroundDark
import com.ghostnexora.vpn.ui.theme.BorderSubtle
import com.ghostnexora.vpn.ui.theme.Dimens
import com.ghostnexora.vpn.ui.theme.GhostCard
import com.ghostnexora.vpn.ui.theme.NeonAmber
import com.ghostnexora.vpn.ui.theme.NeonCyan
import com.ghostnexora.vpn.ui.theme.NeonGreen
import com.ghostnexora.vpn.ui.theme.SurfaceVariant
import com.ghostnexora.vpn.ui.theme.TextPrimary
import com.ghostnexora.vpn.ui.theme.TextSecondary
import com.ghostnexora.vpn.ui.theme.TextTertiary

@Composable
fun DocumentationScreen() {
    val scrollState = rememberScrollState()
    val supported = ConnectionMode.entries.filter { it.supported }
    val planned = ConnectionMode.entries.filterNot { it.supported }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Documentación técnica") },
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
            HeroDocCard()

            SectionTitle("Visión general")
            DocCard {
                Text(
                    text = "Ghost Nexora VPN es una aplicación Android nativa para administrar perfiles de conexión, estados del túnel, actualización online y diagnósticos en una sola interfaz. La pantalla principal funciona como inicio, mientras que el registro en vivo se abre con un deslizamiento horizontal o desde el botón superior derecho del bloque de inicio.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            SectionTitle("Arquitectura del sistema")
            DocCard {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
                    Bullet("UI en Jetpack Compose con navegación por pantallas y vistas reactivas.")
                    Bullet("StateFlow + Repository como fuente única de verdad para perfiles, ajustes y logs.")
                    Bullet("Room para persistencia local de perfiles, sesiones y registros.")
                    Bullet("DataStore para preferencias de reconexión, permisos y límites de logs.")
                    Bullet("VpnService para el túnel de red y servicio foreground persistente.")
                    Bullet("GitHub Releases como canal de actualización de APK y changelog.")
                }
            }

            SectionTitle("Flujo de uso")
            DocCard {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
                    Numbered("1", "El usuario concede permisos al primer inicio.")
                    Numbered("2", "Se crea o importa un perfil VPN.")
                    Numbered("3", "El dashboard muestra el estado, el perfil activo y el resumen del sistema.")
                    Numbered("4", "Se inicia la conexión y se registran los eventos en tiempo real.")
                    Numbered("5", "El registro completo se consulta desde la vista lateral de logs.")
                    Numbered("6", "La app busca actualizaciones desde GitHub Releases y ofrece instalar la nueva APK encima de la anterior.")
                }
            }

            SectionTitle("Modos de conexión activos")
            ModeList(
                title = "Compatibles en el motor actual",
                subtitle = "Estos modos aparecen como activos porque el core ya puede resolverlos.",
                color = NeonCyan,
                modes = supported
            )

            SectionTitle("Modos documentados para futuras etapas")
            ModeList(
                title = "Reservados para un core dedicado",
                subtitle = "Se muestran como referencia estructural dentro de la app.",
                color = NeonAmber,
                modes = planned
            )

            SectionTitle("Sistema de logs")
            DocCard {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
                    Bullet("La pantalla principal muestra un resumen rápido de la actividad.")
                    Bullet("El registro detallado se abre deslizando a la izquierda o pulsando el icono superior derecho del bloque Inicio.")
                    Bullet("Cada línea conserva hora, nivel, etiqueta y mensaje para facilitar soporte técnico.")
                    Bullet("Los logs pueden copiarse y exportarse desde la pantalla específica de registros.")
                    Bullet("Cuando el historial supera el límite configurado, la app recorta las entradas más antiguas.")
                }
            }

            SectionTitle("Sistema de actualizaciones")
            DocCard {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
                    Bullet("La app consulta GitHub Releases y lee tag_name, name, body y assets[].browser_download_url.")
                    Bullet("La comparación de versión se basa en versionCode para evitar falsos positivos.")
                    Bullet("La descarga se realiza sobre el asset publicado, no sobre artifacts temporales del workflow.")
                    Bullet("La instalación se abre sobre la APK actual; si la firma y el applicationId coinciden, los datos y ajustes se conservan.")
                    Bullet("El diálogo de actualización muestra notas de versión, estado de descarga y verificación SHA-256.")
                }
            }

            SectionTitle("Permisos y seguridad")
            DocCard {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
                    PermissionLine("VPN", "Necesario para crear y controlar el túnel.", true)
                    PermissionLine("Ventanas flotantes", "Permite mostrar controles sobre otras apps.", true)
                    PermissionLine("Notificaciones", "Mantiene el estado persistente visible.", true)
                    PermissionLine("Instalar apps desconocidas", "Se usa para abrir el instalador de la nueva APK.", true)
                    PermissionLine("Almacenamiento", "Soporta exportación, respaldo y logs.", false)
                    PermissionLine("Boot / batería", "Ayuda al arranque y a la reconexión automática.", false)
                }
            }

            SectionTitle("Troubleshooting rápido")
            DocCard {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
                    Bullet("UnknownHostException: revisar host, DNS o campo de destino.")
                    Bullet("Auth fail: validar usuario, contraseña y método permitido por el servidor.")
                    Bullet("Trust anchor not found: revisar certificados o SNI.")
                    Bullet("No APK disponible: confirmar que la release tenga un asset .apk publicado.")
                    Bullet("El registro no avanza: revisar el servicio foreground y el estado del perfil activo.")
                }
            }

            SectionTitle("Metadatos de la app")
            DocCard {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
                    InfoPair("Versión", "${BuildConfig.VERSION_NAME} · Build ${BuildConfig.VERSION_CODE}")
                    InfoPair("Canal", "GitHub Releases")
                    InfoPair("UI", "Jetpack Compose + Material 3")
                    InfoPair("Persistencia", "Room + DataStore")
                    InfoPair("Motor de red", "VpnService + SSH/TLS")
                }
            }

            Spacer(Modifier.height(Dimens.Space3XL))
        }
    }
}

@Composable
private fun HeroDocCard() {
    GhostCard(
        backgroundColor = SurfaceVariant,
        borderColor = BorderSubtle
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.SpaceMD),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Description, contentDescription = null, tint = NeonCyan)
                Spacer(Modifier.width(Dimens.SpaceSM))
                Text(
                    text = "Ghost Nexora VPN",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = "Guía técnica y funcional de la aplicación, con foco en arquitectura, navegación, actualización online, permisos, logs y modos de conexión.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)
            ) {
                TagChip("Inicio + logs", NeonCyan)
                TagChip("GitHub Releases", NeonGreen)
                TagChip("Diagnóstico", NeonAmber)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        ),
        color = NeonCyan
    )
}

@Composable
private fun DocCard(content: @Composable () -> Unit) {
    GhostCard(
        backgroundColor = SurfaceVariant,
        borderColor = BorderSubtle,
        contentPadding = PaddingValues(Dimens.SpaceMD)
    ) {
        content()
    }
}

@Composable
private fun Bullet(text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
        Text("•", color = NeonCyan, style = MaterialTheme.typography.bodyMedium)
        Text(text, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Numbered(number: String, text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
        Text(number, color = NeonCyan, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Text(text, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PermissionLine(title: String, description: String, critical: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            if (critical) Icons.Filled.CheckCircle else Icons.Filled.Info,
            contentDescription = null,
            tint = if (critical) NeonGreen else TextTertiary
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(description, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun InfoPair(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ModeList(
    title: String,
    subtitle: String,
    color: Color,
    modes: List<ConnectionMode>
) {
    DocCard {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)) {
            Text(title, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            modes.forEach { mode ->
                ModeCard(mode = mode, accent = color)
            }
        }
    }
}

@Composable
private fun ModeCard(
    mode: ConnectionMode,
    accent: Color
) {
    GhostCard(
        backgroundColor = if (mode.supported) accent.copy(alpha = 0.08f) else Color(0xFF0B1220),
        borderColor = if (mode.supported) accent.copy(alpha = 0.35f) else BorderSubtle,
        contentPadding = PaddingValues(Dimens.SpaceMD)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = mode.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (mode.supported) TextPrimary else TextTertiary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (mode.supported) "Activo" else "Planificado",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (mode.supported) NeonGreen else NeonAmber
                )
            }

            Text(mode.description, color = TextSecondary, style = MaterialTheme.typography.bodySmall)

            Text(
                text = "Campos: ${mode.requiredFields.joinToString(", ")}",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun TagChip(text: String, color: Color) {
    GhostCard(
        backgroundColor = color.copy(alpha = 0.12f),
        borderColor = color.copy(alpha = 0.35f),
        contentPadding = PaddingValues(horizontal = Dimens.SpaceMD, vertical = Dimens.SpaceXS)
    ) {
        Text(text, color = color, style = MaterialTheme.typography.labelMedium)
    }
}
