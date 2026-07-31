package com.ghostnexora.vpn.tunnel

import com.ghostnexora.vpn.data.model.ConnectionMode
import com.ghostnexora.vpn.data.model.VpnProfile

/** Stable user-facing error with a searchable code and remediation. */
data class VpnFailure(
    val code: String,
    val stage: String,
    val title: String,
    val detail: String,
    val solution: String
) {
    fun userMessage(): String = "$title · $solution [$code]"
    fun logMessage(): String = "[$code] [$stage] $title · $detail · Solución: $solution"
}

object ConnectionErrorCatalog {
    fun classify(error: Throwable, profile: VpnProfile): VpnFailure {
        val raw = generateSequence(error) { it.cause }
            .mapNotNull { it.message?.takeIf(String::isNotBlank) }
            .joinToString(" · ")
            .take(360)
        val lower = raw.lowercase()

        return when {
            lower.contains("no hay una red") || lower.contains("network is unreachable") -> failure(
                "NET-001", "NETWORK", "No physical Internet connection", raw,
                "Enable mobile data or Wi-Fi and retry."
            )
            lower.contains("unknownhost") || lower.contains("unable to resolve") || lower.contains("name or service") -> failure(
                "DNS-001", "DNS", "The server name could not be resolved", raw,
                "Check the host and the selected DNS servers."
            )
            lower.contains("connection refused") -> failure(
                "TCP-002", "TCP", "The remote port rejected the connection", raw,
                "Verify the server address, port and service status."
            )
            lower.contains("timeout") || lower.contains("timed out") || lower.contains("deadline exceeded") -> failure(
                "NET-003", "NETWORK", "The server did not respond in time", raw,
                "Check signal quality, server availability and transport parameters."
            )
            lower.contains("407") || lower.contains("proxy authentication") -> failure(
                "PROXY-407", "PROXY", "Proxy authentication is required", raw,
                "Verify the proxy username and password."
            )
            lower.contains("proxy") && (lower.contains("refused") || lower.contains("failed")) -> failure(
                "PROXY-002", "PROXY", "The proxy connection failed", raw,
                "Check proxy type, host, port and credentials."
            )
            lower.contains("certificate") || lower.contains("certificado") ||
                lower.contains("trust anchor") || lower.contains("hostname") || lower.contains("sni") -> failure(
                "TLS-004", "TLS", "TLS certificate or SNI validation failed", raw,
                if (
                    profile.selectedMode.isSsh &&
                    profile.selectedMode.usesTls &&
                    !profile.selectedTlsVerificationMode.verifiesHostname
                ) {
                    "Compatibility mode already allows an SNI/SAN mismatch. Check certificate trust, validity and the TLS service."
                } else {
                    "Use the SNI configured by the server and a certificate valid for that name, or explicitly enable HTTP Custom SNI compatibility for this SSH profile."
                }
            )
            lower.contains("auth fail") || lower.contains("authentication") || lower.contains("autenticación") -> failure(
                if (profile.selectedMode.isSsh) "SSH-401" else "AUTH-401",
                if (profile.selectedMode.isSsh) "SSH" else "AUTH",
                "Authentication failed", raw,
                if (profile.selectedMode.isSsh) {
                    "Verify the SSH username and password."
                } else {
                    "Verify the UUID, password or authentication token required by the selected protocol."
                }
            )
            lower.contains("hostkey") || lower.contains("host key") -> failure(
                "SSH-409", "SSH", "The SSH server identity changed", raw,
                "Confirm the server change and reset its stored fingerprint only when trusted."
            )
            lower.contains("classnotfoundexception") || lower.contains("com.jcraft.jsch") -> failure(
                "SSH-500", "SSH", "The SSH runtime could not initialize", raw,
                "Install the latest build and export the diagnostic report if it persists."
            )
            lower.contains("uuid") -> failure(
                "XRAY-UUID", "XRAY", "The VLESS/VMess identifier is invalid", raw,
                "Enter a valid UUID supplied by the server."
            )
            lower.contains("app-routing") || lower.contains("split tunneling") || lower.contains("selected application") -> failure(
                "APP-ROUTE-001", "APP_ROUTING", "The application routing rule is invalid", raw,
                "Select at least one installed application or change the routing mode."
            )
            lower.contains("package") && lower.contains("not found") -> failure(
                "APP-ROUTE-404", "APP_ROUTING", "A selected application is no longer installed", raw,
                "Refresh the application list and remove unavailable packages."
            )
            profile.selectedMode.isSsh && lower.contains("closed pipe") -> failure(
                "SSH-BRIDGE-502", "SSH",
                "${profile.connectionModeLabel} authenticated, but its forwarding channel closed",
                raw,
                "The selected SSH profile reached authentication. Install the latest build; if it persists, verify that the SSH account permits direct-tcpip forwarding and export the SSH/SOCKS log."
            )
            lower.contains("no entregan acceso") || lower.contains("no pudo entregar") ||
                lower.contains("generate_204") || lower.contains("outbound") -> failure(
                if (profile.selectedMode.isSsh) "SSH-ROUTE-204" else "ROUTE-204",
                if (profile.selectedMode.isSsh) "SSH" else "ROUTING",
                if (profile.selectedMode.isSsh) {
                    "${profile.connectionModeLabel} authenticated, but SSH forwarding has no Internet"
                } else {
                    "The tunnel started but the outbound has no Internet"
                },
                raw,
                if (profile.selectedMode.isSsh) {
                    "Keep this profile as ${profile.connectionModeLabel}; verify SSH TCP forwarding and Internet egress on that SSH server."
                } else {
                    "Check only the server, credentials and transport fields required by ${profile.connectionModeLabel}."
                }
            )
            lower.contains("libv2ray") || lower.contains("xray core") || lower.contains("go_seq") -> failure(
                "XRAY-500", "XRAY", "Xray Core could not start", raw,
                "Review the profile transport fields and export the complete diagnostic report."
            )
            lower.contains("tun") || lower.contains("descriptor") -> failure(
                "TUN-500", "TUN", "Android could not establish the VPN interface", raw,
                "Disconnect other VPN applications, reauthorize VPN access and retry."
            )
            profile.selectedMode == ConnectionMode.V2RAY -> failure(
                "XRAY-000", "XRAY", "V2Ray/Xray connection failed", raw,
                "Run Connection diagnostics to identify DNS, TCP, TLS or outbound failure."
            )
            else -> failure(
                "VPN-000", "VPN", "The connection could not be completed", raw.ifBlank { error.javaClass.simpleName },
                "Run Connection diagnostics and export the report."
            )
        }
    }

    private fun failure(code: String, stage: String, title: String, detail: String, solution: String) =
        VpnFailure(code, stage, title, detail.ifBlank { "No additional detail" }, solution)
}
