package com.ghostnexora.vpn.tunnel

import android.content.Context
import com.ghostnexora.vpn.data.model.ProxyConfig
import com.ghostnexora.vpn.data.model.VpnProfile
import com.ghostnexora.vpn.util.PayloadAction
import com.ghostnexora.vpn.util.PayloadContext
import com.ghostnexora.vpn.util.PayloadEngine
import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.SocketFactory
import com.jcraft.jsch.UserInfo
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Motor SSH para los modos directos, TLS/SNI, payload y proxy.
 *
 * La sesión SSH no se considera una VPN por sí sola. [connectWithSocks] crea
 * además un proxy SOCKS5 local respaldado por canales direct-tcpip de SSH;
 * Xray Core usa ese SOCKS como salida del TUN de Android.
 */
class SshTunnelEngine(
    private val context: Context? = null,
    private val onStatus: (String) -> Unit = {}
) {

    fun connect(profile: VpnProfile): Session {
        val mode = profile.selectedMode
        require(mode.isSsh) { "El motor SSH no soporta ${mode.label}" }

        val transportHost = profile.host.trim()
        val transportPort = profile.port.coerceIn(1, 65535)
        require(transportHost.isNotBlank()) { "El host del perfil no puede estar vacío" }
        require(profile.username.isNotBlank()) { "El usuario SSH no puede estar vacío" }
        require(profile.password.isNotBlank()) { "La contraseña SSH es obligatoria" }

        val jsch = JSch().apply {
            removeAllIdentity()
            configureKnownHosts(this)
        }

        val password = profile.password
        val session = jsch.getSession(profile.username.trim(), transportHost, transportPort)
        session.setPassword(password)
        session.setUserInfo(ProfileUserInfo(password))
        session.setConfig("StrictHostKeyChecking", "ask")
        session.setConfig("PreferredAuthentications", "password,keyboard-interactive")
        session.setConfig("MaxAuthTries", "3")
        session.setConfig(
            "server_host_key",
            "ssh-ed25519,ecdsa-sha2-nistp521,ecdsa-sha2-nistp384,ecdsa-sha2-nistp256,rsa-sha2-512,rsa-sha2-256"
        )
        session.setServerAliveInterval(15_000)
        session.setServerAliveCountMax(3)
        session.setTimeout(25_000)
        session.setSocketFactory(TunnelSocketFactory(profile, onStatus))

        try {
            onStatus("[SSH] Abriendo sesión y negociando algoritmos")
            session.connect(25_000)
            onStatus("[SSH] Autenticación completada · sesión cifrada activa")
        } catch (e: JSchException) {
            if (e.message?.contains("auth fail", ignoreCase = true) == true) {
                throw IllegalStateException(
                    "Autenticación SSH fallida. Verifica usuario, contraseña, puerto y acceso por contraseña.",
                    e
                )
            }
            throw e
        }

        return session
    }

    fun connectWithSocks(profile: VpnProfile): SshTunnelHandle {
        val session = connect(profile)
        return try {
            val socksServer = SshSocksServer(session, onStatus).also { it.start() }
            SshTunnelHandle(session, socksServer)
        } catch (error: Throwable) {
            runCatching { session.disconnect() }
            throw error
        }
    }

    fun disconnect(session: Session?) {
        runCatching { session?.disconnect() }
    }

    private fun configureKnownHosts(jsch: JSch) {
        val appContext = context?.applicationContext ?: return
        val knownHosts = appContext.filesDir.resolve("ssh_known_hosts")
        runCatching {
            if (!knownHosts.exists()) knownHosts.createNewFile()
            jsch.setKnownHosts(knownHosts.absolutePath)
        }
    }
}

data class SshTunnelHandle(
    val session: Session,
    val socksServer: SshSocksServer
) : Closeable {
    val socksPort: Int
        get() = socksServer.localPort

    override fun close() {
        runCatching { socksServer.close() }
        runCatching { session.disconnect() }
    }
}

