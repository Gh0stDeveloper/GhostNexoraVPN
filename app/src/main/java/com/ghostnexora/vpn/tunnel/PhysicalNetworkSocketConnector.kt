package com.ghostnexora.vpn.tunnel

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Opens transport sockets on a real Android network instead of letting the
 * process default decide after the VPN TUN is active.
 *
 * The application UID is already excluded from the TUN by GhostVpnService.
 * Binding every SSH/proxy socket to a NOT_VPN network adds a second routing
 * guarantee and also lets DNS resolution use that same physical network.
 */
internal class PhysicalNetworkSocketConnector(
    context: Context?,
    private val onStatus: (String) -> Unit = {}
) {
    private val connectivityManager = context
        ?.applicationContext
        ?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val knownPhysicalNetworks = ConcurrentHashMap.newKeySet<Network>()

    private val physicalNetworkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            knownPhysicalNetworks += network
        }

        override fun onCapabilitiesChanged(
            network: Network,
            capabilities: NetworkCapabilities
        ) {
            val physicalInternet =
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            if (physicalInternet) {
                knownPhysicalNetworks += network
            } else {
                knownPhysicalNetworks -= network
            }
        }

        override fun onLost(network: Network) {
            knownPhysicalNetworks -= network
        }
    }

    init {
        connectivityManager?.let { manager ->
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()
            runCatching {
                manager.registerNetworkCallback(request, physicalNetworkCallback)
            }.onFailure { error ->
                onStatus(
                    "[NETWORK] Registro de redes físicas no disponible · ${error.shortMessage()}"
                )
            }
        }
    }

    fun connect(host: String, port: Int, timeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS): Socket {
        require(host.isNotBlank()) { "El host de transporte no puede estar vacío" }
        require(port in 1..65535) { "El puerto de transporte es inválido" }

        val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs.toLong())
        val failures = mutableListOf<Throwable>()
        val networks = physicalNetworks()

        for ((networkIndex, network) in networks.withIndex()) {
            val addresses = resolve(network, host, failures)
            if (addresses.isEmpty()) continue

            onStatus(
                "[NETWORK] DNS físico · $host → ${addresses.joinToString { it.hostAddress.orEmpty() }}"
            )
            for ((addressIndex, address) in addresses.withIndex()) {
                val remainingMs = remainingTimeoutMs(deadlineNanos)
                if (remainingMs <= 0) break

                val socket = configuredSocket()
                try {
                    network.bindSocket(socket)
                    onStatus(
                        "[NETWORK] Intento TCP físico ${networkIndex + 1}.${addressIndex + 1} · " +
                            "${address.hostAddress}:$port · ${networkLabel(network)}"
                    )
                    val startedAt = System.nanoTime()
                    socket.connect(InetSocketAddress(address, port), remainingMs)
                    val latencyMs = TimeUnit.NANOSECONDS
                        .toMillis(System.nanoTime() - startedAt)
                        .coerceAtLeast(1L)
                    onStatus(
                        "[NETWORK] Socket TCP físico conectado · ${address.hostAddress}:$port · " +
                            "$latencyMs ms · ${networkLabel(network)}"
                    )
                    return socket
                } catch (error: Throwable) {
                    failures += error
                    runCatching { socket.close() }
                    onStatus(
                        "[NETWORK] IP no disponible · ${address.hostAddress}:$port · " +
                            error.shortMessage()
                    )
                }
            }
        }

        // Defensive fallback for devices whose vendor connectivity service does
        // not expose a NOT_VPN Network object. The app UID remains disallowed
        // from the VPN, so this socket still cannot recurse through the TUN.
        val fallbackAddresses = resolve(network = null, host = host, failures = failures)
        for ((index, address) in fallbackAddresses.withIndex()) {
            val remainingMs = remainingTimeoutMs(deadlineNanos)
            if (remainingMs <= 0) break

            val socket = configuredSocket()
            try {
                onStatus(
                    "[NETWORK] Intento TCP con bypass de aplicación ${index + 1} · " +
                        "${address.hostAddress}:$port"
                )
                socket.connect(InetSocketAddress(address, port), remainingMs)
                onStatus("[NETWORK] Socket TCP conectado por bypass propio · ${address.hostAddress}:$port")
                return socket
            } catch (error: Throwable) {
                failures += error
                runCatching { socket.close() }
            }
        }

        val last = failures.lastOrNull()
        val attempted = failures.size.coerceAtLeast(1)
        throw IOException(
            "[TCP-ALL-FAILED] No fue posible conectar con ninguna IP de $host:$port " +
                "tras $attempted intento(s).",
            last
        )
    }

    private fun physicalNetworks(): List<Network> {
        val manager = connectivityManager ?: return emptyList()
        val active = manager.activeNetwork
        return buildList {
            if (active != null && manager.isPhysicalInternetNetwork(active)) add(active)
            knownPhysicalNetworks.forEach { network ->
                if (network != active && manager.isPhysicalInternetNetwork(network)) add(network)
            }
        }
    }

    private fun resolve(
        network: Network?,
        host: String,
        failures: MutableList<Throwable>
    ): List<InetAddress> = runCatching {
        val resolved = if (network != null) network.getAllByName(host) else InetAddress.getAllByName(host)
        resolved
            .distinctBy { it.hostAddress }
            .sortedBy { if (it is Inet4Address) 0 else 1 }
    }.onFailure { failures += it }.getOrDefault(emptyList())

    private fun remainingTimeoutMs(deadlineNanos: Long): Int = TimeUnit.NANOSECONDS
        .toMillis(deadlineNanos - System.nanoTime())
        .coerceAtMost(PER_ADDRESS_TIMEOUT_MS.toLong())
        .toInt()

    private fun ConnectivityManager.isPhysicalInternetNetwork(network: Network): Boolean {
        val capabilities = getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
    }

    private fun networkLabel(network: Network): String {
        val capabilities = connectivityManager?.getNetworkCapabilities(network)
        return when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "datos móviles"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            else -> "red física"
        }
    }

    private fun configuredSocket(): Socket = Socket().apply {
        tcpNoDelay = true
        keepAlive = true
        reuseAddress = true
    }

    private fun Throwable.shortMessage(): String = generateSequence(this) { it.cause }
        .mapNotNull { it.message?.takeIf(String::isNotBlank) }
        .firstOrNull()
        .orEmpty()
        .replace('\n', ' ')
        .take(220)
        .ifBlank { javaClass.simpleName }

    private companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MS = 20_000
        const val PER_ADDRESS_TIMEOUT_MS = 8_000
    }
}
