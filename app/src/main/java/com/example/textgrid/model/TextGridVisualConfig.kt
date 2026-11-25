package com.example.textgrid.model

import java.util.Locale

enum class TextGridScaleMode(val id: String) {
    ELASTIC("ELASTIC"),
    LINEAR("LINEAR");

    companion object {
        fun fromId(id: String?): TextGridScaleMode =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: ELASTIC
    }
}

enum class TextGridWrapMode(val id: String) {
    AUTO("AUTO"),
    SINGLE("SINGLE");

    companion object {
        fun fromId(id: String?): TextGridWrapMode =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: AUTO
    }
}

enum class TextGridTickPrecision(val id: String, val maxDecimals: Int?) {
    P2("P2", 2),
    P3("P3", 3),
    P4("P4", 4),
    FULL("FULL", null),
    NONE("NONE", 0);

    companion object {
        fun fromId(id: String?): TextGridTickPrecision =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: P3
    }
}

enum class TextGridScoreTheme(val id: String) {
    FOLLOW_APP("FOLLOW_APP"),
    WARM_PAPER("WARM_PAPER"),
    ACADEMIC_BW("ACADEMIC_BW");

    companion object {
        fun fromId(id: String?): TextGridScoreTheme =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: FOLLOW_APP
    }
}

enum class TextGridOpenMode(val id: String) {
    VISUAL("VISUAL"),
    SOURCE("SOURCE");

    companion object {
        fun fromId(id: String?): TextGridOpenMode {
            val normalized = id?.trim()?.uppercase(Locale.US) ?: return VISUAL
            return when (normalized) {
                "SOURCE", "SOURCE_CODE" -> SOURCE
                else -> VISUAL
            }
        }
    }
}

/**
 * Pure read-only Dual-Scale TextGrid viewing preferences.
 * Persisted independently in SharedPreferences (`textgrid_visual_config_v1`) using zero-dependency JSON.
 * Backward-compatible with legacy JSON keys (unknown edit/spectrogram fields are ignored gracefully).
 */
data class TextGridVisualConfig(
    val defaultOpenMode: TextGridOpenMode = TextGridOpenMode.VISUAL,
    val scaleMode: TextGridScaleMode = TextGridScaleMode.ELASTIC,
    val wrapMode: TextGridWrapMode = TextGridWrapMode.AUTO,
    val tickPrecision: TextGridTickPrecision = TextGridTickPrecision.P3,
    val showRichWhitespace: Boolean = true,
    val scoreTheme: TextGridScoreTheme = TextGridScoreTheme.FOLLOW_APP
) {
    fun toJsonString(): String {
        fun esc(s: String): String = buildString(s.length + 4) {
            append('"')
            for (ch in s) {
                when (ch) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
            append('"')
        }
        return buildString(256) {
            append('{')
            append("\"defaultOpenMode\":").append(esc(defaultOpenMode.id)).append(',')
            append("\"scaleMode\":").append(esc(scaleMode.id)).append(',')
            append("\"wrapMode\":").append(esc(wrapMode.id)).append(',')
            append("\"tickPrecision\":").append(esc(tickPrecision.id)).append(',')
            append("\"showRichWhitespace\":").append(showRichWhitespace).append(',')
            append("\"scoreTheme\":").append(esc(scoreTheme.id))
            append('}')
        }
    }

    companion object {
        const val PREFS_KEY = "textgrid_visual_config_v1"

        fun fromJsonString(raw: String?): TextGridVisualConfig {
            if (raw.isNullOrBlank()) return TextGridVisualConfig()
            return try {
                val map = parseFlatJsonMap(raw)
                val defaults = TextGridVisualConfig()
                fun bool(key: String, def: Boolean): Boolean =
                    map[key]?.lowercase(Locale.US)?.toBooleanStrictOrNull() ?: def

                TextGridVisualConfig(
                    defaultOpenMode = TextGridOpenMode.fromId(map["defaultOpenMode"]),
                    scaleMode = TextGridScaleMode.fromId(map["scaleMode"]),
                    wrapMode = TextGridWrapMode.fromId(map["wrapMode"]),
                    tickPrecision = TextGridTickPrecision.fromId(map["tickPrecision"]),
                    showRichWhitespace = bool("showRichWhitespace", defaults.showRichWhitespace),
                    scoreTheme = TextGridScoreTheme.fromId(map["scoreTheme"])
                )
            } catch (_: Exception) {
                TextGridVisualConfig()
            }
        }

        private fun parseFlatJsonMap(raw: String): Map<String, String> {
            val map = LinkedHashMap<String, String>()
            var i = 0
            val n = raw.length
            fun skipWs() {
                while (i < n && raw[i].isWhitespace()) i++
            }
            fun readQuoted(): String {
                if (i >= n || raw[i] != '"') return ""
                i++
                val sb = StringBuilder()
                while (i < n) {
                    val ch = raw[i++]
                    if (ch == '"') break
                    if (ch == '\\' && i < n) {
                        when (val esc = raw[i++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> if (i + 4 <= n) {
                                raw.substring(i, i + 4).toIntOrNull(16)?.let { sb.append(it.toChar()) }
                                i += 4
                            }
                            else -> sb.append(esc)
                        }
                    } else {
                        sb.append(ch)
                    }
                }
                return sb.toString()
            }
            skipWs()
            if (i < n && raw[i] == '{') i++
            while (i < n) {
                skipWs()
                if (i >= n || raw[i] == '}') break
                if (raw[i] == ',') {
                    i++
                    continue
                }
                if (raw[i] != '"') {
                    i++
                    continue
                }
                val key = readQuoted()
                skipWs()
                if (i < n && raw[i] == ':') i++
                skipWs()
                if (i >= n) break
                val value = if (raw[i] == '"') {
                    readQuoted()
                } else {
                    val start = i
                    while (i < n && raw[i] != ',' && raw[i] != '}' && !raw[i].isWhitespace()) i++
                    raw.substring(start, i)
                }
                map[key] = value
            }
            return map
        }
    }
}
