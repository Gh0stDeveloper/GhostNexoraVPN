package com.ghostnexora.vpn.data.model

/**
 * Política TLS aplicada únicamente a los transportes SSH encapsulados en TLS.
 *
 * CUSTOM_SNI conserva la validación de la cadena de certificados del sistema,
 * pero no exige que el SNI enviado aparezca en el SAN del certificado. Esto
 * reproduce la compatibilidad necesaria para servidores tipo HTTP Custom sin
 * convertir la aplicación en un cliente global "trust all".
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
        description = "Valida la cadena del certificado y exige que el SNI coincida con su SAN.",
        verifiesHostname = true
    ),
    CUSTOM_SNI(
        id = "custom_sni",
        label = "Compatible con HTTP Custom",
        description = "Envía el SNI configurado sin exigir coincidencia SAN; la cadena TLS y la identidad SSH siguen verificándose.",
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
