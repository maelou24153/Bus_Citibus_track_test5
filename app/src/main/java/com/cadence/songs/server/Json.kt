package com.cadence.songs.server

/** Petit lecteur / écrivain JSON sans dépendance (utilisé par le serveur Wi-Fi et l'import web). */
object Json {
    class JsonException(message: String) : Exception(message)

    /** Renvoie Map<String, Any?>, List<Any?>, String, Double, Boolean ou null. */
    fun parse(text: String): Any? {
        val p = Parser(text)
        p.skipWs()
        val v = p.value()
        p.skipWs()
        if (p.pos != text.length) throw JsonException("Texte en trop à la position ${p.pos}")
        return v
    }

    @Suppress("UNCHECKED_CAST")
    fun parseObject(text: String): Map<String, Any?> =
        parse(text) as? Map<String, Any?> ?: throw JsonException("Objet JSON attendu")

    fun stringify(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

    fun quote(s: String): String = StringBuilder().also { writeString(it, s) }.toString()

    private fun write(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is String -> writeString(sb, v)
            is Boolean -> sb.append(v)
            is Int, is Long -> sb.append(v.toString())
            is Number -> {
                val d = v.toDouble()
                if (d == Math.floor(d) && Math.abs(d) < 1e15) sb.append(d.toLong()) else sb.append(d)
            }
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, value) in v) {
                    if (!first) sb.append(',')
                    first = false
                    writeString(sb, k.toString())
                    sb.append(':')
                    write(sb, value)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (item in v) {
                    if (!first) sb.append(',')
                    first = false
                    write(sb, item)
                }
                sb.append(']')
            }
            else -> writeString(sb, v.toString())
        }
    }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }

    private class Parser(val s: String) {
        var pos = 0

        fun skipWs() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        fun value(): Any? {
            if (pos >= s.length) throw JsonException("Fin inattendue")
            return when (val c = s[pos]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) num() else throw JsonException("Caractère inattendu '$c' à $pos")
            }
        }

        private fun literal(word: String, v: Any?): Any? {
            if (!s.startsWith(word, pos)) throw JsonException("Valeur invalide à $pos")
            pos += word.length
            return v
        }

        private fun num(): Double {
            val start = pos
            if (s[pos] == '-') pos++
            while (pos < s.length && (s[pos].isDigit() || s[pos] in ".eE+-")) pos++
            return s.substring(start, pos).toDoubleOrNull() ?: throw JsonException("Nombre invalide à $start")
        }

        private fun str(): String {
            pos++ // "
            val sb = StringBuilder()
            while (true) {
                if (pos >= s.length) throw JsonException("Chaîne non terminée")
                val c = s[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= s.length) throw JsonException("Échappement incomplet")
                        when (val e = s[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > s.length) throw JsonException("Échappement \\u incomplet")
                                sb.append(s.substring(pos, pos + 4).toInt(16).toChar())
                                pos += 4
                            }
                            else -> throw JsonException("Échappement inconnu \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun arr(): List<Any?> {
            pos++
            val out = mutableListOf<Any?>()
            skipWs()
            if (pos < s.length && s[pos] == ']') { pos++; return out }
            while (true) {
                skipWs()
                out.add(value())
                skipWs()
                if (pos >= s.length) throw JsonException("Tableau non terminé")
                when (s[pos++]) {
                    ',' -> continue
                    ']' -> return out
                    else -> throw JsonException("Virgule ou ] attendu à ${pos - 1}")
                }
            }
        }

        private fun obj(): Map<String, Any?> {
            pos++
            val out = LinkedHashMap<String, Any?>()
            skipWs()
            if (pos < s.length && s[pos] == '}') { pos++; return out }
            while (true) {
                skipWs()
                if (pos >= s.length || s[pos] != '"') throw JsonException("Clé attendue à $pos")
                val k = str()
                skipWs()
                if (pos >= s.length || s[pos] != ':') throw JsonException(": attendu à $pos")
                pos++
                skipWs()
                out[k] = value()
                skipWs()
                if (pos >= s.length) throw JsonException("Objet non terminé")
                when (s[pos++]) {
                    ',' -> continue
                    '}' -> return out
                    else -> throw JsonException("Virgule ou } attendu à ${pos - 1}")
                }
            }
        }
    }
}
