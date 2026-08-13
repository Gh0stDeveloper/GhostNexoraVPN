package com.ghostnexora.vpn.diagnostics

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.ghostnexora.vpn.data.model.NetworkPreferences
import com.ghostnexora.vpn.data.model.VpnProfile
import com.ghostnexora.vpn.data.repository.ProfileRepository
import com.ghostnexora.vpn.tunnel.ConnectionErrorCatalog
import com.ghostnexora.vpn.tunnel.TlsTransport
import com.ghostnexora.vpn.tunnel.TunnelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.security.cert.X509Certificate


enum class DiagnosticStatus { RUNNING, PASSED, FAILED, SKIPPED }

data class DiagnosticStep(
    val id: String,
    val label: String,
    val status: DiagnosticStatus,
    val detail: String,
    val latencyMs: Long? = null,
    val errorCode: String? = null,
    val solution: String? = null
)

data class DiagnosticReport(
    val profileName: String,
    val startedAt: Long,
    val finishedAt: Long,
    val steps: List<DiagnosticStep>
) {
    val successful: Boolean get() = steps.none { it.status == DiagnosticStatus.FAILED }

    fun asText(): String = buildString {
        appendLine("Ghost Nexora VPN — Connection diagnostics")
        appendLine("Profile: $profileName")
        appendLine("Started: $startedAt")
        appendLine("Finished: $finishedAt")
        appendLine("Result: ${if (successful) "PASSED" else "FAILED"}")
        appendLine()
        steps.forEach { step ->
            append("[${step.status}] [${step.id}] ${step.label}: ${step.detail}")
            step.latencyMs?.let { append(" · ${it} ms") }
            step.errorCode?.let { append(" · code=$it") }
            appendLine()
            step.solution?.let { appendLine("  Solution: $it") }
        }
    }
}

/**
 * Runs non-destructive checks. It never establishes Android VPN routes.
 * The final outbound test starts SSH/Xray only in preflight mode and closes it.
 */
