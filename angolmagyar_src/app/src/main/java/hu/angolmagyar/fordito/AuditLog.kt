package hu.angolmagyar.fordito

import android.content.Context
import hu.angolmagyar.fordito.core.review.ReviewAuditEntry
import org.json.JSONObject
import java.io.File

/**
 * Visszavonható módosítási napló fájlonként (soronként egy JSON). Minden kézi vagy automatikus szövegváltoztatás
 * ide kerül az előtte/utána értékkel, az okkal és azzal, hogy a felhasználó fogadta-e el.
 */
object AuditLog {
    private const val MAX_ENTRIES = 400

    private fun file(ctx: Context, key: String): File {
        val dir = File(ctx.filesDir, "audit")
        dir.mkdirs()
        return File(dir, OutputWriter.safeName(key).take(80) + ".jsonl")
    }

    @Synchronized
    fun add(ctx: Context, key: String, e: ReviewAuditEntry) {
        if (key.isBlank()) return
        try {
            val o = JSONObject()
                .put("t", e.timestampEpochMs).put("i", e.lineIndex)
                .put("b", e.beforeText).put("a", e.afterText)
                .put("r", e.reason).put("u", e.acceptedByUser).put("s", e.source)
            val f = file(ctx, key)
            f.appendText(o.toString() + "\n", Charsets.UTF_8)
            if (f.length() > 400_000L) {
                f.writeText(load(ctx, key).takeLast(MAX_ENTRIES).joinToString("\n") { toJson(it) } + "\n", Charsets.UTF_8)
            }
        } catch (ex: Exception) {
            DiagLog.log("AuditLog írás sikertelen: ${ex.message}")
        }
    }

    private fun toJson(e: ReviewAuditEntry) = JSONObject()
        .put("t", e.timestampEpochMs).put("i", e.lineIndex).put("b", e.beforeText).put("a", e.afterText)
        .put("r", e.reason).put("u", e.acceptedByUser).put("s", e.source).toString()

    @Synchronized
    fun load(ctx: Context, key: String): List<ReviewAuditEntry> {
        val f = file(ctx, key)
        if (!f.exists()) return emptyList()
        val out = ArrayList<ReviewAuditEntry>()
        try {
            for (ln in f.readLines(Charsets.UTF_8)) {
                if (ln.isBlank()) continue
                try {
                    val o = JSONObject(ln)
                    out.add(ReviewAuditEntry(o.getLong("t"), o.getInt("i"), o.optString("b"), o.optString("a"),
                        o.optString("r"), o.optBoolean("u", true), o.optString("s", "manual")))
                } catch (_: Exception) { /* hibás sor kihagyása */ }
            }
        } catch (ex: Exception) {
            DiagLog.log("AuditLog olvasás sikertelen: ${ex.message}")
        }
        return out
    }

    fun clear(ctx: Context, key: String) {
        try { file(ctx, key).delete() } catch (ex: Exception) { DiagLog.log("AuditLog törlés: ${ex.message}") }
    }

    /** A kétlistás változás sorszintű naplózása (azonos hossznál soronként; különben egy összegző bejegyzés). */
    fun recordDiff(
        ctx: Context, key: String, before: List<SrtExporter.Cue>, after: List<SrtExporter.Cue>,
        reason: String, source: String, showTarget: Boolean
    ) {
        val now = System.currentTimeMillis()
        fun txt(c: SrtExporter.Cue) = if (showTarget) c.target else c.source
        if (before.size == after.size) {
            for (i in before.indices) {
                val b = txt(before[i]); val a = txt(after[i])
                if (b != a) add(ctx, key, ReviewAuditEntry(now, i, b, a, reason, true, source))
            }
        } else {
            add(ctx, key, ReviewAuditEntry(now, -1, "${before.size} blokk", "${after.size} blokk", reason, true, source))
            // a megváltozott szövegek külön is megőrződnek (a hely szerinti párosítás a közös elejig)
            val n = minOf(before.size, after.size)
            for (i in 0 until n) {
                val b = txt(before[i]); val a = txt(after[i])
                if (b != a) add(ctx, key, ReviewAuditEntry(now, i, b, a, reason, true, source))
            }
        }
    }
}
