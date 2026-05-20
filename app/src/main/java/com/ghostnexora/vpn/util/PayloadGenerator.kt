package com.ghostnexora.vpn.util

/**
 * Genera plantillas de payload reutilizables para distintos escenarios.
 *
 * La app no depende de un payload único. Este helper produce variantes
 * seguras y editables para navegación, streaming, juego y perfiles custom.
 */
enum class PayloadUseCase(val label: String, val description: String) {
    BROWSING(
        label = "Navegación",
        description = "Payload simple y estable para navegación web y apps ligeras"
    ),
    STREAMING(
        label = "Streaming",
        description = "Incluye encabezados más tolerantes para video y servicios pesados"
    ),
    GAMING(
        label = "Gaming",
        description = "Reduce ruido en el handshake y prioriza persistencia"
    ),
    CUSTOM(
        label = "Personalizado",
        description = "Base editable para adaptar a un caso concreto"
    )
}

object PayloadGenerator {

    fun presets(): List<PayloadUseCase> = PayloadUseCase.entries

    fun generate(
        preset: PayloadUseCase,
        host: String,
        port: Int,
        sni: String = host,
        path: String = "/",
        userAgent: String = "Mozilla/5.0",
        extraHostHeader: String = host
    ): String {
        val target = "${host.trim()}:${port.coerceIn(1, 65535)}"
        val hostHeader = extraHostHeader.ifBlank { host.trim() }.ifBlank { sni.trim() }
        val safePath = path.ifBlank { "/" }

        val baseLines = when (preset) {
            PayloadUseCase.BROWSING -> listOf(
                "CONNECT [host_port] HTTP/1.1",
                "Host: [host]",
                "Connection: Keep-Alive",
                "Proxy-Connection: Keep-Alive",
                "Pragma: no-cache",
                "Cache-Control: no-cache"
            )

            PayloadUseCase.STREAMING -> listOf(
                "CONNECT [host_port] HTTP/1.1",
                "Host: [host]",
                "Connection: Keep-Alive",
                "X-Online-Host: [sni]",
                "User-Agent: $userAgent",
                "Accept: */*",
                "Cache-Control: no-cache"
            )

            PayloadUseCase.GAMING -> listOf(
                "CONNECT [host_port] HTTP/1.1",
                "Host: [host]",
                "Connection: Keep-Alive",
                "X-Online-Host: [sni]",
                "X-Forward-Host: [host]",
                "Pragma: no-cache"
            )

            PayloadUseCase.CUSTOM -> listOf(
                "CONNECT [host_port] HTTP/1.1",
                "Host: [host]",
                "Connection: Keep-Alive",
                "User-Agent: $userAgent"
            )
        }

        return baseLines.joinToString(separator = "[crlf]") + "[crlf][crlf]"
            .replace("[host_port]", target)
            .replace("[host]", hostHeader)
            .replace("[sni]", sni.ifBlank { hostHeader })
            .replace("[path]", safePath)
            .replace("[crlf]", "\r\n")
    }
}
