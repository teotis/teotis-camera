package com.opencamera.core.media

data class ReversibleWatermarkArchiveManifest(
    val schema: String = "org.opencamera.reversible-watermark",
    val version: Int = 1,
    val container: String = "jpeg-app15-ocwm",
    val payloadKind: String = "embedded-original-jpeg",
    val payloadMimeType: String = "image/jpeg",
    val payloadCompression: String = "none",
    val pipelineStage: String = "after-upstream-postprocessors-before-watermark",
    val watermarkTemplateId: String,
    val visibleImageSha256: String,
    val payloadSha256: String,
    val payloadLength: Long,
    val originalWidth: Int = 0,
    val originalHeight: Int = 0
) {
    fun toJson(): String = buildString {
        append('{')
        appendStringKey("schema", schema)
        append(',')
        appendIntKey("version", version)
        append(',')
        appendStringKey("container", container)
        append(',')
        appendStringKey("payloadKind", payloadKind)
        append(',')
        appendStringKey("payloadMimeType", payloadMimeType)
        append(',')
        appendStringKey("payloadCompression", payloadCompression)
        append(',')
        appendStringKey("pipelineStage", pipelineStage)
        append(',')
        appendStringKey("watermarkTemplateId", watermarkTemplateId)
        append(',')
        appendStringKey("visibleImageSha256", visibleImageSha256)
        append(',')
        appendStringKey("payloadSha256", payloadSha256)
        append(',')
        appendLongKey("payloadLength", payloadLength)
        append(',')
        appendIntKey("originalWidth", originalWidth)
        append(',')
        appendIntKey("originalHeight", originalHeight)
        append('}')
    }

    companion object {
        fun fromJson(json: String): ReversibleWatermarkArchiveManifest {
            val map = parseJsonToMap(json)
            val schema = map.requireString("schema")
            require(schema == "org.opencamera.reversible-watermark") { "unsupported schema: $schema" }
            val version = map.requireInt("version")
            require(version == 1) { "unsupported version: $version" }
            val container = map.requireString("container")
            require(container == "jpeg-app15-ocwm") { "unsupported container: $container" }
            val payloadLength = map.requireLong("payloadLength")
            require(payloadLength >= 0) { "negative payloadLength: $payloadLength" }
            return ReversibleWatermarkArchiveManifest(
                schema = schema,
                version = version,
                container = container,
                payloadKind = map.requireString("payloadKind"),
                payloadMimeType = map.requireString("payloadMimeType"),
                payloadCompression = map.requireString("payloadCompression"),
                pipelineStage = map.requireString("pipelineStage"),
                watermarkTemplateId = map.requireString("watermarkTemplateId"),
                visibleImageSha256 = map.requireString("visibleImageSha256"),
                payloadSha256 = map.requireString("payloadSha256"),
                payloadLength = payloadLength,
                originalWidth = map.getOrDefault("originalWidth", "0").toIntOrNull() ?: 0,
                originalHeight = map.getOrDefault("originalHeight", "0").toIntOrNull() ?: 0
            )
        }

        private fun Map<String, String>.requireString(key: String): String =
            this[key] ?: throw IllegalArgumentException("missing required key: $key")

        private fun Map<String, String>.requireInt(key: String): Int =
            (this[key] ?: throw IllegalArgumentException("missing required key: $key")).toIntOrNull()
                ?: throw IllegalArgumentException("invalid int for key: $key")

        private fun Map<String, String>.requireLong(key: String): Long =
            (this[key] ?: throw IllegalArgumentException("missing required key: $key")).toLongOrNull()
                ?: throw IllegalArgumentException("invalid long for key: $key")

        internal fun parseJsonToMap(json: String): Map<String, String> {
            val result = mutableMapOf<String, String>()
            var i = 0
            val s = json.trim()
            require(s.startsWith('{') && s.endsWith('}')) { "invalid JSON object" }
            i = 1
            while (i < s.length - 1) {
                skipWhitespace(s, i).also { i = it }
                if (i >= s.length - 1 || s[i] == '}') break
                val key = parseJsonString(s, i)
                i = key.second
                skipWhitespace(s, i).also { i = it }
                require(i < s.length && s[i] == ':') { "expected ':' at $i" }
                i++
                skipWhitespace(s, i).also { i = it }
                val value = parseJsonValue(s, i)
                result[key.first] = value.first
                i = value.second
                skipWhitespace(s, i).also { i = it }
                if (i < s.length - 1 && s[i] == ',') i++
            }
            return result
        }

        private fun skipWhitespace(s: String, start: Int): Int {
            var i = start
            while (i < s.length && s[i] in " \t\n\r") i++
            return i
        }

        private fun parseJsonString(s: String, start: Int): Pair<String, Int> {
            require(start < s.length && s[start] == '"') { "expected '\"' at $start" }
            val sb = StringBuilder()
            var i = start + 1
            while (i < s.length && s[i] != '"') {
                if (s[i] == '\\') {
                    i++
                    require(i < s.length) { "unexpected end of escape" }
                    when (s[i]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('')
                        'u' -> {
                            require(i + 4 < s.length) { "invalid unicode escape" }
                            val hex = s.substring(i + 1, i + 5)
                            sb.append(hex.toInt(16).toChar())
                            i += 4
                        }
                        else -> throw IllegalArgumentException("invalid escape: \\${s[i]}")
                    }
                } else {
                    sb.append(s[i])
                }
                i++
            }
            require(i < s.length && s[i] == '"') { "unterminated string" }
            return Pair(sb.toString(), i + 1)
        }

        private fun parseJsonValue(s: String, start: Int): Pair<String, Int> {
            if (s[start] == '"') return parseJsonString(s, start)
            val sb = StringBuilder()
            var i = start
            while (i < s.length && s[i] !in ",}" && s[i] != '"') {
                sb.append(s[i])
                i++
            }
            return Pair(sb.toString().trim(), i)
        }
    }
}

private fun StringBuilder.appendJsonString(value: String) {
    append('"')
    for (c in value) {
        when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\b' -> append("\\b")
            '' -> append("\\f")
            else -> append(c)
        }
    }
    append('"')
}

private fun StringBuilder.appendStringKey(key: String, value: String) {
    appendJsonString(key)
    append(':')
    appendJsonString(value)
}

private fun StringBuilder.appendIntKey(key: String, value: Int) {
    appendJsonString(key)
    append(':')
    append(value)
}

private fun StringBuilder.appendLongKey(key: String, value: Long) {
    appendJsonString(key)
    append(':')
    append(value)
}

fun sha256Hex(data: ByteArray): String {
    val md = java.security.MessageDigest.getInstance("SHA-256")
    val digest = md.digest(data)
    return digest.joinToString("") { "%02x".format(it) }
}
