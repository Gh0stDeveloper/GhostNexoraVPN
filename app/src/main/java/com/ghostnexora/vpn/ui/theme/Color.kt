package com.ghostnexora.vpn.ui.theme

import androidx.compose.ui.graphics.Color

// ══════════════════════════════════════════════════════════════════════════
// GHOST NEXORA VPN — PALETA DE COLORES
// Paleta AMOLED con estados de conexión identificables.
// ══════════════════════════════════════════════════════════════════════════

// ── Fondos ────────────────────────────────────────────────────────────────
val BackgroundDeep    = Color(0xFF000000)   // Fondo más oscuro (raíz)
val BackgroundDark    = Color(0xFF000000)   // Fondo principal
val SurfaceDark       = Color(0xFF101010)   // Superficie base
val SurfaceVariant    = Color(0xFF191919)   // Tarjetas y contenedores
val SurfaceElevated   = Color(0xFF242424)   // Tarjetas elevadas
val SurfaceDim        = Color(0xFF141414)   // Overlay sutil

// ── Acentos y estados ──────────────────────────────────────────────────────────
val NeonCyan          = Color(0xFFF5F5F5)   // Acento principal
val NeonCyanDim       = Color(0xFFBDBDBD)   // Cyan apagado
val NeonCyanGlow      = Color(0x40FFFFFF)   // Cyan con transparencia (glow)
val NeonBlue          = Color(0xFFD6D6D6)   // Azul claro
val NeonBlueDark      = Color(0xFF999999)   // Azul oscuro
val NeonGreen         = Color(0xFF00E676)   // Verde conexión activa
val NeonGreenDim      = Color(0xFF00C853)   // Verde apagado
val NeonGreenGlow     = Color(0x4000E676)   // Verde glow
val NeonAmber         = Color(0xFFFFD740)   // Advertencia
val NeonRed           = Color(0xFFFF5252)   // Error / desconexión
val NeonRedDim        = Color(0xFFD50000)   // Rojo oscuro
val NeonRedGlow       = Color(0x40FF5252)   // Rojo glow
val NeonPurple        = Color(0xFFBABABA)   // Acento secundario

// ── Texto ─────────────────────────────────────────────────────────────────
val TextPrimary       = Color(0xFFFFFFFF)   // Texto principal
val TextSecondary     = Color(0xFFB3B3B3)   // Texto secundario
val TextTertiary      = Color(0xFF909090)   // Texto terciario / placeholder
val TextDisabled      = Color(0xFF414141)   // Texto deshabilitado
val TextOnAccent      = Color(0xFF000000)   // Texto sobre botones neon

// ── Bordes y Divisores ────────────────────────────────────────────────────
val BorderSubtle      = Color(0xFF292929)   // Borde muy sutil
val BorderNormal      = Color(0xFF414141)   // Borde estándar
val BorderAccent      = Color(0xFFF5F5F5)   // Borde resaltado (neon)

// ── Estados de Conexión ───────────────────────────────────────────────────
val StateConnected    = NeonGreen
val StateConnecting   = NeonAmber
val StateDisconnected = TextSecondary
val StateError        = NeonRed

// ── Material 3 — Color Scheme Tokens (Dark) ───────────────────────────────
// Primary
val Md3Primary        = NeonCyan
val Md3OnPrimary      = Color(0xFF171717)
val Md3PrimaryContainer    = Color(0xFF2B2B2B)
val Md3OnPrimaryContainer  = NeonCyan

// Secondary
val Md3Secondary      = NeonBlue
val Md3OnSecondary    = Color(0xFF171717)
val Md3SecondaryContainer  = Color(0xFF2B2B2B)
val Md3OnSecondaryContainer = NeonBlue

// Tertiary
val Md3Tertiary       = NeonPurple
val Md3OnTertiary     = Color(0xFF191919)
val Md3TertiaryContainer   = Color(0xFF2B2B2B)
val Md3OnTertiaryContainer = NeonPurple

// Error
val Md3Error          = NeonRed
val Md3OnError        = Color(0xFF690005)
val Md3ErrorContainer      = Color(0xFF93000A)
val Md3OnErrorContainer    = Color(0xFFFFDAD6)

// Background / Surface
val Md3Background     = BackgroundDark
val Md3OnBackground   = TextPrimary
val Md3Surface        = SurfaceDark
val Md3OnSurface      = TextPrimary
val Md3SurfaceVariant = SurfaceVariant
val Md3OnSurfaceVariant    = TextSecondary
val Md3Outline        = BorderNormal
val Md3OutlineVariant = BorderSubtle
val Md3InverseSurface      = Color(0xFFE6E6E6)
val Md3InverseOnSurface    = Color(0xFF191919)
val Md3InversePrimary      = Color(0xFF555555)
val Md3SurfaceTint    = NeonCyan
val Md3Scrim          = Color(0xFF000000)
