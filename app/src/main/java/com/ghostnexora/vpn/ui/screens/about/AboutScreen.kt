package com.ghostnexora.vpn.ui.screens.about

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostnexora.vpn.BuildConfig
import com.ghostnexora.vpn.ui.theme.*

// ══════════════════════════════════════════════════════════════════════════
// ABOUT SCREEN
// ══════════════════════════════════════════════════════════════════════════

private const val GITHUB_URL   = "https://github.com/CHICO-CP"
private const val TELEGRAM_URL = "https://t.me/Gh0stDeveloper"
private const val EMAIL        = "ghostnexora@gmail.com"
private val APP_VERSION = BuildConfig.VERSION_NAME

@Composable
fun AboutScreen() {
    val context = LocalContext.current

    fun openUrl(url: String) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
        )
    }

    fun openEmail() {
        context.startActivity(
            Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$EMAIL")
                putExtra(Intent.EXTRA_SUBJECT, "Ghost Nexora VPN — Contacto")
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .verticalScroll(rememberScrollState())
            .padding(Dimens.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXXL)
    ) {
        Spacer(Modifier.height(Dimens.SpaceSM))

        // ── 1. Header animado ──────────────────────────────────────────────
        AboutHeader()

        // ── 2. Descripción del proyecto ────────────────────────────────────
        ProjectDescriptionCard()

        // ── 3. Características clave ───────────────────────────────────────
        FeaturesCard()

        // ── 4. Desarrollador ──────────────────────────────────────────────
        DeveloperCard(
            onGithub   = { openUrl(GITHUB_URL) },
            onTelegram = { openUrl(TELEGRAM_URL) },
            onEmail    = { openEmail() }
        )

        // ── 5. Links rápidos ──────────────────────────────────────────────
        QuickLinksCard(
            onSourceCode  = { openUrl("$GITHUB_URL/GhostNexoraVPN") },
            onIssues      = { openUrl("$GITHUB_URL/GhostNexoraVPN/issues") },
            onPrivacy     = { openUrl("$GITHUB_URL/GhostNexoraVPN/blob/main/PRIVACY.md") },
            onChangelog   = { openUrl("$GITHUB_URL/GhostNexoraVPN/blob/main/CHANGELOG.md") }
        )

        // ── 6. Versión y créditos ─────────────────────────────────────────
        VersionFooter()

        Spacer(Modifier.height(Dimens.Space3XL))
    }
}

