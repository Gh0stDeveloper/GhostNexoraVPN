package com.ghostnexora.vpn.data.model

/**
 * Política TLS aplicada únicamente a los transportes SSH encapsulados en TLS.
 *
 * El host del perfil siempre conserva dos responsabilidades: extremo TCP real
 * e identidad lógica SSH. El SNI se envía únicamente en ClientHello para que
 * el servidor TLS seleccione el certificado o virtual host correspondiente.
 *
 * CUSTOM_SNI conserva los TrustManager de Android, pero no exige coincidencia
 * entre el SNI configurado y los SAN del certificado. Nunca sustituye el host
 * SSH por el dominio SNI como destino físico.
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
        description = "Conecta al servidor SSH, envía el SNI configurado y exige que el certificado sea válido para ese SNI.",
        verifiesHostname = true
    ),
    CUSTOM_SNI(
        id = "custom_sni",
        label = "Compatible con HTTP Injector/Custom",
        description = "Conecta TCP al servidor SSH y usa el dominio configurado solo como SNI TLS, sin exigir coincidencia SNI/SAN; la cadena TLS y la identidad SSH siguen verificándose.",
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
