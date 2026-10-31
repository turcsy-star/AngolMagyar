package hu.angolmagyar.fordito

/**
 * Kicsi, függőség nélküli JSON-olvasó/író (a hálózati válaszokhoz és a kérések összeállításához;
 * így JVM-en is tesztelhető). Értékek: Map<String, Any?>, List<Any?>, String, Double, Boolean, null.
 */
object MiniJson {

    fun quote(s: String): String {
        val sb = StringBuilder(s.length + 2)
        sb.append('"')
        for (ch in s) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (ch < ' ') sb.append(String.format("\\u%04x", ch.code)) else sb.append(ch)
            }
        }
        sb.append('"')
        return sb.toString()
    }

    /** @throws IllegalArgumentException hibás JSON esetén */
    fun parse(text: String): Any? {
        val p = Parser(text)
        p.ws()
        val v = p.value()
        p.ws()
        if (p.pos != text.length) throw IllegalArgumentException("felesleges szöveg a JSON után (${p.pos}. karakter)")
        return v
    }

    /** Az első kiegyensúlyozott [...] rész kivágása (a modell néha körbeírja szöveggel / kódblokkal). */
    fun extractArray(text: String): String? {
        val start = text.indexOf('[')
        if (start < 0) return null
        var depth = 0
        var inStr = false
        var esc = false
        for (i in start until text.length) {
            val c = text[i]
            if (inStr) {
                if (esc) esc = false else if (c == '\\') esc = true else if (c == '"') inStr = false
            } else when (c) {
                '"' -> inStr = true
                '[' -> depth++
                ']' -> { depth--; if (depth == 0) return text.substring(start, i + 1) }
            }
        }
        return null
    }

    private class Parser(val s: String) {
        var pos = 0

        fun ws() { while (pos < s.length && s[pos].isWhitespace()) pos++ }

        fun value(): Any? {
            if (pos >= s.length) throw IllegalArgumentException("váratlan vég")
            return when (val c = s[pos]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else throw IllegalArgumentException("váratlan karakter '$c' ($pos)")
            }
        }

        private fun lit(w: String, v: Any?): Any? {
            if (!s.startsWith(w, pos)) throw IllegalArgumentException("hibás érték ($pos)")
            pos += w.length
            return v
        }

        private fun num(): Double {
            val st = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] in "+-.eE")) pos++
            return s.substring(st, pos).toDoubleOrNull() ?: throw IllegalArgumentException("hibás szám ($st)")
        }

        private fun str(): String {
            pos++ // nyitó "
            val sb = StringBuilder()
            while (pos < s.length) {
                val c = s[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= s.length) break
                        when (val e = s[pos++]) {
                            '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                            'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                            'u' -> {
                                if (pos + 4 > s.length) throw IllegalArgumentException("hibás \\u")
                                sb.append(s.substring(pos, pos + 4).toInt(16).toChar())
                                pos += 4
                            }
                            else -> throw IllegalArgumentException("hibás escape \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
            throw IllegalArgumentException("lezáratlan string")
        }

        private fun arr(): List<Any?> {
            pos++
            val out = ArrayList<Any?>()
            ws()
            if (pos < s.length && s[pos] == ']') { pos++; return out }
            while (true) {
                ws(); out.add(value()); ws()
                if (pos >= s.length) throw IllegalArgumentException("lezáratlan tömb")
                when (s[pos++]) {
                    ',' -> continue
                    ']' -> return out
                    else -> throw IllegalArgumentException("hibás tömb ($pos)")
                }
            }
        }

        private fun obj(): Map<String, Any?> {
            pos++
            val out = LinkedHashMap<String, Any?>()
            ws()
            if (pos < s.length && s[pos] == '}') { pos++; return out }
            while (true) {
                ws()
                if (pos >= s.length || s[pos] != '"') throw IllegalArgumentException("kulcs hiányzik ($pos)")
                val k = str()
                ws()
                if (pos >= s.length || s[pos++] != ':') throw IllegalArgumentException("':' hiányzik")
                ws(); out[k] = value(); ws()
                if (pos >= s.length) throw IllegalArgumentException("lezáratlan objektum")
                when (s[pos++]) {
                    ',' -> continue
                    '}' -> return out
                    else -> throw IllegalArgumentException("hibás objektum ($pos)")
                }
            }
        }
    }
}