private class TunnelSocketFactory(
    private val profile: VpnProfile,
    private val onStatus: (String) -> Unit
) : SocketFactory {

    private val wrappedInputs = Collections.synchronizedMap(WeakHashMap<Socket, InputStream>())

    override fun createSocket(host: String, port: Int): Socket {
        val targetHost = profile.host.trim().ifBlank { host }
        val targetPort = profile.port.takeIf { it in 1..65535 } ?: port
        val mode = profile.selectedMode
        val proxy = profile.proxy.takeIf { mode.requiresProxy && it.host.isNotBlank() && it.port in 1..65535 }

        var socket: Socket
        var payloadAlreadySent = false

        if (proxy != null) {
            socket = connectDirect(proxy.host.trim(), proxy.port)
            when (proxy.type.trim().lowercase()) {
                "socks", "socks5" -> performSocks5Handshake(socket, targetHost, targetPort)
                else -> {
                    if (mode.requiresPayload) {
                        wrappedInputs[socket] = performPayloadHandshake(socket, targetHost, targetPort)
                        payloadAlreadySent = true
                    } else {
                        performHttpConnectHandshake(socket, targetHost, targetPort)
                    }
                }
            }
        } else {
            socket = connectDirect(targetHost, targetPort)
        }

        if (mode.usesTls) {
            val tlsSocket = TlsTransport.upgrade(
                connectedSocket = socket,
                targetHost = targetHost,
                targetPort = targetPort,
                sniHost = profile.sni.trim().ifBlank { targetHost },
                verificationMode = profile.selectedTlsVerificationMode
            )
            onStatus(
                "[TLS] ${tlsSocket.session.protocol} · ${tlsSocket.session.cipherSuite} · " +
                    profile.selectedTlsVerificationMode.label
            )
            socket = tlsSocket
        }

        if (mode.requiresPayload && !payloadAlreadySent) {
            wrappedInputs[socket] = performPayloadHandshake(socket, targetHost, targetPort)
        }

        return socket
    }

    override fun getInputStream(socket: Socket): InputStream =
        wrappedInputs.remove(socket) ?: socket.getInputStream()

    override fun getOutputStream(socket: Socket): OutputStream = socket.getOutputStream()

    private fun connectDirect(host: String, port: Int): Socket {
        onStatus("[NETWORK] Abriendo socket TCP · $host:$port")
        val startedAt = System.nanoTime()
        return Socket().apply {
            tcpNoDelay = true
            keepAlive = true
            connect(InetSocketAddress(host, port), 20_000)
            val latencyMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(1L)
            onStatus("[NETWORK] Socket TCP conectado · $latencyMs ms")
        }
    }

    /**
     * Lee la cabecera HTTP byte a byte. No usa BufferedReader porque su buffer
     * puede adelantarse hasta el banner SSH/TLS y perder bytes del túnel.
     */
    private fun performHttpConnectHandshake(socket: Socket, host: String, port: Int) {
        val request = buildString {
            append("CONNECT $host:$port HTTP/1.1\r\n")
            append("Host: $host:$port\r\n")
            append("Proxy-Connection: Keep-Alive\r\n")
            append("Connection: Keep-Alive\r\n\r\n")
        }
        socket.getOutputStream().apply {
            write(request.toByteArray(Charsets.UTF_8))
            flush()
        }

        val header = readHttpHeader(socket.getInputStream())
        val statusLine = header.lineSequence().firstOrNull().orEmpty()
        val code = statusLine.split(' ').getOrNull(1)?.toIntOrNull()
        if (code !in 200..299) {
            throw IOException("HTTP proxy rechazó la conexión: $statusLine")
        }
    }

    private fun readHttpHeader(input: InputStream): String {
        val captured = ByteArrayOutputStream()
        var previous = -1
        var beforePrevious = -1
        var thirdPrevious = -1

        while (captured.size() < MAX_HANDSHAKE_BYTES) {
            val current = input.read()
            if (current < 0) throw IOException("El proxy cerró la conexión durante el handshake")
            captured.write(current)

            val crlfEnd = thirdPrevious == '\r'.code &&
                beforePrevious == '\n'.code &&
                previous == '\r'.code &&
                current == '\n'.code
            val lfEnd = previous == '\n'.code && current == '\n'.code
            if (crlfEnd || lfEnd) {
                return captured.toByteArray().toString(Charsets.ISO_8859_1)
            }

            thirdPrevious = beforePrevious
            beforePrevious = previous
            previous = current
        }

        throw IOException("Cabecera HTTP demasiado grande")
    }

    private fun performSocks5Handshake(socket: Socket, host: String, port: Int) {
        val out = socket.getOutputStream()
        val input = socket.getInputStream()

        out.write(byteArrayOf(0x05, 0x01, 0x00))
        out.flush()

        val methodResponse = ByteArray(2)
        readFully(input, methodResponse)
        if (methodResponse[0].toInt() != 0x05 || methodResponse[1].toInt() != 0x00) {
            throw IOException("El proxy SOCKS5 requiere un método de autenticación no soportado")
        }

        val hostBytes = host.toByteArray(Charsets.UTF_8)
        require(hostBytes.size <= 255) { "Nombre de host demasiado largo para SOCKS5" }
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

        val header = ByteArray(4)
        readFully(input, header)
        if (header[1].toInt() != 0x00) {
            throw IOException("SOCKS5 rechazó la conexión (código ${header[1].toInt() and 0xFF})")
        }
        val addressLength = when (header[3].toInt() and 0xFF) {
            0x01 -> 4
            0x03 -> input.read().takeIf { it >= 0 } ?: throw IOException("Respuesta SOCKS5 incompleta")
            0x04 -> 16
            else -> throw IOException("Tipo de dirección SOCKS5 inválido")
        }
        readFully(input, ByteArray(addressLength + 2))
    }

    private fun performPayloadHandshake(socket: Socket, host: String, port: Int): PushbackInputStream {
        val plan = PayloadEngine.compile(
            raw = profile.payload,
            context = PayloadContext(
                host = host,
                port = port,
                sni = profile.sni.ifBlank { host },
                proxyHost = profile.proxy.host,
                proxyPort = profile.proxy.port
            )
        )
        val output = socket.getOutputStream()
        plan.actions.forEach { action ->
            when (action) {
                is PayloadAction.Send -> {
                    output.write(action.text.toByteArray(Charsets.UTF_8))
                    output.flush()
                }
                is PayloadAction.Delay -> Thread.sleep(action.millis)
            }
        }
        onStatus("[PAYLOAD] ${plan.segmentCount} segmento(s) enviado(s) · contenido protegido")

        val input = PushbackInputStream(socket.getInputStream(), MAX_HANDSHAKE_BYTES)
        if (!looksLikeHttpPayload(plan.rendered)) return input

        val previousTimeout = socket.soTimeout
        socket.soTimeout = 8_000
        val captured = ByteArrayOutputStream()
        try {
            var previous = -1
            var beforePrevious = -1
            var thirdPrevious = -1
            while (captured.size() < MAX_HANDSHAKE_BYTES) {
                val current = input.read()
                if (current < 0) break
                captured.write(current)

                val crlfEnd = thirdPrevious == '\r'.code && beforePrevious == '\n'.code && previous == '\r'.code && current == '\n'.code
                val lfEnd = previous == '\n'.code && current == '\n'.code
                val sshBannerComplete = current == '\n'.code &&
                    captured.size() <= MAX_SSH_BANNER_BYTES &&
                    captured.toByteArray()
                        .toString(Charsets.ISO_8859_1)
                        .trimStart()
                        .startsWith("SSH-", ignoreCase = true)
                if (crlfEnd || lfEnd || sshBannerComplete) break

                thirdPrevious = beforePrevious
                beforePrevious = previous
                previous = current
            }
        } catch (_: SocketTimeoutException) {
            // Algunos injectores no devuelven cabecera HTTP y entregan el banner SSH después.
        } finally {
            socket.soTimeout = previousTimeout
        }

        val responseBytes = captured.toByteArray()
        if (responseBytes.isEmpty()) {
            onStatus("[PAYLOAD] Sin cabecera HTTP inmediata · esperando banner SSH")
            return input
        }

        val responseText = responseBytes.toString(Charsets.ISO_8859_1)
        val firstLine = responseText.lineSequence().firstOrNull().orEmpty()
        if (firstLine.startsWith("HTTP/", ignoreCase = true)) {
            val code = firstLine.split(' ').getOrNull(1)?.toIntOrNull()
            if (code == null || (code !in 200..299 && code != 101)) {
                throw IOException("El servidor rechazó el payload: $firstLine")
            }
            onStatus("[PAYLOAD] Respuesta aceptada · HTTP $code")
        } else {
            input.unread(responseBytes)
            if (firstLine.startsWith("SSH-", ignoreCase = true)) {
                onStatus("[PAYLOAD] Banner SSH directo recibido")
            }
        }

        return input
    }

    private fun looksLikeHttpPayload(payload: String): Boolean {
        val first = payload.trimStart().substringBefore(' ').uppercase()
        return first in setOf("CONNECT", "GET", "POST", "HEAD", "OPTIONS", "PUT", "PATCH")
    }

    private fun readFully(input: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val count = input.read(buffer, offset, buffer.size - offset)
            if (count < 0) throw IOException("Socket cerrado durante el handshake")
            offset += count
        }
    }

    companion object {
        private const val MAX_HANDSHAKE_BYTES = 16 * 1024
        private const val MAX_SSH_BANNER_BYTES = 512
    }
}

