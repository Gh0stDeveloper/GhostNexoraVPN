package com.ghostnexora.vpn.security

/**
 * Saneador conservador para notas de creadores.
 *
 * Permite HTML de presentación, tablas, enlaces de contacto y CSS inline o en
 * `<style>`. Elimina ejecución, frames, formularios, recursos remotos, eventos
 * y protocolos activos. La vista WebView aplica una segunda barrera.
 */
object HtmlNoteSanitizer {
    private const val MAX_INPUT_CHARS = 64 * 1024
    private const val MAX_OUTPUT_CHARS = 72 * 1024

    private val allowedTags = setOf(
        "html", "head", "body", "style",
        "div", "span", "p", "br", "hr", "section", "article", "header", "footer",
        "h1", "h2", "h3", "h4", "h5", "h6",
        "strong", "b", "em", "i", "u", "s", "small", "big",
        "blockquote", "pre", "code", "center",
        "ul", "ol", "li",
        "table", "thead", "tbody", "tfoot", "tr", "th", "td",
        "a"
    )
    private val globalAttributes = setOf(
        "class", "id", "style", "title", "align", "dir", "lang"
    )
    private val tableAttributes = setOf("colspan", "rowspan", "scope")
    private val blockElement = Regex(
        """(?is)<(script|iframe|object|embed|form|input|button|textarea|select|option|video|audio|canvas|svg|math|applet|frameset|frame|template)\b[^>]*>.*?</\1\s*>"""
    )
    private val forbiddenSingleTag = Regex(
        """(?is)</?(script|iframe|object|embed|form|input|button|textarea|select|option|video|audio|canvas|svg|math|applet|frameset|frame|template|meta|link|base)\b[^>]*>"""
    )
    private val tagPattern = Regex("""(?is)<\s*(/?)\s*([a-zA-Z][a-zA-Z0-9]*)\b([^>]*)>""")
    private val attributePattern = Regex(
        """([A-Za-z_:][A-Za-z0-9_:.-]*)\s*(?:=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+)))?"""
    )
    private val commentPattern = Regex("""(?is)<!--.*?-->""")
    private val declarationPattern = Regex("""(?is)<![^>]*>""")
    private val styleBlockPattern = Regex("""(?is)<style\b[^>]*>(.*?)</style\s*>""")
    private val cssImport = Regex("""(?is)@(?:import|font-face|namespace|charset|page|keyframes)\b[^;{]*(?:;|\{.*?})""")
    private val cssUrl = Regex("""(?is)url\s*\([^)]*\)""")
    private val cssUnsafeDeclaration = Regex(
        """(?is)(?:expression\s*\(|javascript\s*:|vbscript\s*:|-moz-binding|behavior\s*:)[^;}]*"""
    )
    private val dangerousStyleProperty = Regex(
        """(?is)(?:position|z-index|filter|backdrop-filter|content)\s*:[^;}]*"""
    )

    fun sanitize(raw: String): String {
        if (raw.isBlank()) return ""
        var value = raw.take(MAX_INPUT_CHARS)
            .replace("\u0000", "")
            .replace(commentPattern, "")
            .replace(declarationPattern, "")

        repeat(3) {
            value = value.replace(blockElement, "")
        }
        value = value.replace(forbiddenSingleTag, "")
        value = value.replace(styleBlockPattern) { match ->
            "<style>${sanitizeStyleSheet(match.groupValues[1])}</style>"
        }
        value = tagPattern.replace(value, ::sanitizeTag)
        return value.take(MAX_OUTPUT_CHARS).trim()
    }

    fun containsActiveContent(raw: String): Boolean {
        val lowered = raw.lowercase()
        return listOf(
            "<script", "<iframe", "<object", "<embed", "<form",
            "javascript:", "vbscript:", "data:text/html", "onerror=", "onclick=",
            "@import", "url("
        ).any(lowered::contains)
    }

    private fun sanitizeTag(match: MatchResult): String {
        val closing = match.groupValues[1].isNotEmpty()
        val tag = match.groupValues[2].lowercase()
        if (tag !in allowedTags) return ""
        if (closing) return "</$tag>"
        if (tag == "style") return "<style>"

        val allowed = buildSet {
            addAll(globalAttributes)
            if (tag == "a") add("href")
            if (tag in setOf("td", "th")) addAll(tableAttributes)
        }
        val attributes = attributePattern.findAll(match.groupValues[3])
            .mapNotNull { attribute ->
                val name = attribute.groupValues[1].lowercase()
                if (name.startsWith("on") || name !in allowed) return@mapNotNull null
                val rawValue = sequenceOf(
                    attribute.groupValues[2],
                    attribute.groupValues[3],
                    attribute.groupValues[4]
                ).firstOrNull(String::isNotEmpty).orEmpty()
                val safeValue = when (name) {
                    "href" -> sanitizeHref(rawValue)
                    "style" -> sanitizeInlineStyle(rawValue)
                    "colspan", "rowspan" -> rawValue.toIntOrNull()
                        ?.coerceIn(1, 20)
                        ?.toString()
                        .orEmpty()
                    "dir" -> rawValue.lowercase().takeIf { it in setOf("ltr", "rtl", "auto") }.orEmpty()
                    else -> rawValue.take(2_048)
                }
                if (safeValue.isBlank() && name !in setOf("style")) null
                else "$name=\"${escapeAttribute(safeValue)}\""
            }
            .toMutableList()

        if (tag == "a") {
            attributes += "target=\"_blank\""
            attributes += "rel=\"noopener noreferrer nofollow\""
        }
        return buildString {
            append('<').append(tag)
            if (attributes.isNotEmpty()) append(' ').append(attributes.joinToString(" "))
            append('>')
        }
    }

    private fun sanitizeHref(value: String): String {
        val trimmed = value.trim().take(2_048)
        val scheme = trimmed.substringBefore(':', "").lowercase()
        return when {
            trimmed.startsWith("#") -> trimmed
            scheme in setOf("https", "http", "mailto", "tel") -> trimmed
            else -> "#"
        }
    }

    private fun sanitizeInlineStyle(value: String): String =
        sanitizeCss(value)
            .split(';')
            .map(String::trim)
            .filter(String::isNotBlank)
            .joinToString("; ")
            .take(8_192)

    private fun sanitizeStyleSheet(value: String): String =
        sanitizeCss(value)
            .replace(Regex("""(?is)@[^;{]+(?:;|\{.*?})"""), "")
            .take(24 * 1024)

    private fun sanitizeCss(value: String): String =
        value
            .replace(Regex("""(?is)/\*.*?\*/"""), "")
            .replace(cssImport, "")
            .replace(cssUrl, "none")
            .replace(cssUnsafeDeclaration, "")
            .replace(dangerousStyleProperty, "")
            .replace(Regex("""(?is)[<>]"""), "")

    private fun escapeAttribute(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
}
