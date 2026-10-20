package hu.angolmagyar.fordito

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Átirat-tár: lista-információk, címke/cím, kuka (visszavonáshoz) és teljes szöveges keresés. */
object TranscriptRepo {

    data class Info(
        val file: File,
        val title: String,
        val tag: String,
        val whenLabel: String,
        val durationMs: Long,
        val entries: Int,
        val firstLine: String,
        val hasAudio: Boolean,
        val sizeKb: Long
    )

    data class Hit(val file: File, val title: String, val stampMs: Long, val snippet: String)

    // ------------------------------------------------------------------ meta (cím, címke)

    private fun metaFile(txt: File) = File(txt.parentFile, txt.nameWithoutExtension + ".meta")

    fun readMeta(txt: File): Pair<String, String> {
        return try {
            val f = metaFile(txt)
            if (!f.exists()) return "" to ""
            val o = JSONObject(f.readText())
            o.optString("title", "") to o.optString("tag", "")
        } catch (_: Exception) {
            "" to ""
        }
    }

    fun writeMeta(txt: File, title: String, tag: String) {
        try {
            val f = metaFile(txt)
            if (title.isBlank() && tag.isBlank()) {
                f.delete()
            } else {
                f.writeText(JSONObject().put("title", title.trim()).put("tag", tag.trim()).toString())
            }
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------------------ lista

    private fun parseStampLine(l: String): Long? {
        if (l.length < 10 || l[0] != '[' || l[9] != ']') return null
        return try {
            val h = l.substring(1, 3).toLong()
            val m = l.substring(4, 6).toLong()
            val s = l.substring(7, 9).toLong()
            (h * 3600 + m * 60 + s) * 1000
        } catch (_: Exception) {
            null
        }
    }

    /** Gyors áttekintés egy fájlról (soronként olvas, nem tölti be az egészet a memóriába). */
    fun info(file: File): Info {
        var count = 0
        var last = 0L
        var first = ""
        try {
            file.useLines { lines ->
                for (l in lines) {
                    val st = parseStampLine(l) ?: continue
                    count++
                    last = st
                    if (first.isEmpty()) {
                        first = l.substring(10).trim().replace(Regex("^\\{[^}]*}\\s*"), "")
                    }
                }
            }
        } catch (_: Exception) {
        }
        if (count == 0) {
            first = try {
                file.useLines { seq -> seq.firstOrNull { it.isNotBlank() } ?: "" }.trim()
            } catch (_: Exception) {
                ""
            }
        }
        val (title, tag) = readMeta(file)
        val raw = file.nameWithoutExtension
        val when_ = parseNameDate(raw) ?: SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            .format(Date(file.lastModified()))
        return Info(
            file = file,
            title = title.ifBlank { raw.removePrefix("atirat_") },
            tag = tag,
            whenLabel = when_,
            durationMs = last,
            entries = count,
            firstLine = first.take(120),
            hasAudio = TranscriptStore.audioFile(file).exists(),
            sizeKb = file.length() / 1024
        )
    }

    private fun parseNameDate(name: String): String? {
        val m = Regex("""(\d{8})_(\d{6})""").find(name) ?: return null
        return try {
            val d = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).parse(m.value) ?: return null
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(d)
        } catch (_: Exception) {
            null
        }
    }

    fun listInfos(ctx: Context): List<Info> {
        purgeTrash(ctx)
        return TranscriptStore.list(ctx).map { info(it) }
    }

    // ------------------------------------------------------------------ kuka

    private val SIDE_EXT = listOf("aac", "meta", "partial")

    /** A fájl (és a hozzá tartozó hang/meta) a kukába kerül; [restore] visszaállítja. */
    fun trash(ctx: Context, txt: File): Boolean {
        val dest = TranscriptStore.trashDir(ctx)
        var ok = txt.renameTo(File(dest, txt.name))
        for (ext in SIDE_EXT) {
            val side = File(txt.parentFile, txt.nameWithoutExtension + "." + ext)
            if (side.exists()) side.renameTo(File(dest, side.name))
        }
        if (!ok) {
            // fallback: másolás + törlés
            ok = try {
                txt.copyTo(File(dest, txt.name), overwrite = true)
                txt.delete()
            } catch (_: Exception) {
                false
            }
        }
        return ok
    }

    fun restore(ctx: Context, name: String) {
        val trash = TranscriptStore.trashDir(ctx)
        val base = name.substringBeforeLast('.')
        val files = trash.listFiles { f -> f.nameWithoutExtension == base } ?: return
        for (f in files) {
            f.renameTo(File(TranscriptStore.dir(ctx), f.name))
        }
    }

    /** A 2 napnál régebbi kukatartalom végleges törlése. */
    fun purgeTrash(ctx: Context) {
        try {
            val limit = System.currentTimeMillis() - 2L * 24 * 3600 * 1000
            TranscriptStore.trashDir(ctx).listFiles()?.forEach {
                if (it.lastModified() < limit) it.delete()
            }
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------------------ keresés

    /** Ékezet- és kisbetű-független összehasonlító forma (ő → o, é → e …). */
    fun fold(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

    /** Hosszt megőrző ékezet- és kisbetű-független forma (kiemeléshez: a pozíciók azonosak). */
    fun foldKeep(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            val d = Normalizer.normalize(ch.toString(), Normalizer.Form.NFD)
            val base = d.firstOrNull { Character.getType(it) != Character.NON_SPACING_MARK.toInt() } ?: ch
            sb.append(base.lowercaseChar())
        }
        return sb.toString()
    }

    /** Keresés az összes átiratban; `#címke` esetén címke szerinti szűrés. */
    fun search(ctx: Context, query: String, maxHits: Int = 200): List<Hit> {
        val q = fold(query.trim())
        if (q.length < 2) return emptyList()
        val hits = mutableListOf<Hit>()
        for (f in TranscriptStore.list(ctx)) {
            val title = readMeta(f).first.ifBlank { f.nameWithoutExtension.removePrefix("atirat_") }
            try {
                var lastStamp = 0L
                f.useLines { lines ->
                    for (l in lines) {
                        val st = parseStampLine(l)
                        if (st != null) lastStamp = st
                        if (l.isBlank()) continue
                        if (fold(l).contains(q)) {
                            val text = l.substringAfter("]", l).trim().replace(Regex("^\\{[^}]*}\\s*"), "")
                            hits.add(Hit(f, title, lastStamp, text.take(160)))
                            if (hits.size >= maxHits) return hits
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        return hits
    }
}