/**
 * SOCKS5 local de solo loopback. Cada CONNECT abre un canal direct-tcpip
 * dentro de la sesión SSH autenticada. UDP se resuelve por el core Xray en los
 * modos que tienen transporte UDP nativo; el bridge SSH es deliberadamente TCP.
 */
class SshSocksServer(
    private val session: Session,
    private val onStatus: (String) -> Unit = {}
) : Closeable {
    private val running = AtomicBoolean(false)
    private val firstChannelOpenedReported = AtomicBoolean(false)
    private val firstForwardedChannelReported = AtomicBoolean(false)
    private val firstDownlinkReported = AtomicBoolean(false)
    private val firstIoFailureReported = AtomicBoolean(false)
    private val executor: ExecutorService = Executors.newCachedThreadPool()
    private val clients = Collections.synchronizedSet(mutableSetOf<Socket>())
    private var serverSocket: ServerSocket? = null

    val localPort: Int
        get() = serverSocket?.localPort ?: 0

    fun start() {
        check(running.compareAndSet(false, true)) { "El bridge SOCKS SSH ya está activo" }
        /*
         * StableXrayConfigFactory always dials 127.0.0.1. Binding the bridge to
         * an implementation-selected loopback address can choose ::1 on some
         * Android runtimes and leave the IPv4 Xray endpoint unreachable.
         */
        try {
            serverSocket = ServerSocket(0, 64, IPV4_LOOPBACK)
        } catch (error: Throwable) {
            running.set(false)
            executor.shutdownNow()
            throw error
        }
        executor.execute {
            while (running.get()) {
                try {
                    val client = serverSocket?.accept() ?: break
                    clients += client
                    executor.execute { handleClient(client) }
                } catch (_: SocketException) {
                    if (running.get()) break
                }
            }
        }
    }

    private fun handleClient(client: Socket) {
        var channel: ChannelDirectTCPIP? = null
        var socksHandshakeCompleted = false
        try {
            client.soTimeout = 15_000
            val input = client.getInputStream()
            val output = client.getOutputStream()

            val greeting = ByteArray(2)
            readFully(input, greeting)
            if ((greeting[0].toInt() and 0xFF) != 5) throw IOException("Versión SOCKS no soportada")
            val methodCount = greeting[1].toInt() and 0xFF
            val methods = ByteArray(methodCount)
            readFully(input, methods)
            if (methods.none { (it.toInt() and 0xFF) == 0 }) {
                output.write(byteArrayOf(0x05, 0xFF.toByte()))
                return
            }
            output.write(byteArrayOf(0x05, 0x00))
            output.flush()

            val request = ByteArray(4)
            readFully(input, request)
            if ((request[0].toInt() and 0xFF) != 5 || (request[1].toInt() and 0xFF) != 1) {
                sendReply(output, 0x07)
                return
            }

            val targetHost = when (request[3].toInt() and 0xFF) {
                0x01 -> InetAddress.getByAddress(ByteArray(4).also { readFully(input, it) }).hostAddress.orEmpty()
                0x03 -> {
                    val length = input.read()
                    if (length < 0) throw IOException("SOCKS5 incompleto")
                    String(ByteArray(length).also { readFully(input, it) }, Charsets.UTF_8)
                }
                0x04 -> InetAddress.getByAddress(ByteArray(16).also { readFully(input, it) }).hostAddress.orEmpty()
                else -> {
                    sendReply(output, 0x08)
                    return
                }
            }
            val portBytes = ByteArray(2)
            readFully(input, portBytes)
            val targetPort = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)

            channel = session.openChannel("direct-tcpip") as ChannelDirectTCPIP
            channel.setHost(targetHost)
            channel.setPort(targetPort)
            channel.setOrgIPAddress(client.inetAddress?.hostAddress ?: "127.0.0.1")
            channel.setOrgPort(client.port)
            val remoteInput = channel.inputStream
            val remoteOutput = channel.outputStream
            channel.connect(20_000)
            check(channel.isConnected) { "El servidor SSH no abrió el canal direct-tcpip" }
            if (firstChannelOpenedReported.compareAndSet(false, true)) {
                onStatus("[SOCKS] Canal direct-tcpip abierto por el servidor SSH")
            }

            sendReply(output, 0x00)
            socksHandshakeCompleted = true
            client.soTimeout = 0

            executor.execute {
                try {
                    copyClientToSshAndHalfClose(input, remoteOutput) {
                        if (firstForwardedChannelReported.compareAndSet(false, true)) {
                            onStatus("[SOCKS] Subida activa · datos enviados por direct-tcpip SSH")
                        }
                    }
                } catch (error: IOException) {
                    reportIoFailure("subida", error)
                }
            }

            copyFromSshChannel(remoteInput, output) {
                if (firstDownlinkReported.compareAndSet(false, true)) {
                    onStatus("[SOCKS] Bajada activa · respuesta remota recibida por SSH")
                }
            }
        } catch (error: Throwable) {
            if (!socksHandshakeCompleted && running.get()) {
                runCatching { sendReply(client.getOutputStream(), 0x01) }
                val detail = generateSequence(error) { it.cause }
                    .mapNotNull { it.message?.takeIf(String::isNotBlank) }
                    .firstOrNull()
                    .orEmpty()
                    .replace('\n', ' ')
                    .take(160)
                    .ifBlank { error.javaClass.simpleName }
                onStatus("[SOCKS] ERROR · canal direct-tcpip no disponible · $detail")
            } else if (socksHandshakeCompleted && error is IOException) {
                reportIoFailure("bajada", error)
            }
        } finally {
            runCatching { channel?.disconnect() }
            runCatching { client.close() }
            clients -= client
        }
    }

    private fun reportIoFailure(direction: String, error: IOException) {
        if (!running.get() || !firstIoFailureReported.compareAndSet(false, true)) return
        val detail = error.message
            .orEmpty()
            .replace('\n', ' ')
            .take(160)
            .ifBlank { error.javaClass.simpleName }
        onStatus("[SOCKS] WARN · cierre de $direction · $detail")
    }

    private fun sendReply(output: OutputStream, code: Int) {
        output.write(byteArrayOf(0x05, code.toByte(), 0x00, 0x01, 0, 0, 0, 0, 0, 0))
        output.flush()
    }

    private fun readFully(input: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val count = input.read(buffer, offset, buffer.size - offset)
            if (count < 0) throw IOException("Conexión SOCKS cerrada")
            offset += count
        }
    }

    override fun close() {
        if (!running.compareAndSet(true, false)) return
        runCatching { serverSocket?.close() }
        serverSocket = null
        val activeClients = synchronized(clients) { clients.toList() }
        activeClients.forEach { runCatching { it.close() } }
        clients.clear()
        executor.shutdownNow()
    }

    private companion object {
        val IPV4_LOOPBACK: InetAddress = InetAddress.getByAddress(
            byteArrayOf(127, 0, 0, 1)
        )
    }
}

