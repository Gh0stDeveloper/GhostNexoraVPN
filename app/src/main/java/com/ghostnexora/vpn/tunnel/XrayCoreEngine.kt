package com.ghostnexora.vpn.tunnel

import android.content.Context
import android.provider.Settings
import go.Seq
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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
    private var controller: CoreController? = null
    private var activeHealthCheckPort: Int? = null

    val isRunning: Boolean
        get() = controller?.isRunning == true

    /**
     * Comprueba el perfil sin TUN. Android conserva su conexión física si el
     * host, UUID, contraseña, SNI, Host, path o transporte son incorrectos.
     */
    @Synchronized
    fun verifyOutbound(config: String): OutboundCheck {
        initializeCore()
        onStatus("[CORE] Verificación nativa sin TUN iniciada")
        return measureAcrossEndpoints { url ->
            Libv2ray.measureOutboundDelay(config, url)
        }
    }

    @Synchronized
    fun start(config: String, tunFd: Int, healthCheckPort: Int? = null) {
        require(tunFd > 0) { "Descriptor TUN inválido" }
        healthCheckPort?.let {
            require(it in 1..65535) { "Puerto de comprobación Xray inválido" }
        }
        if (isRunning) error("Xray Core ya está ejecutándose")

        initializeCore()
        onStatus("[CORE] Creando controlador AndroidLibXrayLite")
        val newController = Libv2ray.newCoreController(CoreCallback())
        controller = newController

        try {
            onStatus("[TUN] Entregando la interfaz Android al core nativo")
            newController.startLoop(config, tunFd)
            if (!newController.isRunning) {
                error("Xray Core no pudo iniciar el loop TUN")
            }
            activeHealthCheckPort = healthCheckPort
            onStatus("[CORE] Xray Core activo")
        } catch (error: Throwable) {
            controller = null
            activeHealthCheckPort = null
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
        val healthCheckPort = activeHealthCheckPort
        if (healthCheckPort != null) {
            return verifySshSocksOutbound(healthCheckPort)
        }
        onStatus("[NETWORK] Comprobando Internet a través del outbound activo")
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
        activeHealthCheckPort = null
        runCatching {
            if (activeController.isRunning) activeController.stopLoop()
        }
    }

    fun version(): String = runCatching { Libv2ray.checkVersionX() }.getOrDefault("desconocida")

    private fun verifySshSocksOutbound(healthCheckPort: Int): OutboundCheck {
        var lastError: Throwable? = null
        onStatus("[NETWORK] Comprobando ruta Xray → SOCKS → direct-tcpip SSH")

        for ((index, target) in Socks5OutboundProbe.targets.withIndex()) {
            try {
                onStatus(
                    "[SOCKS] Prueba real ${index + 1}/${Socks5OutboundProbe.targets.size} · " +
                        "TLS remoto por SSH"
                )
                val result = Socks5OutboundProbe.measure(healthCheckPort, target)
                onStatus(
                    "[SOCKS] Ruta bidireccional verificada · ${result.tlsProtocol} · " +
                        "${result.cipherSuite} · ${result.latencyMs} ms"
                )
                return OutboundCheck(result.latencyMs, target.endpoint)
            } catch (error: Throwable) {
                lastError = error
                onStatus(
                    "[SOCKS] WARN · prueba ${index + 1} falló · " +
                        error.message.orEmpty().replace('\n', ' ').take(180)
                )
            }
        }

        throw IllegalStateException(
            "La ruta Xray → SOCKS → SSH no completó el handshake TLS remoto: " +
                lastError?.message.orEmpty().replace('\n', ' ').take(180),
            lastError
        )
    }

    private fun measureAcrossEndpoints(measure: (String) -> Long): OutboundCheck {
        var lastError: Throwable? = null

        for ((index, endpoint) in CONNECTIVITY_TEST_URLS.withIndex()) {
            try {
                onStatus("[NETWORK] Prueba de salida ${index + 1}/${CONNECTIVITY_TEST_URLS.size}")
                val latency = measure(endpoint)
                if (latency >= 0L) {
                    onStatus("[NETWORK] Salida verificada · $latency ms")
                    return OutboundCheck(latencyMs = latency, endpoint = endpoint)
                }
                lastError = IllegalStateException("La prueba devolvió latencia inválida: $latency")
            } catch (error: Throwable) {
                lastError = error
                onStatus(
                    "[NETWORK] WARN · prueba ${index + 1} falló · " +
                        error.message.orEmpty().replace('\n', ' ').take(160)
                )
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
        if (initialized.get()) return
        synchronized(initializationLock) {
            if (initialized.get()) return
            try {
                onStatus("[CORE] Inicializando entorno nativo una sola vez")
                Seq.setContext(appContext)
                val assetsDir = appContext.filesDir.resolve("xray-assets").apply { mkdirs() }
                prepareEmbeddedGeoData(assetsDir)
                val missingAssets = REQUIRED_ASSETS.filter { name ->
                    assetsDir.resolve(name).let { !it.isFile || it.length() <= 0L }
                }
                check(missingAssets.isEmpty()) {
                    "Faltan recursos Xray: ${missingAssets.joinToString()}"
                }
                onStatus("[CORE] Recursos geoip/geosite verificados")
                Libv2ray.initCoreEnv(assetsDir.absolutePath, deviceKey())
                initialized.set(true)
                onStatus("[CORE] Entorno nativo inicializado")
            } catch (error: Throwable) {
                initialized.set(false)
                throw error
            }
        }
    }

    private fun prepareEmbeddedGeoData(assetDirectory: File) {
        REQUIRED_ASSETS.forEach { fileName ->
            val destination = assetDirectory.resolve(fileName)
            if (destination.isFile && destination.length() > 0L) return@forEach

            val staged = File.createTempFile("$fileName-", ".tmp", assetDirectory)
            try {
                appContext.assets.open(fileName).use { input ->
                    staged.outputStream().use(input::copyTo)
                }
                check(staged.length() > 0L) { "Embedded $fileName is empty" }
                try {
                    Files.move(
                        staged.toPath(),
                        destination.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(
                        staged.toPath(),
                        destination.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                }
            } finally {
                staged.delete()
            }
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
            onStatus("[CORE] Señal nativa de inicio recibida")
            return 0
        }

        override fun shutdown(): Long {
            onStatus("[CORE] Señal nativa de cierre recibida")
            return 0
        }

        override fun onEmitStatus(code: Long, message: String?): Long {
            message?.takeIf { it.isNotBlank() }?.let {
                onStatus("[CORE] Evento nativo $code · $it")
            }
            return 0
        }
    }

    private companion object {
        val initialized = AtomicBoolean(false)
        val initializationLock = Any()
        val REQUIRED_ASSETS = listOf("geoip.dat", "geosite.dat")
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
