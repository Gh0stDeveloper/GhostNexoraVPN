package com.ghostnexora.vpn.data.model

/**
 * Política TLS aplicada únicamente a los transportes SSH encapsulados en TLS.
 *
 * CUSTOM_SNI reproduce el flujo de HTTP Injector/HTTP Custom: conserva el host
 * del perfil como identidad lógica SSH, pero abre el socket TCP/TLS contra el
 * SNI configurado. La cadena del certificado continúa validándose con los
 * TrustManager de Android, aunque no se exige coincidencia SNI/SAN.
 */
enum class TlsVerificationMode(
    val id: String,
    val label: String,
    val description: String,
    val verifiesHostname: Boolean
) {
    STRICT(
        id = "strict",
        label = "TLS estricto",
        description = "Conecta al host del servidor, valida la cadena del certificado y exige que el SNI coincida con su SAN.",
        verifiesHostname = true
    ),
    CUSTOM_SNI(
        id = "custom_sni",
        label = "Compatible con HTTP Injector/Custom",
        description = "Conecta TCP/TLS al SNI configurado y después inicia SSH, sin exigir coincidencia SAN; la cadena TLS y la identidad SSH siguen verificándose.",
        verifiesHostname = false
    );

    companion object {
        fun fromStored(value: String?): TlsVerificationMode = when (value?.trim()?.lowercase()) {
            CUSTOM_SNI.id,
            "custom",
            "http_custom",
            "injector",
            "relaxed_hostname",
            "allow_sni_mismatch" -> CUSTOM_SNI

            else -> STRICT
        }
    }
}