// ══════════════════════════════════════════════════════════════════════════
// HEADER ANIMADO
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun AboutHeader() {
    // Glow pulsante del logo
    val glowAlpha by rememberInfiniteTransition(label = "glow")
        .animateFloat(
            initialValue = 0.3f,
            targetValue  = 0.7f,
            animationSpec = infiniteRepeatable(
                animation  = tween(2000, easing = EaseInOut),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glow_alpha"
        )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)
    ) {
        // Logo circular con glow neon
        Box(contentAlignment = Alignment.Center) {
            // Anillo exterior de glow
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(NeonCyan.copy(alpha = glowAlpha * 0.15f))
            )
            // Círculo principal
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                NeonCyan.copy(0.25f),
                                SurfaceVariant
                            )
                        )
                    )
                    .border(2.dp, NeonCyan, CircleShape)
                    .neonGlow(NeonCyan, radius = 20.dp, alpha = glowAlpha * 0.5f),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = null,
                    tint     = NeonCyan,
                    modifier = Modifier.size(46.dp)
                )
            }
        }

        // Nombre de la app
        Text(
            text  = "Ghost Nexora VPN",
            style = MaterialTheme.typography.headlineSmall.copy(
                color      = TextPrimary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        )

        // Tagline
        Text(
            text  = "Gestión profesional de perfiles VPN",
            style = MaterialTheme.typography.bodyMedium,
            color = NeonCyanDim,
            textAlign = TextAlign.Center
        )

        // Badge de versión
        Box(
            modifier = Modifier
                .clip(TagShape)
                .background(NeonCyan.copy(0.1f))
                .border(Dimens.BorderNormal, NeonCyan.copy(0.3f), TagShape)
                .padding(horizontal = Dimens.SpaceMD, vertical = Dimens.SpaceXS)
        ) {
            Text(
                text  = "v$APP_VERSION  ·  V21 estable",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = NeonCyan,
                    letterSpacing = 1.sp
                )
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// DESCRIPCIÓN DEL PROYECTO
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun ProjectDescriptionCard() {
    GhostCard(
        borderColor     = NeonCyan.copy(0.2f),
        backgroundColor = NeonCyan.copy(0.03f)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)) {
            // Título de sección
            SectionLabel(text = "SOBRE EL PROYECTO", icon = Icons.Filled.Info, color = NeonCyan)

            Text(
                text = "Ghost Nexora VPN es una aplicación Android nativa diseñada para " +
                       "centralizar la gestión de perfiles de conexión VPN de forma " +
                       "moderna, segura y minimalista.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Text(
                text = "A diferencia de los gestores convencionales, Ghost Nexora ofrece " +
                       "una experiencia similar a las VPN comerciales premium: importación " +
                       "y exportación de perfiles en JSON, creación manual con soporte para " +
                       "múltiples métodos (SSH, V2Ray, Shadowsocks, WireGuard), dashboard " +
                       "con estados visuales en tiempo real y ejecución persistente en " +
                       "segundo plano mediante VpnService.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Text(
                text = "El proyecto está diseñado para escalar hacia funcionalidades " +
                       "avanzadas como per-app VPN, cifrado de exportaciones, " +
                       "sincronización en nube y bloqueo biométrico.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// CARACTERÍSTICAS CLAVE
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun FeaturesCard() {
    GhostCard(borderColor = BorderSubtle) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)) {
            SectionLabel(text = "CARACTERÍSTICAS", icon = Icons.Filled.Star, color = NeonAmber)

            val features = listOf(
                Triple(Icons.Filled.VpnKey,          NeonCyan,   "Gestión completa de perfiles VPN"),
                Triple(Icons.Filled.FileDownload,     NeonBlue,   "Importación y exportación en JSON"),
                Triple(Icons.Filled.Shield,           NeonGreen,  "Interfaz TUN nativa con VpnService"),
                Triple(Icons.Filled.Layers,           NeonPurple, "Ventana flotante de control rápido"),
                Triple(Icons.Filled.Notifications,   NeonAmber,  "Notificación persistente de sesión"),
                Triple(Icons.Filled.DarkMode,         NeonCyan,   "Tema oscuro con acentos neon"),
                Triple(Icons.Filled.Terminal,         NeonBlue,   "Registro de logs en tiempo real"),
                Triple(Icons.Filled.RestartAlt,       NeonGreen,  "Reconexión automática al inicio"),
                Triple(Icons.Filled.Security,         NeonPurple, "Almacenamiento seguro con Keystore"),
                Triple(Icons.Filled.Code,             NeonCyan,   "Código fuente abierto en GitHub")
            )

            features.forEach { (icon, color, label) ->
                FeatureRow(icon = icon, color = color, label = label)
            }
        }
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    color: Color,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(color.copy(0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text  = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════
// TARJETA DE DESARROLLADOR
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun DeveloperCard(
    onGithub: () -> Unit,
    onTelegram: () -> Unit,
    onEmail: () -> Unit
) {
    GhostCard(
        borderColor     = NeonGreen.copy(0.3f),
        glowColor       = NeonGreen,
        backgroundColor = NeonGreen.copy(0.03f)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXXL)) {
            SectionLabel(text = "DESARROLLADOR", icon = Icons.Filled.Person, color = NeonGreen)

            // Avatar + nombre
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(NeonGreen.copy(0.3f), SurfaceElevated)
                            )
                        )
                        .border(2.dp, NeonGreen.copy(0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text  = "G",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            color      = NeonGreen,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Column {
                    Text(
                        text  = "Ghost Developer",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color      = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text  = "Desarrollador independiente Android",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Text(
                        text  = "Kotlin · Jetpack Compose · VpnService",
                        style = MonoStyle.copy(fontSize = 10.sp, color = NeonGreenDim)
                    )
                }
            }

            NeonDivider(color = BorderSubtle)

            // Links de contacto
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
                ContactLinkRow(
                    icon      = Icons.Filled.Code,
                    platform  = "GitHub",
                    handle    = "github.com/CHICO-CP",
                    color     = TextPrimary,
                    bgColor   = SurfaceElevated,
                    onClick   = onGithub
                )
                ContactLinkRow(
                    icon      = Icons.Filled.Send,
                    platform  = "Telegram",
                    handle    = "t.me/Gh0stDeveloper",
                    color     = NeonBlue,
                    bgColor   = NeonBlue.copy(0.08f),
                    onClick   = onTelegram
                )
                ContactLinkRow(
                    icon      = Icons.Filled.Email,
                    platform  = "Correo",
                    handle    = EMAIL,
                    color     = NeonAmber,
                    bgColor   = NeonAmber.copy(0.08f),
                    onClick   = onEmail
                )
            }
        }
    }
}

@Composable
private fun ContactLinkRow(
    icon: ImageVector,
    platform: String,
    handle: String,
    color: Color,
    bgColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(bgColor)
            .border(Dimens.BorderNormal, color.copy(0.2f), MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(Dimens.SpaceMD),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = platform,
            tint = color,
            modifier = Modifier.size(Dimens.IconMD)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = platform,
                style = MaterialTheme.typography.labelMedium.copy(
                    color      = color,
                    fontWeight = FontWeight.SemiBold
                )
            )
            Text(
                text  = handle,
                style = MonoStyle.copy(fontSize = 11.sp, color = TextTertiary)
            )
        }
        Icon(
            imageVector = Icons.Filled.OpenInNew,
            contentDescription = "Abrir",
            tint = color.copy(0.5f),
            modifier = Modifier.size(Dimens.IconSM)
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════
// LINKS RÁPIDOS
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun QuickLinksCard(
    onSourceCode: () -> Unit,
    onIssues: () -> Unit,
    onPrivacy: () -> Unit,
    onChangelog: () -> Unit
) {
    GhostCard(borderColor = BorderSubtle) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)) {
            SectionLabel(
                text  = "RECURSOS",
                icon  = Icons.Filled.Link,
                color = NeonPurple
            )

            val links = listOf(
                QuadLink(Icons.Filled.Code,          NeonCyan,   "Código Fuente",      "Ver repositorio en GitHub",          onSourceCode),
                QuadLink(Icons.Filled.BugReport,     NeonAmber,  "Reportar un bug",    "Abrir un issue en GitHub",           onIssues),
                QuadLink(Icons.Filled.PrivacyTip,    NeonBlue,   "Privacidad",         "Política de privacidad del proyecto", onPrivacy),
                QuadLink(Icons.Filled.NewReleases,   NeonGreen,  "Changelog",          "Historial de versiones",             onChangelog)
            )

            links.forEach { link ->
                QuickLinkRow(
                    icon     = link.icon,
                    color    = link.color,
                    title    = link.title,
                    subtitle = link.subtitle,
                    onClick  = link.onClick
                )
                if (link != links.last()) {
                    NeonDivider(
                        modifier = Modifier.padding(horizontal = Dimens.SpaceXS),
                        color    = BorderSubtle
                    )
                }
            }
        }
    }
}

