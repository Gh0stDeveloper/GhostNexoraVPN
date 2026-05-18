package com.ghostnexora.vpn.tunnel

import com.ghostnexora.vpn.data.model.ProxyConfig
import com.ghostnexora.vpn.data.model.VpnProfile
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.SocketFactory
import com.jcraft.jsch.UserInfo
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Motor SSH por modos con transporte directo, proxy y/o TLS/SNI.
 *
 * Estrategia:
 * 1) resolver el transporte TCP (directo o proxy),
 * 2) opcionalmente envolverlo en TLS/SNI,
 * 3) entregar el socket a JSch para levantar la sesión SSH real.
 */
class SshTunnelEngine {

    fun connect(profile: VpnProfile): Session {
        val mode = profile.selectedMode
        require(mode.family == "ssh" || mode.family == "ssl") {
            "El motor actual solo soporta perfiles SSH o SSL/SNI. Modo recibido: ${mode.label}"
        }

        val transportHost = profile.host.trim()
        val transportPort = profile.port.coerceIn(1, 65535)
        require(transportHost.isNotBlank()) { "El host del perfil no puede estar vacío" }
        require(profile.username.isNotBlank()) { "El usuario SSH no puede estar vacío" }

        val sshUser = profile.username.trim()
        val sshPassword = profile.password.trim()
        require(sshPassword.isNotBlank()) { "La contraseña SSH es obligatoria para este modo" }

        val jsch = JSch()
        jsch.removeAllIdentity()

        val session = jsch.getSession(sshUser, transportHost, transportPort)
        session.setPassword(sshPassword)
        session.setUserInfo(ProfileUserInfo(sshPassword))
        session.setConfig("StrictHostKeyChecking", "no")
        session.setConfig("PreferredAuthentications", "password")
        session.setConfig("PubkeyAuthentication", "no")
        session.setConfig("MaxAuthTries", "1")
        session.setServerAliveInterval(15_000)
        session.setTimeout(20_000)
        session.setSocketFactory(TunnelSocketFactory(profile))

        try {
            // Si el modo usa payload, dejamos la señalización para el nivel de UI/log.
            // El transporte SSH/TLS sigue funcionando como base.
            session.connect(20_000)
        } catch (e: JSchException) {
            if (e.message?.contains("auth fail", ignoreCase = true) == true) {
                throw IllegalStateException(
                    "Autenticación SSH fallida. Verifica usuario, contraseña, puerto y que el servidor permita password auth.",
                    e
                )
            }
            throw e
        }

        return session
    }

    fun disconnect(session: Session?) {
        runCatching { session?.disconnect() }
    }
}

private class TunnelSocketFactory(
    private val profile: VpnProfile
) : SocketFactory {

    override fun createSocket(host: String, port: Int): Socket {
        val targetHost = profile.host.trim().ifBlank { host }
        val targetPort = profile.port.coerceIn(1, 65535).takeIf { it > 0 } ?: port
        val proxy = profile.proxy.takeIf { it.host.isNotBlank() && it.port in 1..65535 }

        val socket = if (proxy == null) {
            connectDirect(targetHost, targetPort)
        } else {
            connectThroughProxy(targetHost, targetPort, proxy)
        }

        return if (profile.selectedMode.usesTls) {
            wrapWithTls(socket, profile.sni.ifBlank { targetHost }, targetHost, targetPort)
        } else {
            socket
        }
    }

    override fun getInputStream(socket: Socket): InputStream = socket.getInputStream()

    override fun getOutputStream(socket: Socket): OutputStream = socket.getOutputStream()

    private fun connectDirect(host: String, port: Int): Socket {
        return Socket().apply {
            tcpNoDelay = true
            keepAlive = true
            connect(InetSocketAddress(host, port), 15_000)
        }
    }

    private fun connectThroughProxy(host: String, port: Int, proxy: ProxyConfig): Socket {
        val socket = connectDirect(proxy.host.trim(), proxy.port)
        when (proxy.type.trim().lowercase()) {
            "socks5" -> performSocks5Handshake(socket, host, port)
            else -> performHttpConnectHandshake(socket, host, port)
        }
        return socket
    }

    private fun performHttpConnectHandshake(socket: Socket, host: String, port: Int) {
        val request = buildString {
            append("CONNECT ")
            append(host)
            append(":")
            append(port)
            append(" HTTP/1.1\r\n")
            append("Host: ")
            append(host)
            append(":")
            append(port)
            append("\r\n")
            append("Proxy-Connection: Keep-Alive\r\n\r\n")
        }
        socket.getOutputStream().write(request.toByteArray())
        socket.getOutputStream().flush()

        val response = BufferedReader(socket.getInputStream().reader()).readLine().orEmpty()
        if (!response.contains("200")) {
            throw IOException("HTTP proxy rechazó la conexión: $response")
        }
    }

    private fun performSocks5Handshake(socket: Socket, host: String, port: Int) {
        val out = socket.getOutputStream()
        val input = socket.getInputStream()

        out.write(byteArrayOf(0x05, 0x01, 0x00))
        out.flush()

        val methodResponse = ByteArray(2)
        readFully(input, methodResponse)
        if (methodResponse[1].toInt() != 0x00) {
            throw IOException("SOCKS5 requirió autenticación no soportada")
        }

        val hostBytes = host.toByteArray()
        val request = ByteArray(7 + hostBytes.size)
        request[0] = 0x05
        request[1] = 0x01
        request[2] = 0x00
        request[3] = 0x03
        request[4] = hostBytes.size.toByte()
        hostBytes.copyInto(request, destinationOffset = 5)
        val portIndex = 5 + hostBytes.size
        request[portIndex] = ((port shr 8) and 0xFF).toByte()
        request[portIndex + 1] = (port and 0xFF).toByte()
        out.write(request)
        out.flush()

        val response = ByteArray(10)
        readFully(input, response)
        if (response[1].toInt() != 0x00) {
            throw IOException("SOCKS5 rechazó la conexión")
        }
    }

    private fun readFully(input: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read < 0) throw IOException("Socket cerrado durante handshake")
            offset += read
        }
    }

    private fun wrapWithTls(socket: Socket, sniHost: String, peerHost: String, peerPort: Int): SSLSocket {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(TrustAllX509TrustManager()), SecureRandom())

        val sslSocket = sslContext.socketFactory.createSocket(socket, peerHost, peerPort, true) as SSLSocket
        sslSocket.useClientMode = true

        val params: SSLParameters = sslSocket.sslParameters
        params.serverNames = listOf(SNIHostName(sniHost))
        sslSocket.sslParameters = params

        sslSocket.startHandshake()
        return sslSocket
    }
}

private class ProfileUserInfo(
    private val password: String
) : UserInfo {
    override fun getPassword(): String? = password
    override fun promptYesNo(str: String?): Boolean = true
    override fun getPassphrase(): String? = null
    override fun promptPassphrase(message: String?): Boolean = false
    override fun promptPassword(message: String?): Boolean = password.isNotBlank()
    override fun showMessage(message: String?) = Unit
}

private class TrustAllX509TrustManager : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}