/**
 * JSch's Channel.getOutputStream() buffers a partial SSH packet. A long-lived
 * SOCKS client does not close its input after sending a request, so waiting for
 * InputStream.copyTo() to return before flushing deadlocks the request/response
 * exchange: the remote never receives the request and the client eventually
 * closes its side of the pipe. Flush every block while the stream is active.
 */
internal fun copyToSshChannel(
    input: InputStream,
    output: OutputStream,
    bufferSize: Int = DEFAULT_BUFFER_SIZE,
    onFirstFlush: () -> Unit = {}
): Long {
    require(bufferSize > 0) { "bufferSize must be positive" }
    val buffer = ByteArray(bufferSize)
    var copied = 0L
    var firstFlushPending = true
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (count == 0) continue
        output.write(buffer, 0, count)
        output.flush()
        copied += count
        if (firstFlushPending) {
            firstFlushPending = false
            onFirstFlush()
        }
    }
    return copied
}

/**
 * Half-closes only the client -> SSH direction when the SOCKS client reaches
 * EOF. The caller deliberately keeps the channel and client socket alive so
 * the remote response can finish on the opposite direction.
 */
internal fun copyClientToSshAndHalfClose(
    input: InputStream,
    output: OutputStream,
    bufferSize: Int = DEFAULT_BUFFER_SIZE,
    onFirstFlush: () -> Unit = {}
): Long = try {
    copyToSshChannel(input, output, bufferSize, onFirstFlush)
} finally {
    output.close()
}