private data class QuadLink(
    val icon: ImageVector,
    val color: Color,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit
)

@Composable
private fun QuickLinkRow(
    icon: ImageVector,
    color: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = Dimens.SpaceXS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(MaterialTheme.shapes.small)
                .background(color.copy(0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(Dimens.IconSM)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color      = TextPrimary,
                    fontWeight = FontWeight.Medium
                )
            )
            Text(
                text  = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(Dimens.IconSM)
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════
// FOOTER DE VERSIÓN
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun VersionFooter() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXS),
        modifier = Modifier.padding(vertical = Dimens.SpaceSM)
    ) {
        // Stack técnico
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TechBadge("Kotlin", NeonCyan)
            TechBadge("Compose", NeonBlue)
            TechBadge("Hilt", NeonPurple)
            TechBadge("Room", NeonGreen)
        }

        Spacer(Modifier.height(Dimens.SpaceXS))

        Text(
            text  = "Ghost Nexora VPN  v$APP_VERSION",
            style = MaterialTheme.typography.labelSmall.copy(
                color         = TextTertiary,
                letterSpacing = 1.sp
            )
        )
        Text(
            text  = "Desarrollado  por Ghost Developer",
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary,
            textAlign = TextAlign.Center
        )
        Text(
            text  = "© 2026 Ghost Developer · Todos los derechos reservados",
            style = MaterialTheme.typography.labelSmall.copy(
                color     = TextTertiary.copy(0.5f),
                fontSize  = 10.sp
            ),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TechBadge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(TagShape)
            .background(color.copy(0.08f))
            .border(Dimens.BorderThin, color.copy(0.3f), TagShape)
            .padding(horizontal = Dimens.SpaceSM, vertical = 3.dp)
    ) {
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color     = color,
                fontSize  = 10.sp,
                letterSpacing = 0.5.sp
            )
        )
    }
}

// ── Helper compartido ─────────────────────────────────────────────────────

@Composable
private fun SectionLabel(
    text: String,
    icon: ImageVector,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(Dimens.IconSM)
        )
        Text(
            text  = text,
            style = MaterialTheme.typography.labelSmall.copy(
                color         = color,
                letterSpacing = 2.sp,
                fontWeight    = FontWeight.SemiBold
            )
        )
    }
}
