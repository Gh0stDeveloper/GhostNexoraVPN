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
 * Adaptador pequeño y aislado alrededor de AndroidLibXrayLite.
 *
 * El core recibe directamente el file descriptor creado por VpnService, por
 * lo que el estado "Connected" solo se publica cuando Xray confirma que el
 * loop está realmente activo.
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

    @Synchronized
    fun stop() {
        val activeController = controller ?: return
        controller = null
        runCatching {
            if (activeController.isRunning) activeController.stopLoop()
        }
    }

    fun version(): String = runCatching { Libv2ray.checkVersionX() }.getOrDefault("desconocida")

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
}
