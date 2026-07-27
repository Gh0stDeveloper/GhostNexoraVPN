package com.ghostnexora.vpn.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// ══════════════════════════════════════════════════════════════════════════
// GHOST NEXORA VPN — COLOR SCHEME OSCURO
// ══════════════════════════════════════════════════════════════════════════

private val GhostDarkColorScheme = darkColorScheme(
    primary                = Md3Primary,
    onPrimary              = Md3OnPrimary,
    primaryContainer       = Md3PrimaryContainer,
    onPrimaryContainer     = Md3OnPrimaryContainer,

    secondary              = Md3Secondary,
    onSecondary            = Md3OnSecondary,
    secondaryContainer     = Md3SecondaryContainer,
    onSecondaryContainer   = Md3OnSecondaryContainer,

    tertiary               = Md3Tertiary,
    onTertiary             = Md3OnTertiary,
    tertiaryContainer      = Md3TertiaryContainer,
    onTertiaryContainer    = Md3OnTertiaryContainer,

    error                  = Md3Error,
    onError                = Md3OnError,
    errorContainer         = Md3ErrorContainer,
    onErrorContainer       = Md3OnErrorContainer,

    background             = Md3Background,
    onBackground           = Md3OnBackground,

    surface                = Md3Surface,
    onSurface              = Md3OnSurface,
    surfaceVariant         = Md3SurfaceVariant,
    onSurfaceVariant       = Md3OnSurfaceVariant,

    outline                = Md3Outline,
    outlineVariant         = Md3OutlineVariant,
    inverseSurface         = Md3InverseSurface,
    inverseOnSurface       = Md3InverseOnSurface,
    inversePrimary         = Md3InversePrimary,
    surfaceTint            = Md3SurfaceTint,
    scrim                  = Md3Scrim
)

// ══════════════════════════════════════════════════════════════════════════
// COMPOSABLE PRINCIPAL DEL TEMA
// ══════════════════════════════════════════════════════════════════════════

/**
 * Tema global de Ghost Nexora VPN.
 *
 * @param darkTheme   Fuerza el tema oscuro; por defecto siempre oscuro.
 * @param dynamicColor Si es true y Android 12+, usa colores del sistema
 *                     (deshabilitado por defecto para mantener la identidad neon).
 * @param content     Contenido Compose anidado.
 */
@Composable
fun GhostNexoraTheme(
    darkTheme: Boolean = true, // La app es siempre oscura por diseño
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Dynamic Color (Material You) — deshabilitado por defecto
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            dynamicDarkColorScheme(context)
        }
        // Siempre oscuro
        else -> GhostDarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = GhostTypography,
        shapes = GhostShapes,
        content = content
    )
}

// ══════════════════════════════════════════════════════════════════════════
// EXTENSIONES DE ACCESO RÁPIDO
// Permite usar MaterialTheme.ghostColors.* en cualquier Composable
// ══════════════════════════════════════════════════════════════════════════

/**
 * Acceso rápido a colores neon personalizados fuera del color scheme de M3.
 * Uso: MaterialTheme.ghostColors.neonGreen
 */
object GhostColors {
    val neonCyan        get() = NeonCyan
    val neonCyanDim     get() = NeonCyanDim
    val neonCyanGlow    get() = NeonCyanGlow
    val neonBlue        get() = NeonBlue
    val neonGreen       get() = NeonGreen
    val neonGreenDim    get() = NeonGreenDim
    val neonGreenGlow   get() = NeonGreenGlow
    val neonRed         get() = NeonRed
    val neonRedGlow     get() = NeonRedGlow
    val neonAmber       get() = NeonAmber
    val neonPurple      get() = NeonPurple
    val backgroundDeep  get() = BackgroundDeep
    val surfaceElevated get() = SurfaceElevated
    val surfaceDim      get() = SurfaceDim
    val borderAccent    get() = BorderAccent
    val borderSubtle    get() = BorderSubtle
    val textPrimary     get() = TextPrimary
    val textSecondary   get() = TextSecondary
    val textTertiary    get() = TextTertiary
    val textOnAccent    get() = TextOnAccent

    // Estados de conexión
    val connected       get() = StateConnected
    val connecting      get() = StateConnecting
    val disconnected    get() = StateDisconnected
    val error           get() = StateError
}

val MaterialTheme.ghostColors: GhostColors
    get() = GhostColors