class ConnectionDiagnosticsEngine(
    context: Context,
    private val repository: ProfileRepository
) {
    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    suspend fun run(
        profile: VpnProfile,
        preferences: NetworkPreferences,
        onStep: suspend (DiagnosticStep) -> Unit = {}
    ): DiagnosticReport = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val steps = mutableListOf<DiagnosticStep>()

        suspend fun record(step: DiagnosticStep) {
            steps += step
            onStep(step)
            val level = if (step.status == DiagnosticStatus.FAILED) {
                com.ghostnexora.vpn.data.model.LogLevel.ERROR
            } else {
                com.ghostnexora.vpn.data.model.LogLevel.INFO
            }
            repository.log(
                level,
                "[${step.id}] ${step.label} · ${step.detail}" +
                    (step.errorCode?.let { " · $it" } ?: ""),
                profile.id,
                "DIAGNOSTIC"
            )
        }

        val physical = runCatching { checkPhysicalNetwork() }
        if (physical.isFailure) {
            val failure = ConnectionErrorCatalog.classify(physical.exceptionOrNull()!!, profile)
            record(failed("NETWORK", "Physical network", failure))
            return@withContext DiagnosticReport(profile.name, startedAt, System.currentTimeMillis(), steps)
        }
        record(passed("NETWORK", "Physical network", physical.getOrThrow()))

        val dnsStart = System.nanoTime()
        val resolved = runCatching { InetAddress.getAllByName(profile.host).toList() }
        if (resolved.isFailure) {
            val failure = ConnectionErrorCatalog.classify(resolved.exceptionOrNull()!!, profile)
            record(failed("DNS", "Server name resolution", failure))
            return@withContext DiagnosticReport(profile.name, startedAt, System.currentTimeMillis(), steps)
        }
        record(
            passed(
                "DNS",
                "Server name resolution",
                resolved.getOrThrow().joinToString { it.hostAddress.orEmpty() },
                elapsedMs(dnsStart)
            )
        )

        val tcpHost = if (profile.selectedMode.requiresProxy) profile.proxy.host else profile.host
        val tcpPort = if (profile.selectedMode.requiresProxy) profile.proxy.port else profile.port
        val tcpStart = System.nanoTime()
        val tcp = runCatching { Socket().use { it.connect(InetSocketAddress(tcpHost, tcpPort), 8_000) } }
        if (tcp.isFailure) {
            val failure = ConnectionErrorCatalog.classify(tcp.exceptionOrNull()!!, profile)
            record(failed(if (profile.selectedMode.requiresProxy) "PROXY" else "TCP", "TCP reachability", failure))
            return@withContext DiagnosticReport(profile.name, startedAt, System.currentTimeMillis(), steps)
        }
        record(passed("TCP", "TCP reachability", "$tcpHost:$tcpPort accepted a connection", elapsedMs(tcpStart)))

        if (profile.selectedMode.usesTls && !profile.selectedMode.requiresProxy) {
            val tlsStart = System.nanoTime()
            val tls = runCatching { inspectTls(profile) }
            if (tls.isFailure) {
                val failure = ConnectionErrorCatalog.classify(tls.exceptionOrNull()!!, profile)
                record(failed("TLS", "TLS and SNI", failure))
                return@withContext DiagnosticReport(profile.name, startedAt, System.currentTimeMillis(), steps)
            }
            record(passed("TLS", "TLS and SNI", tls.getOrThrow(), elapsedMs(tlsStart)))
        } else {
            record(
                DiagnosticStep(
                    "TLS",
                    "TLS and SNI",
                    DiagnosticStatus.SKIPPED,
                    if (profile.selectedMode.usesTls) "Validated by the configured proxy/transport chain" else "Not required by this profile"
                )
            )
        }

        record(
            passed(
                "SETTINGS",
                "Routing configuration",
                "${preferences.ipMode.label} · MTU ${preferences.validatedMtu} · ${preferences.dnsMode.label}"
            )
        )

        val tunnelManager = TunnelManager(appContext)
        val outboundStart = System.nanoTime()
        val outbound = runCatching { tunnelManager.verify(profile, preferences) }
        if (outbound.isFailure) {
            val failure = ConnectionErrorCatalog.classify(outbound.exceptionOrNull()!!, profile)
            record(failed("OUTBOUND", "SSH/Xray outbound Internet", failure))
        } else {
            record(
                passed(
                    "OUTBOUND",
                    "SSH/Xray outbound Internet",
                    "Remote tunnel delivered Internet",
                    outbound.getOrThrow().latencyMs.takeIf { it > 0 } ?: elapsedMs(outboundStart)
                )
            )
        }

        DiagnosticReport(profile.name, startedAt, System.currentTimeMillis(), steps)
    }

    private fun checkPhysicalNetwork(): String {
        val network = connectivityManager.activeNetwork ?: error("No hay una red física activa")
        val capabilities = connectivityManager.getNetworkCapabilities(network)
            ?: error("No hay capacidades de red disponibles")
        check(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            "La red física no anuncia acceso a Internet"
        }
        check(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
            "La red activa ya es otra VPN; desconéctala para ejecutar el diagnóstico"
        }
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data available"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi available"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet available"
            else -> "Physical network available"
        }
    }

    private fun inspectTls(profile: VpnProfile): String {
        val sni = profile.sni.ifBlank { profile.host }
        val rawSocket = Socket().apply {
            connect(InetSocketAddress(profile.host, profile.port), 8_000)
            soTimeout = 8_000
        }
        val tlsSocket = try {
            TlsTransport.upgrade(
                rawSocket,
                profile.host,
                profile.port,
                sni,
                profile.selectedTlsVerificationMode
            )
        } catch (error: Throwable) {
            runCatching { rawSocket.close() }
            throw error
        }
        tlsSocket.use {
            val certificate = it.session.peerCertificates.firstOrNull() as? X509Certificate
            val subject = certificate?.subjectX500Principal?.name.orEmpty().take(160)
            return "${it.session.protocol} · ${it.session.cipherSuite} · " +
                "${profile.selectedTlsVerificationMode.label} · $subject"
        }
    }

    private fun passed(id: String, label: String, detail: String, latencyMs: Long? = null) =
        DiagnosticStep(id, label, DiagnosticStatus.PASSED, detail, latencyMs)

    private fun failed(id: String, label: String, failure: com.ghostnexora.vpn.tunnel.VpnFailure) =
        DiagnosticStep(
            id,
            label,
            DiagnosticStatus.FAILED,
            failure.title,
            errorCode = failure.code,
            solution = failure.solution
        )

    private fun elapsedMs(start: Long): Long =
        ((System.nanoTime() - start) / 1_000_000L).coerceAtLeast(1L)
}
