package com.ghostnexora.vpn.data.model

/** IP behavior used by Android's TUN interface and Xray DNS resolution. */
enum class IpMode(val id: String, val label: String) {
    IPV4_ONLY("ipv4_only", "IPv4 only"),
    IPV4_PREFERRED("ipv4_preferred", "IPv4 preferred"),
    DUAL_STACK("dual_stack", "IPv4 + IPv6");

    val capturesIpv6: Boolean get() = this != IPV4_ONLY
    val xrayQueryStrategy: String get() = if (this == DUAL_STACK) "UseIP" else "UseIPv4"

    companion object {
        fun fromId(value: String?): IpMode = entries.firstOrNull { it.id == value } ?: IPV4_PREFERRED
    }
}

/** DNS presets. CUSTOM accepts literal IPv4/IPv6 resolver addresses. */
enum class DnsMode(val id: String, val label: String) {
    AUTOMATIC("automatic", "Automatic protected DNS"),
    CLOUDFLARE("cloudflare", "Cloudflare"),
    GOOGLE("google", "Google"),
    CUSTOM("custom", "Custom DNS");

    companion object {
        fun fromId(value: String?): DnsMode = entries.firstOrNull { it.id == value } ?: AUTOMATIC
    }
}

data class NetworkPreferences(
    val ipMode: IpMode = IpMode.IPV4_PREFERRED,
    val mtu: Int = DEFAULT_MTU,
    val dnsMode: DnsMode = DnsMode.AUTOMATIC,
    val customDnsPrimary: String = "1.1.1.1",
    val customDnsSecondary: String = "8.8.8.8",
    val reconnectMaxAttempts: Int = 8
) {
    val validatedMtu: Int get() = mtu.coerceIn(MIN_MTU, MAX_MTU)
    val validatedReconnectAttempts: Int get() = reconnectMaxAttempts.coerceIn(1, 12)

    fun dnsServers(): List<String> = when (dnsMode) {
        DnsMode.AUTOMATIC -> listOf("1.1.1.1", "8.8.8.8")
        DnsMode.CLOUDFLARE -> listOf("1.1.1.1", "1.0.0.1")
        DnsMode.GOOGLE -> listOf("8.8.8.8", "8.8.4.4")
        DnsMode.CUSTOM -> listOf(customDnsPrimary, customDnsSecondary)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .ifEmpty { listOf("1.1.1.1") }
    }

    companion object {
        const val MIN_MTU = 1280
        const val MAX_MTU = 1500
        const val DEFAULT_MTU = 1400
        val MTU_PRESETS = listOf(1280, 1360, 1400, 1450, 1500)
    }
}