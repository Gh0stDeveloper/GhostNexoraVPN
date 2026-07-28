package com.ghostnexora.vpn.util

import java.security.SecureRandom
import java.util.Locale
import java.util.Random


data class PayloadContext(
    val host: String,
    val port: Int,
    val sni: String = host,
    val proxyHost: String = "",
    val proxyPort: Int = 0
)

sealed interface PayloadAction {
    data class Send(val text: String) : PayloadAction
    data class Delay(val millis: Long) : PayloadAction
}

data class PayloadPlan(
    val actions: List<PayloadAction>,
    val rendered: String,
    val visiblePreview: String,
    val totalDelayMs: Long,
    val segmentCount: Int
)

data class PayloadValidation(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

enum class PayloadTemplate(val label: String, val description: String) {
    CONNECT("CONNECT", "Abre un túnel HTTP CONNECT persistente"),
    GET("GET", "Petición GET con conexión persistente"),
    POST("POST", "Petición POST vacía con conexión persistente"),
    HEAD("HEAD", "Petición HEAD ligera"),
    WEBSOCKET("WebSocket", "Upgrade HTTP/1.1 a WebSocket")
}

object PayloadEngine {
    private const val MAX_PAYLOAD_CHARS = 32 * 1024
    private const val MAX_ACTIONS = 64
    private const val MAX_DELAY_MS = 5_000L
    private const val MAX_TOTAL_DELAY_MS = 15_000L

    private val controlRegex = Regex("\\[(split|delay\\s*=\\s*\\d{1,6})]", RegexOption.IGNORE_CASE)
    private val tokenRegex = Regex("\\[[^]\\r\\n]{1,64}]", RegexOption.IGNORE_CASE)
    private val delayRegex = Regex("delay\\s*=\\s*(\\d+)", RegexOption.IGNORE_CASE)
    private val knownSimpleTokens = setOf(
        "host",
        "port",
        "host_port",
        "sni",
        "proxy",
        "proxy_port",
        "crlf",
        "lf",
        "cr",
        "random",
        "rotate",
        "split"
    )

    fun validate(raw: String): PayloadValidation {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        if (raw.isBlank()) errors += "El payload está vacío"
        if (raw.length > MAX_PAYLOAD_CHARS) errors += "El payload supera el límite de $MAX_PAYLOAD_CHARS caracteres"

        var totalDelay = 0L
        var actionCount = 1
        controlRegex.findAll(raw).forEach { match ->
            actionCount += 1
            val value = match.groupValues[1]
            if (value.startsWith("delay", ignoreCase = true)) {
                val delay = delayRegex.find(value)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: -1L
                when {
                    delay < 0L -> errors += "Retardo inválido: ${match.value}"
                    delay > MAX_DELAY_MS -> errors += "Cada retardo debe ser de $MAX_DELAY_MS ms o menos"
                    else -> totalDelay += delay
                }
            }
        }
        if (actionCount > MAX_ACTIONS) errors += "El payload contiene demasiados segmentos o retardos"
        if (totalDelay > MAX_TOTAL_DELAY_MS) errors += "La suma de retardos supera $MAX_TOTAL_DELAY_MS ms"

        val unknown = tokenRegex.findAll(raw)
            .map { it.value.removePrefix("[").removeSuffix("]").trim() }
            .filterNot { token ->
                val normalized = token.lowercase(Locale.US)
                normalized in knownSimpleTokens || delayRegex.matches(normalized)
            }
            .distinct()
            .toList()
        if (unknown.isNotEmpty()) {
            errors += "Variables no reconocidas: ${unknown.joinToString()}"
        }

        if (!raw.contains("[crlf]", ignoreCase = true) && !raw.contains("\r\n")) {
            warnings += "El payload no contiene CRLF explícito"
        }
        if (raw.contains("[split]", ignoreCase = true) && raw.startsWith("[split]", ignoreCase = true)) {
            warnings += "El primer segmento está vacío"
        }

        return PayloadValidation(errors.isEmpty(), errors, warnings)
    }

    fun compile(
        raw: String,
        context: PayloadContext,
        deterministicSeed: Long? = null
    ): PayloadPlan {
        val validation = validate(raw)
        require(validation.isValid) { validation.errors.joinToString(" · ") }
        require(context.host.isNotBlank()) { "El host del payload es obligatorio" }
        require(context.port in 1..65535) { "El puerto del payload es inválido" }

        val random = Random(deterministicSeed ?: SecureRandom().nextLong())
        val actions = mutableListOf<PayloadAction>()
        var cursor = 0
        controlRegex.findAll(raw).forEach { match ->
            val text = raw.substring(cursor, match.range.first)
            addSend(actions, expand(text, context, random))
            val control = match.groupValues[1]
            if (control.startsWith("delay", ignoreCase = true)) {
                val delay = delayRegex.find(control)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
                if (delay > 0L) actions += PayloadAction.Delay(delay.coerceAtMost(MAX_DELAY_MS))
            }
            cursor = match.range.last + 1
        }
        addSend(actions, expand(raw.substring(cursor), context, random))

        require(actions.size <= MAX_ACTIONS) { "El plan de payload excede el límite de acciones" }
        val totalDelay = actions.filterIsInstance<PayloadAction.Delay>().sumOf(PayloadAction.Delay::millis)
        require(totalDelay <= MAX_TOTAL_DELAY_MS) { "La suma de retardos excede el límite permitido" }
        val rendered = actions.filterIsInstance<PayloadAction.Send>().joinToString("") { it.text }
        require(rendered.isNotBlank()) { "El payload renderizado está vacío" }

        return PayloadPlan(
            actions = actions,
            rendered = rendered,
            visiblePreview = toVisiblePreview(rendered),
            totalDelayMs = totalDelay,
            segmentCount = actions.count { it is PayloadAction.Send }
        )
    }

    fun template(template: PayloadTemplate): String = when (template) {
        PayloadTemplate.CONNECT -> "CONNECT [host_port] HTTP/1.1[crlf]Host: [host_port][crlf]Proxy-Connection: Keep-Alive[crlf]Connection: Keep-Alive[crlf][crlf]"
        PayloadTemplate.GET -> "GET / HTTP/1.1[crlf]Host: [host][crlf]Connection: Keep-Alive[crlf]User-Agent: Mozilla/5.0[crlf][crlf]"
        PayloadTemplate.POST -> "POST / HTTP/1.1[crlf]Host: [host][crlf]Content-Length: 0[crlf]Connection: Keep-Alive[crlf][crlf]"
        PayloadTemplate.HEAD -> "HEAD / HTTP/1.1[crlf]Host: [host][crlf]Connection: Keep-Alive[crlf][crlf]"
        PayloadTemplate.WEBSOCKET -> "GET / HTTP/1.1[crlf]Host: [sni][crlf]Upgrade: websocket[crlf]Connection: Upgrade[crlf]Sec-WebSocket-Version: 13[crlf]Sec-WebSocket-Key: [random][crlf][crlf]"
    }

    fun toVisiblePreview(value: String): String = value
        .replace("\r\n", "␍␊\n")
        .replace("\r", "␍")
        .replace("\n", "␊\n")

    private fun addSend(actions: MutableList<PayloadAction>, value: String) {
        if (value.isNotEmpty()) actions += PayloadAction.Send(value)
    }

    private fun expand(raw: String, context: PayloadContext, random: Random): String {
        val host = context.host.trim()
        val port = context.port.coerceIn(1, 65535)
        val sni = context.sni.trim().ifBlank { host }
        val proxy = context.proxyHost.trim()
        val proxyPort = context.proxyPort.takeIf { it in 1..65535 }?.toString().orEmpty()
        val rotationValues = listOf(sni, host, proxy).filter(String::isNotBlank).distinct()
        val rotation = rotationValues.getOrElse(random.nextInt(rotationValues.size.coerceAtLeast(1))) { host }
        val randomToken = buildString {
            repeat(16) { append(RANDOM_ALPHABET[random.nextInt(RANDOM_ALPHABET.length)]) }
        }

        return raw
            .replace("[host_port]", "$host:$port", ignoreCase = true)
            .replace("[host]", host, ignoreCase = true)
            .replace("[port]", port.toString(), ignoreCase = true)
            .replace("[sni]", sni, ignoreCase = true)
            .replace("[proxy]", proxy, ignoreCase = true)
            .replace("[proxy_port]", proxyPort, ignoreCase = true)
            .replace("[random]", randomToken, ignoreCase = true)
            .replace("[rotate]", rotation, ignoreCase = true)
            .replace("[crlf]", "\r\n", ignoreCase = true)
            .replace("[lf]", "\n", ignoreCase = true)
            .replace("[cr]", "\r", ignoreCase = true)
    }

    private const val RANDOM_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
}