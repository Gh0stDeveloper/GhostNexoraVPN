package com.ghostnexora.vpn.data.model

/**
 * Modos de conexión disponibles para un perfil.
 * La app usa este valor para decidir qué campos mostrar y qué estrategia de túnel aplicar.
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
    SSL_SNI(
        id = "ssl_sni",
        label = "SSL / TLS / SNI",
        family = "ssl",
        description = "Túnel TLS/SNI directo contra el host configurado.",
        usesTls = true,
        requiresSni = true,
        requiredFields = listOf("Host/IP", "Puerto", "Usuario", "Contraseña", "SNI")
    ),
    SSH_DIRECT(
        id = "ssh_direct",
        label = "SSH directo",
        family = "ssh",
        description = "Conexión SSH estándar usando host, puerto y credenciales.",
        requiredFields = listOf("Host/IP", "Puerto", "Usuario", "Contraseña")
    ),
    SSH_PROXY(
        id = "ssh_proxy",
        label = "SSH + Proxy",
        family = "ssh",
        description = "SSH atravesando un proxy HTTP o SOCKS5.",
        requiresProxy = true,
        requiredFields = listOf("Host/IP", "Puerto", "Usuario", "Contraseña", "Proxy Host", "Proxy Puerto", "Tipo de proxy")
    ),
    SSH_PAYLOAD(
        id = "ssh_payload",
        label = "SSH + Payload",
        family = "ssh",
        description = "SSH con un payload personalizado antes de iniciar el túnel.",
        requiresPayload = true,
        requiredFields = listOf("Host/IP", "Puerto", "Usuario", "Contraseña", "Payload")
    ),
    SSH_PAYLOAD_SSL(
        id = "ssh_payload_ssl",
        label = "SSH + Payload + SSL/TLS",
        family = "ssh",
        description = "SSH, payload y capa TLS/SNI sobre el mismo perfil.",
        usesTls = true,
        requiresSni = true,
        requiresPayload = true,
        requiredFields = listOf("Host/IP", "Puerto", "Usuario", "Contraseña", "SNI", "Payload")
    ),
    SSH_PAYLOAD_PROXY(
        id = "ssh_payload_proxy",
        label = "SSH + Payload + Proxy",
        family = "ssh",
        description = "SSH con payload y salida a través de proxy.",
        requiresProxy = true,
        requiresPayload = true,
        requiredFields = listOf("Host/IP", "Puerto", "Usuario", "Contraseña", "Payload", "Proxy Host", "Proxy Puerto", "Tipo de proxy")
    ),
    V2RAY(
        id = "v2ray",
        label = "V2Ray",
        family = "v2ray",
        description = "Motor planificado para un core V2Ray dedicado.",
        requiredFields = listOf("Host/IP", "Puerto", "UUID", "Security"),
        supported = false
    ),
    TROJAN(
        id = "trojan",
        label = "Trojan",
        family = "trojan",
        description = "Motor planificado para un core Trojan dedicado.",
        requiredFields = listOf("Host/IP", "Puerto", "Password", "SNI"),
        supported = false
    ),
    UDP(
        id = "udp",
        label = "UDP",
        family = "udp",
        description = "Transporte UDP / QUIC / WireGuard para futura integración.",
        requiredFields = listOf("Host/IP", "Puerto"),
        supported = false
    );

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
                method.contains("v2ray") || method.contains("vless") -> V2RAY
                method.contains("trojan") -> TROJAN
                method.contains("udp") || method.contains("wireguard") -> UDP
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
