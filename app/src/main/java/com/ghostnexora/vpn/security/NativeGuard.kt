package com.ghostnexora.vpn.security

import android.os.Debug

/**
 * Puente JNI pequeño para operaciones auxiliares de endurecimiento.
 *
 * No contiene claves criptográficas. Las claves reales se generan aleatoriamente
 * o viven dentro de Android Keystore. La capa nativa se usa para separar el AAD
 * de dominio y limpiar buffers sensibles cuando dejan de ser necesarios.
 */
object NativeGuard {
    private const val FALLBACK_DOMAIN = "GhostNexoraVPN|GNX2|secure-config"

    private val nativeLoaded: Boolean = runCatching {
        System.loadLibrary("ghostguard")
        true
    }.getOrDefault(false)

    @JvmStatic
    private external fun nativeDomainSeparator(): ByteArray

    @JvmStatic
    private external fun nativeWipe(buffer: ByteArray)

    @JvmStatic
    private external fun nativeGnx3KeyFragment(): ByteArray

    @JvmStatic
    private external fun nativeRuntimeCompromised(): Boolean

    fun domainSeparator(): ByteArray = if (nativeLoaded) {
        runCatching { nativeDomainSeparator() }
            .getOrElse { FALLBACK_DOMAIN.toByteArray(Charsets.UTF_8) }
    } else {
        FALLBACK_DOMAIN.toByteArray(Charsets.UTF_8)
    }

    fun wipe(vararg buffers: ByteArray?) {
        buffers.filterNotNull().forEach { buffer ->
            if (nativeLoaded) {
                runCatching { nativeWipe(buffer) }
                    .onFailure { buffer.fill(0) }
            } else {
                buffer.fill(0)
            }
        }
    }

    /**
     * Fragmento de diversificación para el modo GNX3 administrado por la app.
     * No se trata como una clave maestra: se combina con la firma del APK y el
     * paquete. Cualquier secreto incluido en un cliente distribuido puede ser
     * recuperado por un atacante con control total del dispositivo.
     */
    fun gnx3KeyFragment(): ByteArray = if (nativeLoaded) {
        runCatching { nativeGnx3KeyFragment() }
            .getOrElse(::fallbackGnx3Fragment)
    } else {
        fallbackGnx3Fragment()
    }

    fun isInstrumentationDetected(): Boolean {
        if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) return true
        return nativeLoaded && runCatching { nativeRuntimeCompromised() }.getOrDefault(false)
    }

    fun requireProtectedRuntime() {
        check(!isInstrumentationDetected()) {
            "Entorno de instrumentación detectado; el perfil bloqueado no se abrirá"
        }
    }

    private fun fallbackGnx3Fragment(): ByteArray =
        byteArrayOf(
            0x6f, 0x05, 0x50, 0x38, 0x7d, 0x11, 0x64, 0x2e,
            0x17, 0x6a, 0x08, 0x5d, 0x3c, 0x21, 0x73, 0x49,
            0x0f, 0x62, 0x55, 0x14, 0x7b, 0x28, 0x03, 0x6e,
            0x19, 0x45, 0x2a, 0x72, 0x59, 0x0c, 0x67, 0x31
        )
}
