package hu.angolmagyar.fordito

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * Ellenőrzőpont a félbeszakadt feldolgozás folytatásához: minden kész darab cue-it a lemezre írja.
 * Ha ugyanazt a fájlt ugyanazokkal a beállításokkal újraindítod, a kész darabokat nem számolja újra.
 * Formátum (soronként, tabbal tagolva): "C idx n" fejléc, utána n db "start end speaker source target".
 */
class Checkpoint(private val file: File, private val key: String) {

    /** A korábban kész darabok (idx → cue-k). Ha a kulcs nem egyezik vagy a fájl hibás, üres. */
    fun load(): Map<Int, List<SrtExporter.Cue>> {
        val res = LinkedHashMap<Int, List<SrtExporter.Cue>>()
        if (!file.exists()) return res
        val lines = try { file.readLines(Charsets.UTF_8) } catch (_: Exception) { return res }
        if (lines.isEmpty() || lines[0] != "#K\t$key") return res
        var i = 1
        while (i < lines.size) {
            val h = lines[i].split("\t")
            if (h.size != 3 || h[0] != "C") break
            val idx = h[1].toIntOrNull() ?: break
            val n = h[2].toIntOrNull() ?: break
            if (i + n > lines.size - 1) break // csonka (félbemaradt) rekord
            val cues = ArrayList<SrtExporter.Cue>(n)
            var ok = true
            for (k in 1..n) {
                val c = decode(lines[i + k])
                if (c == null) { ok = false; break }
                cues.add(c)
            }
            if (!ok) break
            res[idx] = cues
            i += n + 1
        }
        return res
    }

    @Synchronized
    fun append(idx: Int, cues: List<SrtExporter.Cue>) {
        try {
            file.parentFile?.mkdirs()
            val sb = StringBuilder()
            if (!file.exists() || file.length() == 0L) sb.append("#K\t").append(key).append('\n')
            sb.append("C\t").append(idx).append('\t').append(cues.size).append('\n')
            for (c in cues) sb.append(encode(c)).append('\n')
            file.appendText(sb.toString(), Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }

    fun clear() {
        try { file.delete() } catch (_: Exception) {}
    }

    companion object {
        fun forJob(ctx: Context, key: String): Checkpoint {
            val dir = File(ctx.filesDir, "ckpt")
            // a régi (7 napnál régebbi) ellenőrzőpontok törlése
            try {
                dir.listFiles()?.forEach {
                    if (System.currentTimeMillis() - it.lastModified() > 7L * 24 * 3600 * 1000) it.delete()
                }
            } catch (_: Exception) {
            }
            return Checkpoint(File(dir, "$key.ckpt"), key)
        }

        fun keyOf(vararg parts: Any): String {
            val md = MessageDigest.getInstance("SHA-1")
            val bytes = md.digest(parts.joinToString("|") { it.toString() }.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }.take(24)
        }

        private fun esc(s: String): String {
            val sb = StringBuilder(s.length + 8)
            for (ch in s) when (ch) {
                '\\' -> sb.append("\\\\")
                '\t' -> sb.append("\\t")
                '\n' -> sb.append("\\n")
                '\r' -> {}
                else -> sb.append(ch)
            }
            return sb.toString()
        }

        private fun unesc(s: String): String {
            val sb = StringBuilder(s.length)
            var i = 0
            while (i < s.length) {
                val ch = s[i]
                if (ch == '\\' && i + 1 < s.length) {
                    when (s[i + 1]) {
                        't' -> sb.append('\t')
                        'n' -> sb.append('\n')
                        else -> sb.append(s[i + 1])
                    }
                    i += 2
                } else {
                    sb.append(ch)
                    i++
                }
            }
            return sb.toString()
        }

        fun encode(c: SrtExporter.Cue): String =
            "${c.startMs}\t${c.endMs}\t${esc(c.speaker)}\t${esc(c.source)}\t${esc(c.target)}"

        fun decode(line: String): SrtExporter.Cue? {
            val p = line.split("\t")
            if (p.size != 5) return null
            val s = p[0].toLongOrNull() ?: return null
            val e = p[1].toLongOrNull() ?: return null
            return SrtExporter.Cue(s, e, unesc(p[3]), unesc(p[4]), unesc(p[2]))
        }
    }
}
