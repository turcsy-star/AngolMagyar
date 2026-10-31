package hu.angolmagyar.fordito

import android.content.Context
import hu.angolmagyar.fordito.core.review.RecognitionBenchmark
import org.json.JSONObject
import java.io.File

/**
 * Referencia ("arany") átiratok és mérési előzmények. A referencia a felhasználó által kijavított forrásszöveg;
 * új verzióval újrafuttatott ugyanazon fájlra a WER és a kritikus hibák száma összevethető.
 * A WER felismerési mérőszám, nem fordítás- vagy jogi hitelességi mutató.
 */
object Benchmark {
    private fun dir(ctx: Context) = File(ctx.filesDir, "golden").also { it.mkdirs() }
    private fun id(key: String) = OutputWriter.safeName(key).take(80)
    private fun ref(ctx: Context, key: String) = File(dir(ctx), id(key) + ".txt")
    private fun hist(ctx: Context) = File(dir(ctx), "history.jsonl")

    fun hasReference(ctx: Context, key: String) = ref(ctx, key).exists()

    fun saveReference(ctx: Context, key: String, cues: List<SrtExporter.Cue>) {
        ref(ctx, key).writeText(cues.joinToString("\n") { it.source.replace('\n', ' ').trim() }, Charsets.UTF_8)
    }

    fun deleteReference(ctx: Context, key: String) { try { ref(ctx, key).delete() } catch (_: Exception) {} }

    data class Report(
        val bench: RecognitionBenchmark, val result: WordErrorRate.Result,
        val numberDiffs: Int, val negationDiffs: Int
    )

    private val NEG = Regex("(?<![\\p{L}])(?:nem|ne|sem|se|nincs|soha|not|no|never|none|without)(?![\\p{L}])", RegexOption.IGNORE_CASE)

    private fun versionName(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    } catch (_: Exception) { "?" }

    /** A jelenlegi felismerést a mentett referenciához méri; null, ha nincs referencia. */
    fun evaluate(ctx: Context, key: String, cues: List<SrtExporter.Cue>, elapsedMs: Long, audioMs: Long): Report? {
        val f = ref(ctx, key)
        if (!f.exists()) return null
        val reference = try { f.readText(Charsets.UTF_8) } catch (_: Exception) { return null }
        val hypothesis = cues.joinToString(" ") { it.source }
        val r = WordErrorRate.compare(reference, hypothesis)
        val refNums = TranslationCheck.numbers(reference).groupingBy { it }.eachCount()
        val hypNums = TranslationCheck.numbers(hypothesis).groupingBy { it }.eachCount()
        var numberDiffs = 0
        for (k in (refNums.keys + hypNums.keys)) numberDiffs += kotlin.math.abs((refNums[k] ?: 0) - (hypNums[k] ?: 0))
        val negDiffs = kotlin.math.abs(NEG.findAll(reference).count() - NEG.findAll(hypothesis).count())
        val critical = numberDiffs + negDiffs
        val b = RecognitionBenchmark(versionName(ctx), id(key), r.rate, critical, elapsedMs, audioMs)
        return Report(b, r, numberDiffs, negDiffs)
    }

    fun record(ctx: Context, b: RecognitionBenchmark) {
        try {
            val o = JSONObject().put("v", b.version).put("ref", b.referenceId).put("wer", b.wordErrorRate)
                .put("crit", b.criticalErrors).put("ms", b.elapsedMs).put("audio", b.audioDurationMs)
                .put("t", System.currentTimeMillis())
            hist(ctx).appendText(o.toString() + "\n", Charsets.UTF_8)
        } catch (ex: Exception) { DiagLog.log("Benchmark előzmény írása sikertelen: ${ex.message}") }
    }

    fun history(ctx: Context, key: String): List<RecognitionBenchmark> {
        val f = hist(ctx)
        if (!f.exists()) return emptyList()
        val out = ArrayList<RecognitionBenchmark>()
        for (ln in try { f.readLines(Charsets.UTF_8) } catch (_: Exception) { emptyList() }) {
            try {
                val o = JSONObject(ln)
                if (o.optString("ref") == id(key))
                    out.add(RecognitionBenchmark(o.optString("v"), o.optString("ref"), o.optDouble("wer"),
                        o.optInt("crit"), o.optLong("ms"), o.optLong("audio")))
            } catch (_: Exception) {}
        }
        return out
    }
}
