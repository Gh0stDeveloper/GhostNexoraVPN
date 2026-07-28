package com.ghostnexora.vpn.tunnel

import com.ghostnexora.vpn.data.model.TlsVerificationMode
import java.net.Socket
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket

/**
 * Único punto de construcción TLS para SSH y diagnósticos.
 *
 * El socket siempre usa los TrustManager de la plataforma. La política
 * CUSTOM_SNI cambia únicamente la verificación de hostname para permitir que
 * el SNI y el SAN del certificado sean distintos.
 */
object TlsTransport {
    fun upgrade(
        connectedSocket: Socket,
        targetHost: String,
        targetPort: Int,
        sniHost: String,
        verificationMode: TlsVerificationMode
    ): SSLSocket {
        require(targetHost.isNotBlank()) { "El host TLS no puede estar vacío" }
        require(targetPort in 1..65535) { "El puerto TLS es inválido" }
        require(sniHost.isNotBlank()) { "El SNI no puede estar vacío" }

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, null, null)

        val verificationHost = if (verificationMode.verifiesHostname) sniHost else targetHost
        val sslSocket = sslContext.socketFactory.createSocket(
            connectedSocket,
            verificationHost,
            targetPort,
            true
        ) as SSLSocket
        sslSocket.useClientMode = true
        sslSocket.sslParameters = configureParameters(
            current = sslSocket.sslParameters,
            sniHost = sniHost,
            verificationMode = verificationMode
        )
        sslSocket.startHandshake()
        return sslSocket
    }

    internal fun configureParameters(
        current: SSLParameters,
        sniHost: String,
        verificationMode: TlsVerificationMode
    ): SSLParameters = current.apply {
        endpointIdentificationAlgorithm =
            if (verificationMode.verifiesHostname) HTTPS_ENDPOINT_IDENTIFICATION else null
        serverNames = listOf(
            runCatching { SNIHostName(sniHost) }
                .getOrElse { throw IllegalArgumentException("SNI TLS inválido: $sniHost", it) }
        )
    }

    private const val HTTPS_ENDPOINT_IDENTIFICATION = "HTTPS"
}
