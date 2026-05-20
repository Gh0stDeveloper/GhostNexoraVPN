package com.ghostnexora.vpn.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.NotificationManagerCompat

// ══════════════════════════════════════════════════════════════════════════
// GHOST NEXORA VPN — GESTOR DE PERMISOS
// Centraliza todas las verificaciones y solicitudes de permisos
// ══════════════════════════════════════════════════════════════════════════

object PermissionHelper {

    // ── VPN ───────────────────────────────────────────────────────────────

    /**
     * Verifica si ya se concedió permiso VPN.
     * Si prepare() devuelve null, el permiso ya fue otorgado.
     */
    fun hasVpnPermission(context: Context): Boolean =
        VpnService.prepare(context) == null

    /**
     * Devuelve el Intent para solicitar permiso VPN.
     * Debe lanzarse con startActivityForResult o ActivityResultLauncher.
     * Devuelve null si el permiso ya fue concedido.
     */
    fun vpnPermissionIntent(context: Context): Intent? =
        VpnService.prepare(context)

    // ── Overlay (Ventana flotante) ─────────────────────────────────────────

    /**
     * Verifica si la app puede dibujar sobre otras apps.
     * Requerido para el FloatingWindowService.
     */
    fun hasOverlayPermission(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    /**
     * Intent para abrir la pantalla de permiso de overlay en Ajustes.
     */
    fun overlayPermissionIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:${context.packageName}")
        )

    // ── Notificaciones (Android 13+) ─────────────────────────────────────

    /**
     * Verifica si las notificaciones están habilitadas.
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        } else {
            true // Antes de Android 13 no se requiere permiso explícito
        }
    }

    // ── Instalación de APKs desde fuentes desconocidas ───────────────────

    fun hasInstallUnknownAppsPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun installUnknownAppsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
        }

    // ── Optimización de batería ───────────────────────────────────────────

    /**
     * Verifica si la app está excluida de las optimizaciones de batería.
     * Necesario para mantener el VPN Service activo en segundo plano.
     */
    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Intent para solicitar exclusión de optimización de batería.
     */
    fun batteryOptimizationIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
        }

    // ── Resumen del estado de permisos ────────────────────────────────────

    /**
     * Devuelve un resumen de todos los permisos relevantes.
     * Útil para la pantalla de Ajustes y diagnóstico.
     */
    fun permissionStatus(context: Context): PermissionStatus =
        PermissionStatus(
            vpn          = hasVpnPermission(context),
            overlay      = hasOverlayPermission(context),
            notification = hasNotificationPermission(context),
            battery      = isBatteryOptimizationIgnored(context),
            unknownSources = hasInstallUnknownAppsPermission(context)
        )

    fun storagePermissions(): Array<String> = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
        arrayOf(
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    } else {
        emptyArray()
    }

    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            storagePermissions().all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        } else {
            true
        }
    }
}

/**
 * Estado resumido de todos los permisos necesarios.
 */
data class PermissionStatus(
    val vpn: Boolean,
    val overlay: Boolean,
    val notification: Boolean,
    val battery: Boolean,
    val unknownSources: Boolean = false
) {
    /** True si todos los permisos críticos están concedidos */
    val allGranted: Boolean get() = vpn && notification

    /** True si los permisos opcionales también están concedidos */
    val allOptionalGranted: Boolean get() = overlay && battery && unknownSources

    /** Descripción de qué falta */
    fun missingList(): List<String> = buildList {
        if (!vpn)            add("Permiso VPN")
        if (!notification)    add("Notificaciones")
        if (!overlay)        add("Ventana flotante (overlay)")
        if (!battery)        add("Optimización de batería")
        if (!unknownSources)  add("Instalar apps desconocidas")
    }
}
