package com.ghostnexora.vpn.data.model

/**
 * Modos de conexión disponibles para un perfil.
 *
 * Todos los modos se resuelven a un túnel TUN real. Los modos SSH usan una
 * sesión SSH como transporte y un bridge SOCKS local; V2Ray, Trojan y UDP
 * usan Xray Core como motor de datos.
 */
enum class ConnectionMode(
    val id: String,
    val label: String,
    val family: String,
    val description: String,
    val usesTls: Boolean = false,
    val requiresSni: Boolean = false,
    val requiresProxy: Boolean = false,
    val requiresPayload: Boolean = false,
    val requiredFields: List<String> = emptyList(),
    val supported: Boolean = true
) {
    SSH_DIRECT(
        id = "ssh_direct",
        label = "Conexión directa",
        family = "ssh",
        description = "Túnel SSH directo con transporte cifrado y tráfico Android enrutado por TUN.",
        requiredFields = listOf("Host/IP", "Puerto", "Usuario", "Contraseña")
    ),
    SSL_SNI(
        id = "ssl_sni",
        label = "SSH + SSL",
        family = "ssh",
        description = "SSH encapsulado en TLS con validación de certificado y SNI.",
        usesTls = true,
        requiresSni = true,
        requiredFields = listOf("Host/IP", "Puerto", "Usuario", "Contraseña", "SNI")
    ),
    SSH_PAYLOAD(
        id = "ssh_payload",
        label = "SSH + Payload",
        family = "ssh",
        description = "Inyección de payload HTTP antes del handshake SSH.",
        requiresPayload = true,
        requiredFields = listOf("Host/IP", "Puerto", "Usuario", "Contraseña", "Payload")
    ),
    SSH_PAYLOAD_SSL(
        id = "ssh_payload_ssl",
        label = "SSH + SSL + Payload",
        family = "ssh",
        description = "TLS/SNI validado, payload HTTP y sesión SSH cifrada.",
        usesTls = true,
        requiresSni = true,
        requiresPayload = true,
        requiredFields = listOf("Host/IP", "Puerto", "Usuario", "Contraseña", "SNI", "Payload")
    ),
    SSH_PROXY(
        id = "ssh_proxy",
        label = "SSH + Proxy",
        family = "ssh",
        description = "SSH a través de proxy HTTP CONNECT o SOCKS5.",
        requiresProxy = true,
        requiredFields = listOf("Host/IP", "Puerto", "Usuario", "Contraseña", "Proxy Host", "Proxy Puerto", "Tipo de proxy")
    ),
    SSH_PAYLOAD_PROXY(
        id = "ssh_payload_proxy",
        label = "SSH + Payload + Proxy:Port",
        family = "ssh",
        description = "Payload personalizado enviado a través del proxy antes de levantar SSH.",
        requiresProxy = true,
        requiresPayload = true,
        requiredFields = listOf("Host/IP", "Puerto", "Usuario", "Contraseña", "Payload", "Proxy Host", "Proxy Puerto", "Tipo de proxy")
    ),
    SSH_PAYLOAD_PROXY_SSL(
        id = "ssh_payload_proxy_ssl",
        label = "SSH + Payload + Proxy:Port + SSL",
        family = "ssh",
        description = "Proxy, payload, TLS/SNI validado y SSH en una única cadena de transporte.",
        usesTls = true,
        requiresSni = true,
        requiresProxy = true,
        requiresPayload = true,
        requiredFields = listOf("Host/IP", "Puerto", "Usuario", "Contraseña", "SNI", "Payload", "Proxy Host", "Proxy Puerto")
    ),
    UDP(
        id = "udp",
        label = "UDP",
        family = "udp",
        description = "Túnel UDP cifrado mediante Hysteria2 sobre QUIC/TLS.",
        usesTls = true,
        requiresSni = true,
        requiredFields = listOf("Host/IP", "Puerto", "Contraseña/Auth", "SNI")
    ),
    V2RAY(
        id = "v2ray",
        label = "V2Ray",
        family = "v2ray",
        description = "Motor Xray compatible con perfiles VLESS y VMess.",
        requiredFields = listOf("Host/IP", "Puerto", "UUID / User ID")
    ),
    TROJAN(
        id = "trojan",
        label = "Trojan",
        family = "trojan",
        description = "Trojan cifrado con TLS y validación SNI.",
        usesTls = true,
        requiresSni = true,
        requiredFields = listOf("Host/IP", "Puerto", "Password", "SNI")
    );

    val isSsh: Boolean
        get() = family == "ssh"

    companion object {
        fun fromId(id: String?): ConnectionMode? = entries.firstOrNull { it.id == id }

        fun fromStored(
            storedMode: String?,
            legacyMethod: String? = null,
            sslEnabled: Boolean? = null
        ): ConnectionMode {
            fromId(storedMode)?.let { return it }

            val method = legacyMethod?.lowercase().orEmpty()
            return when {
                method.contains("v2ray") || method.contains("vless") || method.contains("vmess") -> V2RAY
                method.contains("trojan") -> TROJAN
                method.contains("udp") || method.contains("hysteria") -> UDP
                method.contains("payload") && method.contains("proxy") && (method.contains("ssl") || sslEnabled == true) -> SSH_PAYLOAD_PROXY_SSL
                method.contains("payload") && method.contains("proxy") -> SSH_PAYLOAD_PROXY
                method.contains("payload") && (method.contains("ssl") || sslEnabled == true) -> SSH_PAYLOAD_SSL
                method.contains("payload") -> SSH_PAYLOAD
                method.contains("proxy") -> SSH_PROXY
                method.contains("ssl") || sslEnabled == true -> SSL_SNI
                else -> SSH_DIRECT
            }
        }
    }
}
