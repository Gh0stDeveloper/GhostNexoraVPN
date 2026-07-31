package com.ghostnexora.vpn.tunnel

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

/**
 * Verifies the active Xray outbound through its loopback-only SOCKS inbound.
 *
 * The probe performs this exact path:
 * local SOCKS client -> Xray -> SSH SOCKS bridge -> direct-tcpip -> TLS peer.
 * A successful TLS handshake proves bidirectional forwarding instead of only
 * proving that the native core process is running.
 */
internal object Socks5OutboundProbe {
    private const val CONNECT_TIMEOUT_MS = 4_000
    private const val IO_TIMEOUT_MS = 12_000
    private val IPV4_LOOPBACK = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
    val targets = listOf(
        SocksProbeTarget(byteArrayOf(1, 1, 1, 1), 443, "one.one.one.one"),
        SocksProbeTarget(byteArrayOf(8, 8, 8, 8), 443, "dns.google")
    )

    fun measure(localPort: Int, target: SocksProbeTarget): SocksProbeResult {
        require(localPort in 1..65535) { "Invalid Xray health-check port" }
        val startedAt = System.nanoTime()
        val socket = Socket()
        try {
            socket.tcpNoDelay = true
            socket.soTimeout = IO_TIMEOUT_MS
            socket.connect(InetSocketAddress(IPV4_LOOPBACK, localPort), CONNECT_TIMEOUT_MS)

            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            negotiateNoAuthentication(input, output)
            requestIpv4Connect(input, output, target.address, target.port)

            val tlsSocket = SSLContext.getDefault().socketFactory.createSocket(
                socket,
                target.serverName,
                target.port,
                true
            ) as SSLSocket
            tlsSocket.useClientMode = true
            tlsSocket.soTimeout = IO_TIMEOUT_MS
            tlsSocket.sslParameters = tlsSocket.sslParameters.apply {
                endpointIdentificationAlgorithm = "HTTPS"
                serverNames = listOf(SNIHostName(target.serverName))
            }
            tlsSocket.use {
                it.startHandshake()
                val latencyMs = ((System.nanoTime() - startedAt) / 1_000_000L)
                    .coerceAtLeast(1L)
                return SocksProbeResult(
                    latencyMs = latencyMs,
                    tlsProtocol = it.session.protocol.orEmpty(),
                    cipherSuite = it.session.cipherSuite.orEmpty()
                )
            }
        } finally {
            runCatching { socket.close() }
        }
    }

    internal fun negotiateNoAuthentication(input: InputStream, output: OutputStream) {
        output.write(byteArrayOf(0x05, 0x01, 0x00))
        output.flush()

        val response = ByteArray(2)
        readFully(input, response)
        if ((response[0].toInt() and 0xFF) != 0x05) {
            throw IOException("Xray health-check returned an invalid SOCKS version")
        }
        if ((response[1].toInt() and 0xFF) != 0x00) {
            throw IOException("Xray health-check rejected SOCKS no-authentication mode")
        }
    }

    internal fun requestIpv4Connect(
        input: InputStream,
        output: OutputStream,
        address: ByteArray,
        port: Int
    ) {
        output.write(buildIpv4ConnectRequest(address, port))
        output.flush()
        readConnectReply(input)
    }

    internal fun buildIpv4ConnectRequest(address: ByteArray, port: Int): ByteArray {
        require(address.size == 4) { "SOCKS IPv4 address must contain four bytes" }
        require(port in 1..65535) { "SOCKS target port is invalid" }
        return byteArrayOf(
            0x05,
            0x01,
            0x00,
            0x01,
            address[0],
            address[1],
            address[2],
            address[3],
            (port ushr 8).toByte(),
            port.toByte()
        )
    }

    internal fun readConnectReply(input: InputStream) {
        val header = ByteArray(4)
        readFully(input, header)
        if ((header[0].toInt() and 0xFF) != 0x05) {
            throw IOException("Xray health-check returned an invalid SOCKS reply")
        }
        val replyCode = header[1].toInt() and 0xFF
        if (replyCode != 0x00) {
            throw IOException("Xray SOCKS outbound rejected the probe (${replyLabel(replyCode)})")
        }

        val addressLength = when (header[3].toInt() and 0xFF) {
            0x01 -> 4
            0x03 -> input.read().takeIf { it >= 0 }
                ?: throw IOException("Xray health-check returned a truncated domain reply")
            0x04 -> 16
            else -> throw IOException("Xray health-check returned an invalid address type")
        }
        readFully(input, ByteArray(addressLength + 2))
    }

    private fun readFully(input: InputStream, destination: ByteArray) {
        var offset = 0
        while (offset < destination.size) {
            val read = input.read(destination, offset, destination.size - offset)
            if (read < 0) throw IOException("Xray health-check SOCKS connection closed early")
            offset += read
        }
    }

    private fun replyLabel(code: Int): String = when (code) {
        0x01 -> "general failure"
        0x02 -> "connection not allowed"
        0x03 -> "network unreachable"
        0x04 -> "host unreachable"
        0x05 -> "connection refused"
        0x06 -> "TTL expired"
        0x07 -> "command not supported"
        0x08 -> "address type not supported"
        else -> "code $code"
    }
}

internal data class SocksProbeResult(
    val latencyMs: Long,
    val tlsProtocol: String,
    val cipherSuite: String
)

internal data class SocksProbeTarget(
    val address: ByteArray,
    val port: Int,
    val serverName: String
) {
    val endpoint: String
        get() = "https://$serverName:$port"
}