/**
 * Mirrors the remote half of the SSH channel back to the SOCKS client. Socket
 * streams are normally unbuffered, but flushing each block also keeps wrapped
 * streams deterministic and lets readiness be reported on the first response.
 */
internal fun copyFromSshChannel(
    input: InputStream,
    output: OutputStream,
    bufferSize: Int = DEFAULT_BUFFER_SIZE,
    onFirstFlush: () -> Unit = {}
): Long {
    require(bufferSize > 0) { "bufferSize must be positive" }
    val buffer = ByteArray(bufferSize)
    var copied = 0L
    var firstFlushPending = true
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (count == 0) continue
        output.write(buffer, 0, count)
        output.flush()
        copied += count
        if (firstFlushPending) {
            firstFlushPending = false
            onFirstFlush()
        }
    }
    return copied
}

private class ProfileUserInfo(
    private val password: String
) : UserInfo {
    override fun getPassword(): String = password

    override fun promptYesNo(message: String?): Boolean {
        val text = message.orEmpty().lowercase()
        return !text.contains("changed") &&
            !text.contains("man-in-the-middle") &&
            !text.contains("warning: remote host identification")
    }

    override fun getPassphrase(): String? = null
    override fun promptPassphrase(message: String?): Boolean = false
    override fun promptPassword(message: String?): Boolean = password.isNotBlank()
    override fun showMessage(message: String?) = Unit
}
