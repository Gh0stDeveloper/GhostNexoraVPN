package com.ghostnexora.vpn.tunnel

import android.content.Context
import android.provider.Settings
import go.Seq
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Adaptador aislado alrededor de AndroidLibXrayLite.
 *
 * Iniciar el proceso Xray no demuestra que el servidor remoto acepte el
 * perfil. Por eso esta clase puede validar el outbound antes de crear el TUN y
 * volver a comprobarlo mediante la instancia activa antes de publicar el
 * estado Connected.
 */
class XrayCoreEngine(
    context: Context,
    private val onStatus: (String) -> Unit = {}
) {
    private val appContext = context.applicationContext
    private val initialized = AtomicBoolean(false)
    private var controller: CoreController? = null

    val isRunning: Boolean
        get() = controller?.isRunning == true

    /**
     * Comprueba el perfil sin TUN. Android conserva su conexión física si el
     * host, UUID, contraseña, SNI, Host, path o transporte son incorrectos.
     */
    @Synchronized
    fun verifyOutbound(config: String): OutboundCheck {
        initializeCore()
        return measureAcrossEndpoints { url ->
            Libv2ray.measureOutboundDelay(config, url)
        }
    }

    @Synchronized
    fun start(config: String, tunFd: Int) {
        require(tunFd > 0) { "Descriptor TUN inválido" }
        if (isRunning) error("Xray Core ya está ejecutándose")

        initializeCore()
        val newController = Libv2ray.newCoreController(CoreCallback())
        controller = newController

        try {
            newController.startLoop(config, tunFd)
            if (!newController.isRunning) {
                error("Xray Core no pudo iniciar el loop TUN")
            }
            onStatus("Xray Core activo")
        } catch (error: Throwable) {
            controller = null
            runCatching { newController.stopLoop() }
            throw IllegalStateException(
                error.message?.takeIf { it.isNotBlank() } ?: "Fallo iniciando Xray Core",
                error
            )
        }
    }

    /** Verifica que la instancia activa realmente puede alcanzar Internet. */
    @Synchronized
    fun verifyActiveOutbound(): OutboundCheck {
        val activeController = controller?.takeIf { it.isRunning }
            ?: error("Xray Core no está activo para validar la salida")
        return measureAcrossEndpoints { url ->
            activeController.measureDelay(url)
        }
    }

    /**
     * Reads and resets the proxy outbound counters exposed by the bundled
     * core. These values exclude updater, UI and other process traffic.
     */
    @Synchronized
    fun drainProxyTraffic(): XrayTrafficDelta {
        val activeController = controller?.takeIf { it.isRunning }
            ?: return XrayTrafficDelta()
        return XrayTrafficDelta(
            receivedBytes = runCatching {
                activeController.queryStats("proxy", "downlink")
            }.getOrDefault(0L).coerceAtLeast(0L),
            sentBytes = runCatching {
                activeController.queryStats("proxy", "uplink")
            }.getOrDefault(0L).coerceAtLeast(0L)
        )
    }

    @Synchronized
    fun stop() {
        val activeController = controller ?: return
        controller = null
        runCatching {
            if (activeController.isRunning) activeController.stopLoop()
        }
    }

    fun version(): String = runCatching { Libv2ray.checkVersionX() }.getOrDefault("desconocida")

    private fun measureAcrossEndpoints(measure: (String) -> Long): OutboundCheck {
        var lastError: Throwable? = null

        for (endpoint in CONNECTIVITY_TEST_URLS) {
            try {
                val latency = measure(endpoint)
                if (latency >= 0L) {
                    return OutboundCheck(latencyMs = latency, endpoint = endpoint)
                }
                lastError = IllegalStateException("La prueba devolvió latencia inválida: $latency")
            } catch (error: Throwable) {
                lastError = error
            }
        }

        val detail = lastError?.message
            ?.replace('\n', ' ')
            ?.take(180)
            .orEmpty()
        val suffix = detail.takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty()
        throw IllegalStateException(
            "El servidor o la configuración no entregan acceso a Internet$suffix",
            lastError
        )
    }

    private fun initializeCore() {
        if (!initialized.compareAndSet(false, true)) return
        try {
            Seq.setContext(appContext)
            val assetsDir = appContext.filesDir.resolve("xray-assets").apply { mkdirs() }
            Libv2ray.initCoreEnv(assetsDir.absolutePath, deviceKey())
        } catch (error: Throwable) {
            initialized.set(false)
            throw error
        }
    }

    private fun deviceKey(): String {
        val androidId = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
            .orEmpty()
            .ifBlank { appContext.packageName }
        return MessageDigest.getInstance("SHA-256")
            .digest((appContext.packageName + ':' + androidId).toByteArray())
            .take(16)
            .joinToString("") { "%02x".format(it) }
    }

    private inner class CoreCallback : CoreCallbackHandler {
        override fun startup(): Long {
            onStatus("Core iniciado")
            return 0
        }

        override fun shutdown(): Long {
            onStatus("Core detenido")
            return 0
        }

        override fun onEmitStatus(code: Long, message: String?): Long {
            message?.takeIf { it.isNotBlank() }?.let { onStatus("[$code] $it") }
            return 0
        }
    }

    private companion object {
        val CONNECTIVITY_TEST_URLS = listOf(
            "https://cp.cloudflare.com/generate_204",
            "https://www.gstatic.com/generate_204"
        )
    }
}

data class OutboundCheck(
    val latencyMs: Long,
    val endpoint: String
)

data class XrayTrafficDelta(
    val receivedBytes: Long = 0L,
    val sentBytes: Long = 0L
)
