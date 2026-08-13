package com.ghostnexora.vpn.tunnel;

import com.ghostnexora.vpn.data.model.TlsVerificationMode;

import java.net.Socket;
import java.util.Collections;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;

/**
 * Single TLS construction path for VPN transports.
 *
 * The platform TrustManager remains authoritative. CUSTOM_SNI changes only the
 * hostname verification policy already represented by TlsVerificationMode; it
 * never installs a trust-all certificate manager.
 */
public final class TlsTransport {
    private static final String HTTPS_ENDPOINT_IDENTIFICATION = "HTTPS";

    private TlsTransport() {
    }

    public static SSLSocket upgrade(
            Socket connectedSocket,
            String targetHost,
            int targetPort,
            String sniHost,
            TlsVerificationMode verificationMode
    ) throws Exception {
        if (targetHost == null || targetHost.trim().isEmpty()) {
            throw new IllegalArgumentException("El host TLS no puede estar vacío");
        }
        if (targetPort < 1 || targetPort > 65_535) {
            throw new IllegalArgumentException("El puerto TLS es inválido");
        }
        if (sniHost == null || sniHost.trim().isEmpty()) {
            throw new IllegalArgumentException("El SNI no puede estar vacío");
        }
        if (connectedSocket == null) {
            throw new IllegalArgumentException("connectedSocket == null");
        }
        if (verificationMode == null) {
            throw new IllegalArgumentException("verificationMode == null");
        }

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, null, null);

        String verificationHost = verificationMode.getVerifiesHostname() ? sniHost : targetHost;
        SSLSocket sslSocket = (SSLSocket) sslContext.getSocketFactory().createSocket(
                connectedSocket,
                verificationHost,
                targetPort,
                true
        );
        sslSocket.setUseClientMode(true);
        sslSocket.setSSLParameters(configureParameters(
                sslSocket.getSSLParameters(),
                sniHost,
                verificationMode
        ));
        sslSocket.startHandshake();
        return sslSocket;
    }

    public static SSLParameters configureParameters(
            SSLParameters current,
            String sniHost,
            TlsVerificationMode verificationMode
    ) {
        if (current == null) {
            throw new IllegalArgumentException("current == null");
        }
        if (sniHost == null || sniHost.trim().isEmpty()) {
            throw new IllegalArgumentException("El SNI no puede estar vacío");
        }
        if (verificationMode == null) {
            throw new IllegalArgumentException("verificationMode == null");
        }

        current.setEndpointIdentificationAlgorithm(
                verificationMode.getVerifiesHostname() ? HTTPS_ENDPOINT_IDENTIFICATION : null
        );
        try {
            current.setServerNames(Collections.singletonList(new SNIHostName(sniHost)));
        } catch (Throwable error) {
            throw new IllegalArgumentException("SNI TLS inválido: " + sniHost, error);
        }
        return current;
    }
}
