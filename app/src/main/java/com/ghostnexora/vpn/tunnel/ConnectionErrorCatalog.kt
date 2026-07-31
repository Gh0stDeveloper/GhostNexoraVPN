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
                "NET-001", "NETWORK", "No hay una conexión física disponible", raw,
                "Activa los datos móviles o Wi-Fi y vuelve a intentarlo."
            )
            lower.contains("unknownhost") || lower.contains("unable to resolve") || lower.contains("name or service") -> failure(
                "DNS-001", "DNS", "No se pudo resolver el nombre del servidor", raw,
                "Revisa el host y los servidores DNS seleccionados."
            )
            profile.selectedMode.isSsh && (
                lower.contains("ruta xray") ||
                    lower.contains("health-check") ||
                    lower.contains("xray socks outbound")
                ) -> failure(
                "SSH-ROUTE-204", "SSH",
                "${profile.connectionModeLabel} autenticó, pero falló su ruta Xray/SOCKS",
                raw,
                "Conserva el perfil como ${profile.connectionModeLabel}; exporta las etapas SSH/SOCKS para identificar el canal direct-tcpip, subida, bajada o TLS remoto."
            )
            lower.contains("tcp-all-failed") || lower.contains("no fue posible conectar con ninguna ip") -> failure(
                "TCP-003", "TCP", "No se pudo abrir el extremo TCP del transporte", raw,
                if (
                    profile.selectedMode.isSsh &&
                    profile.selectedMode.usesTls &&
                    !profile.selectedTlsVerificationMode.verifiesHostname
                ) {
                    "El modo compatible abre TCP contra el host SSH del perfil y usa el dominio configurado únicamente como SNI TLS. Verifica el host SSH, puerto y disponibilidad del servidor."
                } else {
                    "Verifica que el host y puerto del servidor acepten conexiones desde tu red actual."
                }
            )
            lower.contains("connection refused") || lower.contains("econnrefused") -> failure(
                "TCP-002", "TCP", "El extremo remoto rechazó la conexión TCP", raw,
                if (
                    profile.selectedMode.isSsh &&
                    profile.selectedMode.usesTls &&
                    !profile.selectedTlsVerificationMode.verifiesHostname
                ) {
                    "La conexión TCP se realiza contra el host SSH, no contra el SNI. Revisa el servidor SSH y el puerto configurado."
                } else {
                    "La aplicación alcanzó la IP remota, pero no había un servicio aceptando conexiones en ese puerto. Verifica host y puerto."
                }
            )
            lower.contains("timeout") || lower.contains("timed out") || lower.contains("deadline exceeded") -> failure(
                "NET-003", "NETWORK", "El servidor no respondió a tiempo", raw,
                "Revisa la señal, la disponibilidad del servidor y los parámetros del transporte."
            )
            lower.contains("407") || lower.contains("proxy authentication") -> failure(
                "PROXY-407", "PROXY", "El proxy requiere autenticación", raw,
                "Verifica el usuario y la contraseña del proxy."
            )
            lower.contains("proxy") && (lower.contains("refused") || lower.contains("failed")) -> failure(
                "PROXY-002", "PROXY", "Falló la conexión con el proxy", raw,
                "Revisa tipo, host, puerto y credenciales del proxy."
            )
            lower.contains("certificate") || lower.contains("certificado") ||
                lower.contains("trust anchor") || lower.contains("hostname") || lower.contains("sni") -> failure(
                "TLS-004", "TLS", "Falló la validación TLS o SNI", raw,
                if (
                    profile.selectedMode.isSsh &&
                    profile.selectedMode.usesTls &&
                    !profile.selectedTlsVerificationMode.verifiesHostname
                ) {
                    "El modo compatible ya permite una diferencia SNI/SAN. Revisa la confianza, vigencia y servicio TLS del certificado."
                } else {
                    "Usa el SNI indicado por el administrador o activa explícitamente la compatibilidad SNI tipo HTTP Custom para este perfil SSH."
                }
            )
            profile.selectedMode.isSsh && profile.selectedMode.usesTls && (
                lower.contains("connection is closed by foreign host") ||
                    lower.contains("closed by foreign host") ||
                    lower.contains("foreign host closed")
                ) -> failure(
                "SSH-TLS-502", "SSH",
                "TLS se estableció, pero el extremo cerró el intercambio SSH",
                raw,
                "Comprueba que Host sea el servidor SSH real y que SNI sea solo el dominio TLS usado por esa configuración. Si el mismo perfil funciona en HTTP Injector, conserva exactamente su host, puerto y SNI."
            )
            lower.contains("auth fail") || lower.contains("authentication") || lower.contains("autenticación") -> failure(
                if (profile.selectedMode.isSsh) "SSH-401" else "AUTH-401",
                if (profile.selectedMode.isSsh) "SSH" else "AUTH",
                "Falló la autenticación", raw,
                if (profile.selectedMode.isSsh) {
                    "Verifica el usuario y la contraseña SSH."
                } else {
                    "Verifica el UUID, contraseña o token requerido por el protocolo."
                }
            )
            lower.contains("hostkey") || lower.contains("host key") -> failure(
                "SSH-409", "SSH", "Cambió la identidad del servidor SSH", raw,
                "Confirma el cambio del servidor y restablece su huella solo cuando sea confiable."
            )
            lower.contains("classnotfoundexception") || lower.contains("com.jcraft.jsch") -> failure(
                "SSH-500", "SSH", "No se pudo inicializar el runtime SSH", raw,
                "Instala la compilación más reciente y exporta el diagnóstico si continúa."
            )
            lower.contains("uuid") -> failure(
                "XRAY-UUID", "XRAY", "El identificador VLESS/VMess no es válido", raw,
                "Introduce un UUID válido proporcionado por el servidor."
            )
            lower.contains("app-routing") || lower.contains("split tunneling") || lower.contains("selected application") -> failure(
                "APP-ROUTE-001", "APP_ROUTING", "La regla de aplicaciones VPN no es válida", raw,
                "Selecciona al menos una aplicación instalada o cambia el modo de enrutamiento."
            )
            lower.contains("package") && lower.contains("not found") -> failure(
                "APP-ROUTE-404", "APP_ROUTING", "Una aplicación seleccionada ya no está instalada", raw,
                "Actualiza la lista y elimina los paquetes no disponibles."
            )
            profile.selectedMode.isSsh && lower.contains("closed pipe") -> failure(
                "SSH-BRIDGE-502", "SSH",
                "${profile.connectionModeLabel} autenticó, pero se cerró el canal de reenvío",
                raw,
                "El perfil alcanzó la autenticación SSH. Verifica que la cuenta permita direct-tcpip y exporta el registro SSH/SOCKS."
            )
            lower.contains("no entregan acceso") || lower.contains("no pudo entregar") ||
                lower.contains("generate_204") || lower.contains("outbound") -> failure(
                if (profile.selectedMode.isSsh) "SSH-ROUTE-204" else "ROUTE-204",
                if (profile.selectedMode.isSsh) "SSH" else "ROUTING",
                if (profile.selectedMode.isSsh) {
                    "${profile.connectionModeLabel} autenticó, pero el reenvío SSH no tiene Internet"
                } else {
                    "El túnel inició, pero el outbound no tiene Internet"
                },
                raw,
                if (profile.selectedMode.isSsh) {
                    "Conserva el perfil como ${profile.connectionModeLabel}; verifica TCP forwarding y salida a Internet en el servidor SSH."
                } else {
                    "Revisa solo el servidor, las credenciales y los campos de transporte requeridos por ${profile.connectionModeLabel}."
                }
            )
            lower.contains("libv2ray") || lower.contains("xray core") || lower.contains("go_seq") -> failure(
                "XRAY-500", "XRAY", "No se pudo iniciar Xray Core", raw,
                "Revisa los campos de transporte y exporta el diagnóstico completo."
            )
            lower.contains("tun") || lower.contains("descriptor") -> failure(
                "TUN-500", "TUN", "Android no pudo establecer la interfaz VPN", raw,
                "Desconecta otras VPN, vuelve a autorizar el acceso VPN y reintenta."
            )
            profile.selectedMode == ConnectionMode.V2RAY -> failure(
                "XRAY-000", "XRAY", "Falló la conexión V2Ray/Xray", raw,
                "Ejecuta el diagnóstico para identificar la etapa DNS, TCP, TLS u outbound."
            )
            else -> failure(
                "VPN-000", "VPN", "No se pudo completar la conexión", raw.ifBlank { error.javaClass.simpleName },
                "Ejecuta el diagnóstico y exporta el informe."
            )
        }
    }

    private fun failure(code: String, stage: String, title: String, detail: String, solution: String) =
        VpnFailure(code, stage, title, detail.ifBlank { "Sin detalle adicional" }, solution)
}
