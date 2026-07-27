package com.ghostnexora.vpn.tunnel

import com.jcraft.jsch.JSch
import com.jcraft.jsch.Logger
import com.jcraft.jsch.Random
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android-safe JSch bootstrap.
 *
 * JSch resolves cryptographic providers from class names. Some Android builds
 * can fail to resolve an implementation stored in a secondary DEX even though
 * the class is packaged. This application-owned provider is referenced
 * directly, registered explicitly and verified before any SSH session starts.
 */
object JschRuntime {
    private val installed = AtomicBoolean(false)

    fun install(onStatus: (String) -> Unit = {}) {
        if (installed.compareAndSet(false, true)) {
            val provider = AndroidSecureRandomProvider()
            val probe = ByteArray(32)
            provider.fill(probe, 0, probe.size)
            check(probe.any { it.toInt() != 0 }) { "Android SecureRandom provider did not initialize" }

            val providerClass = AndroidSecureRandomProvider::class.java
            Class.forName(providerClass.name, true, providerClass.classLoader)
            JSch.setConfig("random", providerClass.name)
        }

        JSch.setLogger(SanitizedJschLogger(onStatus))
        onStatus("[SSH] JSch ${JSch.VERSION} listo · SecureRandom Android verificado")
    }

    fun configuredRandomProvider(): String = JSch.getConfig("random")
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
