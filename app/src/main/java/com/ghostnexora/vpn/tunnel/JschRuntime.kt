package com.ghostnexora.vpn.tunnel

import com.jcraft.jsch.AndroidRandomBridge
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Logger
import com.jcraft.jsch.Random
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean

/** Android-safe JSch bootstrap without reflective random-provider loading. */
object JschRuntime {
    private val installed = AtomicBoolean(false)

    fun install(onStatus: (String) -> Unit = {}) {
        if (installed.compareAndSet(false, true)) {
            val provider = AndroidSecureRandomProvider()
            val probe = ByteArray(32)
            provider.fill(probe, 0, probe.size)
            check(probe.any { it.toInt() != 0 }) { "Android SecureRandom provider did not initialize" }
            probe.fill(0)

            // Keep the named configuration for any other JSch code path, but
            // install the instance directly so Session.connect() never reaches
            // Class.forName(getConfig("random")).
            JSch.setConfig("random", AndroidSecureRandomProvider::class.java.name)
            AndroidRandomBridge.install(provider)
            check(AndroidRandomBridge.isInstalled()) { "JSch random bridge did not install" }
        }

        verifyEssentialProviders()
        JSch.setLogger(SanitizedJschLogger(onStatus))
        onStatus("[SSH] JSch ${JSch.VERSION} listo · algoritmos esenciales verificados")
    }

    fun configuredRandomProvider(): String = JSch.getConfig("random")
    fun isDirectProviderInstalled(): Boolean = AndroidRandomBridge.isInstalled()

    /**
     * JSch resuelve estos proveedores mediante nombres de clase guardados en
     * configuración. La comprobación temprana convierte una eliminación de R8
     * en un error identificable antes de abrir el socket de producción.
     */
    fun verifyEssentialProviders() {
        ESSENTIAL_PROVIDER_KEYS.forEach { algorithm ->
            val className = JSch.getConfig(algorithm)
            check(className.isNotBlank()) { "JSch no registró el algoritmo $algorithm" }
            runCatching {
                Class.forName(className, false, JschRuntime::class.java.classLoader)
            }.getOrElse { error ->
                throw IllegalStateException(
                    "Proveedor JSch ausente para $algorithm: $className",
                    error
                )
            }
        }
    }

    internal val essentialProviderKeys: List<String>
        get() = ESSENTIAL_PROVIDER_KEYS

    private val ESSENTIAL_PROVIDER_KEYS = listOf(
        "ecdh-sha2-nistp256",
        "diffie-hellman-group-exchange-sha256",
        "aes256-ctr",
        "hmac-sha2-512",
        "userauth.password",
        "userauth.keyboard-interactive"
    )
}

/** Directly referenced JSch random provider backed by Android/Java SecureRandom. */
class AndroidSecureRandomProvider : Random {
    private val secureRandom = SecureRandom()

    override fun fill(buffer: ByteArray, start: Int, length: Int) {
        require(start >= 0 && length >= 0 && start + length <= buffer.size) {
            "Invalid random buffer range"
        }
        if (length == 0) return
        val generated = ByteArray(length)
        secureRandom.nextBytes(generated)
        generated.copyInto(buffer, destinationOffset = start)
        generated.fill(0)
    }
}

private class SanitizedJschLogger(
    private val onStatus: (String) -> Unit
) : Logger {
    override fun isEnabled(level: Int): Boolean = level >= Logger.INFO

    override fun log(level: Int, message: String?) {
        val clean = message
            ?.replace(Regex("(?i)(password|passphrase|authorization)\\s*[:=]\\s*\\S+"), "$1=<redacted>")
            ?.replace('\n', ' ')
            ?.take(240)
            ?.takeIf(String::isNotBlank)
            ?: return
        val label = when (level) {
            Logger.ERROR -> "ERROR"
            Logger.WARN -> "WARN"
            else -> "INFO"
        }
        onStatus("[SSH] $label · $clean")
    }
}
