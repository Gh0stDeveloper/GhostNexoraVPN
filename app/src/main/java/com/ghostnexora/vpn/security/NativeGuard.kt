package com.ghostnexora.vpn.security

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
}
