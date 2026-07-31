package com.ghostnexora.vpn.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deriva el material portable usado por archivos GNX3 sin contraseña.
 *
 * La firma del APK liga esos archivos a builds firmadas por el mismo
 * desarrollador. El fragmento nativo evita almacenar la clave final como una
 * constante DEX. No convierte al cliente en un HSM: un atacante que controle
 * el APK y el proceso puede reconstruir este material.
 */
@Singleton
class AppManagedConfigKeyProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun obtain(): ByteArray {
        val fragment = NativeGuard.gnx3KeyFragment()
        return try {
            val digest = MessageDigest.getInstance("SHA-512")
            digest.update("GhostNexoraVPN|GNX3|official-build".toByteArray(Charsets.UTF_8))
            digest.update(context.packageName.toByteArray(Charsets.UTF_8))
            digest.update(fragment)
            signingCertificates()
                .sortedWith(BYTE_ARRAY_COMPARATOR)
                .forEach { certificate -> digest.update(certificate) }
            digest.digest().copyOf(32)
        } finally {
            NativeGuard.wipe(fragment)
        }
    }

    @Suppress("DEPRECATION")
    private fun signingCertificates(): List<ByteArray> {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
        } else {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES
            )
        }
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageInfo.signingInfo
                ?: error("El APK no contiene información de firma")
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            packageInfo.signatures
        }
        val verified = signatures?.toList().orEmpty()
        require(verified.isNotEmpty()) { "No se pudo verificar la firma del APK" }
        return verified.map { it.toByteArray() }
    }

    private companion object {
        val BYTE_ARRAY_COMPARATOR = Comparator<ByteArray> { left, right ->
            val limit = minOf(left.size, right.size)
            for (index in 0 until limit) {
                val comparison = (left[index].toInt() and 0xff)
                    .compareTo(right[index].toInt() and 0xff)
                if (comparison != 0) return@Comparator comparison
            }
            left.size.compareTo(right.size)
        }
    }
}
