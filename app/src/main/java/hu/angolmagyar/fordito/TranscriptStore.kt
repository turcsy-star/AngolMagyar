package hu.angolmagyar.fordito

import android.content.Context
import android.os.SystemClock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Átiratok mentése: egy munkamenet = egy .txt fájl, relatív időbélyeggel, forrás + cél sorral.
 * Formátum (kompatibilis az SrtExporter-rel):
 *
 *     [00:01:23] {Beszélő} forrás szöveg
 *                fordítás
 *
 * - a fájlnév a munkamenet elején eldől (a fájl az első sorral jön létre)
 * - a folyamatban lévő (még nem lezárt) mondat időnként .partial fájlba kerül,
 *   hogy ha a rendszer kilövi a szolgáltatást, a következő indításkor pótolható legyen
 * - [resume] esetén az utolsó átirat folytatódik, az időbélyegek az eddigi végétől számolnak
 */
class TranscriptStore(private val ctx: Context, resume: Boolean = false) {

    companion object {
        fun dir(ctx: Context): File = File(ctx.filesDir, "transcripts").apply { mkdirs() }

        fun trashDir(ctx: Context): File = File(ctx.filesDir, "transcripts_trash").apply { mkdirs() }

        fun list(ctx: Context): List<File> =
            dir(ctx).listFiles { f -> f.extension == "txt" }
                ?.sortedByDescending { it.name } ?: emptyList()

        fun partialFile(txt: File) = File(txt.parentFile, txt.nameWithoutExtension + ".partial")

        fun audioFile(txt: File) = File(txt.parentFile, txt.nameWithoutExtension + ".aac")

        fun stamp(ms: Long): String {
            val total = (ms / 1000).coerceAtLeast(0)
            return String.format(Locale.US, "%02d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60)
        }

        /** Az utolsó [HH:mm:ss] bélyeg ms-ben (0, ha nincs). */
        fun lastStampMs(text: String): Long {
            val re = Regex("""(?m)^\[(\d{2}):(\d{2}):(\d{2})\]""")
            var last = 0L
            for (m in re.findAll(text)) {
                last = (m.groupValues[1].toLong() * 3600 + m.groupValues[2].toLong() * 60 +
                    m.groupValues[3].toLong()) * 1000
            }
            return last
        }

        /** A rendszer által kilőtt munkamenetek félbehagyott mondatainak pótlása. */
        fun recoverPartials(ctx: Context) {
            try {
                val parts = dir(ctx).listFiles { f -> f.extension == "partial" } ?: return
                for (p in parts) {
                    val txt = File(p.parentFile, p.nameWithoutExtension + ".txt")
                    val lines = p.readLines()
                    if (lines.size >= 2 && lines[1].isNotBlank()) {
                        txt.appendText("[${lines[0]}] ${lines[1]}\n           …\n\n")
                    }
                    p.delete()
                }
            } catch (e: Exception) { DiagLog.swallow("TranscriptStore", e) }
        }
    }

    private val exec = Executors.newSingleThreadExecutor()
    private val file: File
    private val resumed: Boolean
    private var offsetMs = 0L
    private var offsetReady = false
    @Volatile private var startMs = SystemClock.elapsedRealtime()
    private var lastPartialAt = 0L
    private val entryCount = AtomicInteger(0)
    @Volatile private var lastEntryMs = 0L

    init {
        val prev = if (resume) list(ctx).firstOrNull() else null
        resumed = prev != null
        file = prev ?: File(
            dir(ctx),
            "atirat_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".txt"
        )
    }

    /** Folytatott fájl-e (ilyenkor a hangmentés nem illeszthető az időbélyegekhez). */
    fun isResumed() = resumed

    /** A hangfelvétel tényleges kezdetéhez igazítja a nullpontot. */
    fun markStart() {
        startMs = SystemClock.elapsedRealtime()
    }

    private fun ensureOffset() {
        if (offsetReady) return
        offsetReady = true
        if (resumed && file.exists()) {
            offsetMs = try {
                lastStampMs(file.readText()) + 1000
            } catch (_: Exception) {
                0L
            }
        }
    }

    private fun relNow(): Long = SystemClock.elapsedRealtime() - startMs

    /** Új sor az átiratba (a bélyeg a munkamenet kezdetétől számított idő). */
    fun append(source: String, target: String, speaker: String = "") {
        val rel = relNow()
        exec.execute {
            try {
                ensureOffset()
                val total = rel + offsetMs
                val sp = if (speaker.isBlank()) "" else "{$speaker} "
                file.appendText("[${stamp(total)}] $sp$source\n           $target\n\n")
                entryCount.incrementAndGet()
                lastEntryMs = total
                // a lezárt mondat már bent van: a félbehagyott másolat felesleges
                partialFile(file).delete()
            } catch (e: Exception) { DiagLog.swallow("TranscriptStore", e) }
        }
    }

    /** Kompatibilitás: korábbi hívók relatív bélyeggel. */
    fun appendTimed(source: String, target: String, relativeMs: Long) {
        exec.execute {
            try {
                ensureOffset()
                val total = relativeMs + offsetMs
                file.appendText("[${stamp(total)}] $source\n           $target\n\n")
                entryCount.incrementAndGet()
                lastEntryMs = total
            } catch (e: Exception) { DiagLog.swallow("TranscriptStore", e) }
        }
    }

    /** A folyamatban lévő mondat mentése (legfeljebb 3 mp-enként). */
    fun savePartial(text: String) {
        val now = SystemClock.elapsedRealtime()
        if (text.isBlank() || now - lastPartialAt < 3000) return
        lastPartialAt = now
        val rel = relNow()
        exec.execute {
            try {
                ensureOffset()
                partialFile(file).writeText(stamp(rel + offsetMs) + "\n" + text.replace('\n', ' ') + "\n")
            } catch (e: Exception) { DiagLog.swallow("TranscriptStore", e) }
        }
    }

    fun currentFile(): File? = file

    fun audioFile(): File = audioFile(file)

    fun entries(): Int = entryCount.get()

    fun lastEntryMs(): Long = lastEntryMs

    fun close() {
        exec.execute {
            try {
                partialFile(file).delete()
            } catch (e: Exception) { DiagLog.swallow("TranscriptStore", e) }
        }
        exec.shutdown()
    }
}
