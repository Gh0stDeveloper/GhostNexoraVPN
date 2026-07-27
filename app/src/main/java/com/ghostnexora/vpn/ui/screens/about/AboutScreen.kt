package com.ghostnexora.vpn.ui.screens.about

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
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

private const val GITHUB_URL   = "https://github.com/Gh0stDeveloper"
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
                putExtra(Intent.EXTRA_SUBJECT, "Ghost Nexora VPN — Contact")
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

        AboutHeader()
        ProjectDescriptionCard()
        FeaturesCard()
        DeveloperCard(
            onGithub = { openUrl(GITHUB_URL) },
            onTelegram = { openUrl(TELEGRAM_URL) },
            onEmail = { openEmail() }
        )
        TechStackCard()
        VersionCard()
        Spacer(Modifier.height(Dimens.SpaceXXL))
    }
}

@Composable
private fun AboutHeader() {
    val transition = rememberInfiniteTransition(label = "about_header")
    val glowAlpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "header_glow"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .shadow(24.dp, CircleShape, ambientColor = NeonCyan, spotColor = NeonCyan)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(NeonCyan.copy(alpha = glowAlpha), NeonBlue.copy(alpha = 0.3f), SurfaceDark)
                    )
                )
                .border(1.dp, NeonCyan.copy(alpha = 0.8f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Security,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(Modifier.height(Dimens.SpaceLG))
        Text(
            text = "Ghost Nexora VPN",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = "Verified, encrypted Android tunneling",
            style = MaterialTheme.typography.bodyMedium,
            color = NeonCyan
        )
    }
}

@Composable
private fun ProjectDescriptionCard() {
    GhostCard(borderColor = BorderAccent) {
        Text(
            text = "ABOUT THE PROJECT",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.8.sp
            ),
            color = NeonCyan
        )
        Spacer(Modifier.height(Dimens.SpaceMD))
        Text(
            text = "Ghost Nexora VPN is a native Android VPN client built around verified outbound routing, encrypted profile storage, secure diagnostics, and modern SSH/Xray transports.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Start
        )
    }
}

@Composable
private fun FeaturesCard() {
    GhostCard {
        Text(
            text = "CORE CAPABILITIES",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.8.sp
            ),
            color = NeonCyan
        )
        Spacer(Modifier.height(Dimens.SpaceMD))
        FeatureRow(Icons.Filled.VpnLock, "Verified VPN routing", "Outbound access is checked before reporting connected")
        FeatureRow(Icons.Filled.Lock, "Encrypted profiles", "Android Keystore and authenticated encryption")
        FeatureRow(Icons.Filled.Sync, "Protected reconnection", "Kill Switch and controlled recovery")
        FeatureRow(Icons.Filled.Terminal, "Secure diagnostics", "Sanitized, filterable connection logs")
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.SpaceSM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)
    ) {
        Icon(icon, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
private fun DeveloperCard(
    onGithub: () -> Unit,
    onTelegram: () -> Unit,
    onEmail: () -> Unit
) {
    GhostCard {
        Text(
            text = "DEVELOPER AND CONTACT",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.8.sp
            ),
            color = NeonCyan
        )
        Spacer(Modifier.height(Dimens.SpaceMD))
        Text("Ghost Developer", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Text("@Gh0stDeveloper", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(Modifier.height(Dimens.SpaceMD))
        ContactButton(Icons.Filled.Code, "GitHub", "@Gh0stDeveloper", onGithub)
        ContactButton(Icons.AutoMirrored.Filled.Send, "Telegram", "@Gh0stDeveloper", onTelegram)
        ContactButton(Icons.Filled.Email, "Email", EMAIL, onEmail)
    }
}

@Composable
private fun ContactButton(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Dimens.SpaceSM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMD)
    ) {
        Icon(icon, contentDescription = null, tint = NeonCyan)
        Column(Modifier.weight(1f)) {
            Text(label, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
            Text(value, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = TextTertiary)
    }
}

@Composable
private fun TechStackCard() {
    GhostCard {
        Text(
            text = "TECHNOLOGY",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.8.sp
            ),
            color = NeonCyan
        )
        Spacer(Modifier.height(Dimens.SpaceMD))
        Text(
            text = "Kotlin · Jetpack Compose · Material 3 · Hilt · Room · DataStore · Android Keystore · JSch · Xray Core · NDK/CMake",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}

@Composable
private fun VersionCard() {
    GhostCard(borderColor = NeonGreen.copy(alpha = 0.35f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Verified, contentDescription = null, tint = NeonGreen)
            Spacer(Modifier.width(Dimens.SpaceMD))
            Column(Modifier.weight(1f)) {
                Text("Installed version", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                Text(APP_VERSION, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            }
            Text("Build ${BuildConfig.VERSION_CODE}", color = NeonGreen, style = MaterialTheme.typography.labelMedium)
        }
    }
}
