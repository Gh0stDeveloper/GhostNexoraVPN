package com.ghostnexora.vpn.security

/**
 * Sanitiza notas HTML creadas por administradores de servidores.
 *
 * La política permite contenido de presentación amplio y CSS local, pero
 * elimina ejecución, formularios, frames, eventos y cargas de red. La WebView
 * de notas aplica una segunda barrera con JavaScript y red deshabilitados.
 */
object HtmlNoteSanitizer {
    private const val MAX_INPUT_CHARS = 64 * 1024
    private const val MAX_OUTPUT_CHARS = 72 * 1024
    private const val MAX_STYLE_SHEET_CHARS = 24 * 1024
    private const val MAX_INLINE_STYLE_CHARS = 8 * 1024
    private const val MAX_EMBEDDED_IMAGE_CHARS = 48 * 1024

    private val allowedTags = setOf(
        "html", "head", "body", "style",
        "main", "nav", "div", "span", "p", "br", "hr", "section", "article",
        "header", "footer", "aside", "address", "details", "summary",
        "h1", "h2", "h3", "h4", "h5", "h6",
        "strong", "b", "em", "i", "u", "s", "small", "big", "mark",
        "sub", "sup", "font", "marquee",
        "blockquote", "pre", "code", "kbd", "samp", "var", "center",
        "ul", "ol", "li", "dl", "dt", "dd",
        "figure", "figcaption", "time",
        "table", "caption", "colgroup", "col", "thead", "tbody", "tfoot",
        "tr", "th", "td",
        "a", "img"
    )
    private val globalAttributes = setOf(
        "class", "id", "style", "title", "align", "dir", "lang"
    )
    private val tableAttributes = setOf(
        "colspan", "rowspan", "scope", "width", "height", "bgcolor",
        "border", "cellpadding", "cellspacing", "valign"
    )
    private val imageAttributes = setOf("src", "alt", "width", "height")
    private val fontAttributes = setOf("color", "face", "size")
    private val marqueeAttributes = setOf(
        "direction", "behavior", "scrollamount", "scrolldelay", "loop",
        "width", "height", "bgcolor"
    )

    /*
     * Android uses ICU's regular-expression engine. It rejects some malformed
     * brace patterns that the desktop JVM accepted. Every static expression is
     * compiled defensively so a future pattern can never crash the application
     * with ExceptionInInitializerError.
     */
    private fun compileRegex(pattern: String): Regex =
        runCatching { Regex(pattern) }.getOrElse { Regex("(?!x)x") }

    private val blockElement = compileRegex(
        """(?is)<(script|iframe|object|embed|form|input|button|textarea|select|option|video|audio|canvas|svg|math|applet|frameset|frame|template)\b[^>]*>.*?</\1\s*>"""
    )
    private val forbiddenSingleTag = compileRegex(
        """(?is)</?(script|iframe|object|embed|form|input|button|textarea|select|option|video|audio|canvas|svg|math|applet|frameset|frame|template|meta|link|base)\b[^>]*>"""
    )
    private val tagPattern = compileRegex(
        """(?is)<\s*(/?)\s*([a-zA-Z][a-zA-Z0-9]*)\b([^>]*)>"""
    )
    private val attributePattern = compileRegex(
        """([A-Za-z_:][A-Za-z0-9_:.-]*)\s*(?:=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+)))?"""
    )
    private val htmlCommentPattern = compileRegex("""(?is)<!--.*?-->""")
    private val declarationPattern = compileRegex("""(?is)<![^>]*>""")
    private val styleBlockPattern = compileRegex(
        """(?is)<style\b[^>]*>(.*?)</style\s*>"""
    )
    private val dimensionPattern = compileRegex(
        """(?i)^(?:auto|0|[0-9]{1,4}(?:\.[0-9]{1,2})?(?:px|%|em|rem|vw|vh)?)$"""
    )

    private val forbiddenAtRules = setOf(
        "import", "font-face", "namespace", "charset", "page", "document"
    )
    private val safeGroupAtRules = setOf(
        "media", "supports", "keyframes", "-webkit-keyframes", "container", "layer"
    )
    private val blockedCssProperties = setOf(
        "z-index", "filter", "backdrop-filter", "content", "behavior", "-moz-binding"
    )

    fun sanitize(raw: String): String {
        if (raw.isBlank()) return ""
        return runCatching { sanitizeInternal(raw) }
            .getOrElse { plainTextFallback(raw) }
    }

