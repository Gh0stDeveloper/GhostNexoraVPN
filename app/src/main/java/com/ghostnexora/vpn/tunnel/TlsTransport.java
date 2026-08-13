package com.ghostnexora.vpn.tunnel;

import com.ghostnexora.vpn.data.model.TlsVerificationMode;

import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Single TLS construction path for VPN transports.
 *
 * STRICT delegates certificate-chain and hostname verification to Android.
 * CUSTOM_SNI is an explicit, profile-scoped interoperability policy for
 * injector configurations: it keeps TLS encryption and a valid certificate
 * time window, but does not require a public trust anchor or an SNI/SAN match.
 * The SSH layer still authenticates the final server identity independently.
 */
public final class TlsTransport {
    private static final String HTTPS_ENDPOINT_IDENTIFICATION = "HTTPS";
    private static final X509TrustManager INJECTOR_COMPATIBILITY_TRUST_MANAGER =
            new InjectorCompatibilityTrustManager();

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
        sslContext.init(null, trustManagersFor(verificationMode), null);

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

    /**
     * A null array intentionally selects Android's platform trust store.
     * The compatibility manager is never installed for strict TLS or for any
     * transport that did not explicitly select CUSTOM_SNI.
     */
    static TrustManager[] trustManagersFor(TlsVerificationMode verificationMode) {
        if (verificationMode == null) {
            throw new IllegalArgumentException("verificationMode == null");
        }
        if (verificationMode.getVerifiesCertificateChain()) {
            return null;
        }
        return new TrustManager[]{INJECTOR_COMPATIBILITY_TRUST_MANAGER};
    }

    /**
     * Compatibility equivalent to injector-style "accept certificate": the
     * peer must still present a non-empty, currently valid X.509 leaf, but the
     * chain may be self-signed, private, incomplete, or unrelated to the SNI.
     */
    private static final class InjectorCompatibilityTrustManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            throw new CertificateException("El transporte VPN no acepta certificados de cliente TLS");
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            if (chain == null || chain.length == 0) {
                throw new CertificateException("El servidor TLS no presentó certificados");
            }
            X509Certificate leaf = chain[0];
            if (leaf == null) {
                throw new CertificateException("La cadena TLS contiene un certificado vacío");
            }
            leaf.checkValidity();
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