    fun containsActiveContent(raw: String): Boolean {
        val lowered = raw.lowercase()
        return listOf(
            "<script", "<iframe", "<object", "<embed", "<form",
            "javascript:", "vbscript:", "data:text/html", "onerror=", "onclick=",
            "@import", "@font-face", "url("
        ).any(lowered::contains)
    }

    private fun sanitizeInternal(raw: String): String {
        var value = raw.take(MAX_INPUT_CHARS)
            .replace("\u0000", "")
            .replace(htmlCommentPattern, "")
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

    private fun sanitizeTag(match: MatchResult): String {
        val closing = match.groupValues[1].isNotEmpty()
        val tag = match.groupValues[2].lowercase()
        if (tag !in allowedTags) return ""
        if (closing) return if (tag == "img") "" else "</$tag>"
        if (tag == "style") return "<style>"

        val allowed = buildSet {
            addAll(globalAttributes)
            when (tag) {
                "a" -> add("href")
                "img" -> addAll(imageAttributes)
                "font" -> addAll(fontAttributes)
                "marquee" -> addAll(marqueeAttributes)
                "table", "col", "th", "td" -> addAll(tableAttributes)
            }
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
                val safeValue = sanitizeAttribute(tag, name, rawValue)
                if (safeValue.isBlank() && name !in setOf("style", "alt")) null
                else "$name=\"${escapeAttribute(safeValue)}\""
            }
            .toMutableList()

        when (tag) {
            "a" -> {
                attributes += "target=\"_blank\""
                attributes += "rel=\"noopener noreferrer nofollow\""
            }
            "img" -> {
                attributes += "loading=\"lazy\""
                attributes += "referrerpolicy=\"no-referrer\""
            }
        }
        return buildString {
            append('<').append(tag)
            if (attributes.isNotEmpty()) append(' ').append(attributes.joinToString(" "))
            append('>')
        }
    }

    private fun sanitizeAttribute(tag: String, name: String, value: String): String = when (name) {
        "href" -> sanitizeHref(value)
        "src" -> if (tag == "img") sanitizeEmbeddedImage(value) else ""
        "style" -> sanitizeInlineStyle(value)
        "colspan", "rowspan" -> value.toIntOrNull()?.coerceIn(1, 20)?.toString().orEmpty()
        "border", "cellpadding", "cellspacing" ->
            value.toIntOrNull()?.coerceIn(0, 40)?.toString().orEmpty()
        "width", "height" -> sanitizeDimension(value)
        "dir" -> value.lowercase().takeIf { it in setOf("ltr", "rtl", "auto") }.orEmpty()
        "align" -> value.lowercase().takeIf {
            it in setOf("left", "right", "center", "justify", "start", "end")
        }.orEmpty()
        "valign" -> value.lowercase().takeIf {
            it in setOf("top", "middle", "bottom", "baseline")
        }.orEmpty()
        "color", "bgcolor" -> sanitizeColor(value)
        "face" -> value.take(120).filterNot { it in "<>;{}" }
        "size" -> sanitizeLegacyFontSize(value)
        "direction" -> value.lowercase().takeIf {
            it in setOf("left", "right", "up", "down")
        }.orEmpty()
        "behavior" -> value.lowercase().takeIf {
            it in setOf("scroll", "slide", "alternate")
        }.orEmpty()
        "scrollamount" -> value.toIntOrNull()?.coerceIn(1, 40)?.toString().orEmpty()
        "scrolldelay" -> value.toIntOrNull()?.coerceIn(30, 5_000)?.toString().orEmpty()
        "loop" -> value.toIntOrNull()?.coerceIn(1, 100)?.toString().orEmpty()
        else -> value.take(2_048)
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

    private fun sanitizeEmbeddedImage(value: String): String {
        val trimmed = value.trim().take(MAX_EMBEDDED_IMAGE_CHARS)
        val lowered = trimmed.lowercase()
        val allowed = listOf(
            "data:image/png;base64,",
            "data:image/jpeg;base64,",
            "data:image/jpg;base64,",
            "data:image/gif;base64,",
            "data:image/webp;base64,"
        ).any(lowered::startsWith)
        return trimmed.takeIf {
            allowed && '<' !in it && '>' !in it && "javascript:" !in lowered
        }.orEmpty()
    }

    private fun sanitizeDimension(value: String): String =
        value.trim().take(24).takeIf(dimensionPattern::matches).orEmpty()

    private fun sanitizeColor(value: String): String {
        val trimmed = value.trim().take(64)
        val lowered = trimmed.lowercase()
        return trimmed.takeIf {
            it.none { char -> char in ";{}<>" } &&
                "url(" !in lowered &&
                "expression(" !in lowered &&
                "javascript:" !in lowered
        }.orEmpty()
    }

    private fun sanitizeLegacyFontSize(value: String): String {
        val trimmed = value.trim()
        val numeric = trimmed.removePrefix("+").removePrefix("-").toIntOrNull()
        return trimmed.takeIf { numeric in 1..7 }.orEmpty()
    }

    private fun sanitizeInlineStyle(value: String): String {
        val clean = replaceCssUrlFunctions(stripCssComments(value))
        return sanitizeDeclarationBlock(clean).take(MAX_INLINE_STYLE_CHARS)
    }

    private fun sanitizeStyleSheet(value: String): String {
        val clean = stripForbiddenAtRules(stripCssComments(value))
        return sanitizeRuleList(replaceCssUrlFunctions(clean)).take(MAX_STYLE_SHEET_CHARS)
    }

    private fun stripCssComments(value: String): String {
        val output = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            if (index + 1 < value.length && value[index] == '/' && value[index + 1] == '*') {
                val end = value.indexOf("*/", index + 2)
                index = if (end >= 0) end + 2 else value.length
            } else {
                output.append(value[index++])
            }
        }
        return output.toString()
    }

    private fun stripForbiddenAtRules(value: String): String {
        val output = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            if (value[index] == '@') {
                var nameEnd = index + 1
                while (nameEnd < value.length &&
                    (value[nameEnd].isLetterOrDigit() || value[nameEnd] == '-')
                ) {
                    nameEnd += 1
                }
                val name = value.substring(index + 1, nameEnd).lowercase()
                if (name in forbiddenAtRules) {
                    index = skipAtRule(value, nameEnd)
                    continue
                }
            }
            output.append(value[index++])
        }
        return output.toString()
    }

    private fun skipAtRule(value: String, fromIndex: Int): Int {
        var index = fromIndex
        var quote: Char? = null
        var escaped = false
        while (index < value.length) {
            val char = value[index]
            if (escaped) {
                escaped = false
            } else if (char == '\\') {
                escaped = true
            } else if (quote != null) {
                if (char == quote) quote = null
            } else when (char) {
                '\'', '"' -> quote = char
                ';' -> return index + 1
                '{' -> {
                    val close = findMatchingBrace(value, index)
                    return if (close >= 0) close + 1 else value.length
                }
            }
            index += 1
        }
        return value.length
    }

    private fun replaceCssUrlFunctions(value: String): String {
        val output = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            if (isUrlFunctionAt(value, index)) {
                var open = index + 3
                while (open < value.length && value[open].isWhitespace()) open += 1
                val end = findFunctionEnd(value, open)
                output.append("none")
                index = if (end >= 0) end + 1 else value.length
            } else {
                output.append(value[index++])
            }
        }
        return output.toString()
    }

    private fun isUrlFunctionAt(value: String, index: Int): Boolean {
        if (!value.regionMatches(index, "url", 0, 3, ignoreCase = true)) return false
        if (index > 0 && (value[index - 1].isLetterOrDigit() || value[index - 1] in "_-")) return false
        var cursor = index + 3
        while (cursor < value.length && value[cursor].isWhitespace()) cursor += 1
        return cursor < value.length && value[cursor] == '('
    }

    private fun findFunctionEnd(value: String, openIndex: Int): Int {
        if (openIndex !in value.indices || value[openIndex] != '(') return -1
        var depth = 0
        var quote: Char? = null
        var escaped = false
        for (index in openIndex until value.length) {
            val char = value[index]
            if (escaped) {
                escaped = false
                continue
            }
            if (char == '\\') {
                escaped = true
                continue
            }
            if (quote != null) {
                if (char == quote) quote = null
                continue
            }
            when (char) {
                '\'', '"' -> quote = char
                '(' -> depth += 1
                ')' -> {
                    depth -= 1
                    if (depth == 0) return index
                }
            }
        }
        return -1
    }

    private fun sanitizeRuleList(value: String): String {
        val output = StringBuilder(value.length)
        var cursor = 0
        while (cursor < value.length) {
            val open = findNextUnquoted(value, '{', cursor)
            if (open < 0) break
            val close = findMatchingBrace(value, open)
            if (close < 0) break
            val prelude = value.substring(cursor, open).trim().take(2_048)
            if (isSafeRulePrelude(prelude)) {
                val body = value.substring(open + 1, close)
                val atRule = prelude.trimStart().removePrefix("@").substringBefore(' ').lowercase()
                val sanitizedBody = if (prelude.startsWith("@") && atRule in safeGroupAtRules) {
                    sanitizeRuleList(body)
                } else {
                    sanitizeDeclarationBlock(body)
                }
                if (sanitizedBody.isNotBlank()) {
                    output.append(prelude).append('{').append(sanitizedBody).append('}')
                }
            }
            cursor = close + 1
        }
        return output.toString()
    }

    private fun isSafeRulePrelude(value: String): Boolean {
        if (value.isBlank() || value.any { it == '<' || it == '>' }) return false
        if (!value.startsWith("@")) return true
        val name = value.removePrefix("@").substringBefore(' ').substringBefore('{').lowercase()
        return name in safeGroupAtRules
    }

    private fun sanitizeDeclarationBlock(value: String): String =
        splitTopLevelDeclarations(value)
            .mapNotNull(::sanitizeDeclaration)
            .joinToString("; ")

    private fun sanitizeDeclaration(value: String): String? {
        val colon = findTopLevelColon(value)
        if (colon <= 0) return null
        val property = value.substring(0, colon).trim().lowercase()
        var cssValue = value.substring(colon + 1).trim().take(4_096)
        if (!isSafePropertyName(property) || cssValue.isBlank()) return null
        cssValue = cssValue.replace("<", "").replace(">", "")
        val lowered = cssValue.lowercase()
        if (property in blockedCssProperties) return null
        if (property == "position" && listOf("fixed", "sticky", "absolute").any(lowered::contains)) {
            return null
        }
        if (listOf(
                "expression(", "javascript:", "vbscript:", "-moz-binding", "behavior:",
                "@import", "url("
            ).any(lowered::contains)
        ) {
            return null
        }
        if ('{' in cssValue || '}' in cssValue) return null
        return "$property: $cssValue"
    }

    private fun isSafePropertyName(value: String): Boolean {
        if (value.isBlank() || value.length > 80) return false
        if (!(value.first().isLetter() || value.first() == '-')) return false
        return value.all { it.isLetterOrDigit() || it == '-' }
    }

    private fun splitTopLevelDeclarations(value: String): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        var parentheses = 0
        var quote: Char? = null
        var escaped = false
        for (index in value.indices) {
            val char = value[index]
            if (escaped) {
                escaped = false
                continue
            }
            if (char == '\\') {
                escaped = true
                continue
            }
            if (quote != null) {
                if (char == quote) quote = null
                continue
            }
            when (char) {
                '\'', '"' -> quote = char
                '(' -> parentheses += 1
                ')' -> if (parentheses > 0) parentheses -= 1
                ';' -> if (parentheses == 0) {
                    result += value.substring(start, index)
                    start = index + 1
                }
            }
        }
        if (start < value.length) result += value.substring(start)
        return result
    }

    private fun findTopLevelColon(value: String): Int {
        var parentheses = 0
        var quote: Char? = null
        var escaped = false
        for (index in value.indices) {
            val char = value[index]
            if (escaped) {
                escaped = false
                continue
            }
            if (char == '\\') {
                escaped = true
                continue
            }
            if (quote != null) {
                if (char == quote) quote = null
                continue
            }
            when (char) {
                '\'', '"' -> quote = char
                '(' -> parentheses += 1
                ')' -> if (parentheses > 0) parentheses -= 1
                ':' -> if (parentheses == 0) return index
            }
        }
        return -1
    }

    private fun findNextUnquoted(value: String, target: Char, fromIndex: Int): Int {
        var quote: Char? = null
        var escaped = false
        for (index in fromIndex until value.length) {
            val char = value[index]
            if (escaped) {
                escaped = false
                continue
            }
            if (char == '\\') {
                escaped = true
                continue
            }
            if (quote != null) {
                if (char == quote) quote = null
            } else if (char == '\'' || char == '"') {
                quote = char
            } else if (char == target) {
                return index
            }
        }
        return -1
    }

    private fun findMatchingBrace(value: String, openIndex: Int): Int {
        if (openIndex !in value.indices || value[openIndex] != '{') return -1
        var depth = 0
        var quote: Char? = null
        var escaped = false
        for (index in openIndex until value.length) {
            val char = value[index]
            if (escaped) {
                escaped = false
                continue
            }
            if (char == '\\') {
                escaped = true
                continue
            }
            if (quote != null) {
                if (char == quote) quote = null
                continue
            }
            when (char) {
                '\'', '"' -> quote = char
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) return index
                }
            }
        }
        return -1
    }

    private fun plainTextFallback(raw: String): String =
        "<pre>${escapeText(raw.take(MAX_INPUT_CHARS))}</pre>"

    private fun escapeAttribute(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    private fun escapeText(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
}
