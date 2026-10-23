package hu.angolmagyar.fordito

import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * SRT felirat generálása időbélyeges sorokból.
 * Támogatott módok: csak forrás, csak cél, mindkettő (két sor / blokk).
 */
object SrtExporter {

    data class Cue(
        val startMs: Long,
        val endMs: Long,
        val source: String,
        val target: String,
        val speaker: String = "" // pl. S1, S2 vagy "Anna"
    )

    enum class Mode {
        SOURCE_ONLY,   // csak angol vagy csak magyar (forrás)
        TARGET_ONLY,   // csak fordítás
        BOTH           // forrás + cél egy blokkban
    }

    fun formatTime(ms: Long): String {
        val h = TimeUnit.MILLISECONDS.toHours(ms)
        val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        val milli = ms % 1000
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", h, m, s, milli)
    }

    private fun prefixSpeaker(c: Cue, body: String, colors: Map<String, String>? = null): String {
        if (c.speaker.isBlank() || body.isBlank()) return body
        val lines = body.split("\n").toMutableList()
        val col = colors?.get(c.speaker)
        if (lines.isNotEmpty()) {
            lines[0] = if (col != null) "<font color=\"$col\">${c.speaker}:</font> ${lines[0]}"
            else "${c.speaker}: ${lines[0]}"
        }
        return lines.joinToString("\n")
    }

    /** Max ~42 karakter / sor, szóhatáron törve. */
    fun wrap(text: String, maxLen: Int = 42): List<String> {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val lines = mutableListOf<String>()
        var cur = StringBuilder()
        for (w in words) {
            if (cur.isEmpty()) {
                cur.append(w)
            } else if (cur.length + 1 + w.length <= maxLen) {
                cur.append(' ').append(w)
            } else {
                lines.add(cur.toString())
                cur = StringBuilder(w)
            }
        }
        if (cur.isNotEmpty()) lines.add(cur.toString())
        return lines
    }

    /** @param colors beszélő → szín (#RRGGBB): ha meg van adva, a beszélőjelölés színes (<font>) */
    fun build(cues: List<Cue>, mode: Mode, colors: Map<String, String>? = null): String {
        val sb = StringBuilder()
        var idx = 1
        for (c in cues) {
            val body = when (mode) {
                Mode.SOURCE_ONLY -> prefixSpeaker(c, SubFormat.wrapBalanced(c.source).joinToString("\n"), colors)
                Mode.TARGET_ONLY -> prefixSpeaker(c, SubFormat.wrapBalanced(c.target).joinToString("\n"), colors)
                Mode.BOTH -> {
                    val src = SubFormat.wrapBalanced(c.source).joinToString("\n")
                    val tgt = SubFormat.wrapBalanced(c.target).joinToString("\n")
                    val merged = if (src.isBlank()) tgt
                    else if (tgt.isBlank()) src
                    else "$src\n$tgt"
                    prefixSpeaker(c, merged, colors)
                }
            }
            if (body.isBlank()) continue
            val end = if (c.endMs <= c.startMs) c.startMs + 2000 else c.endMs
            sb.append(idx).append('\n')
            sb.append(formatTime(c.startMs)).append(" --> ").append(formatTime(end)).append('\n')
            sb.append(body).append("\n\n")
            idx++
        }
        return sb.toString()
    }

    fun write(file: File, cues: List<Cue>, mode: Mode) {
        file.parentFile?.mkdirs()
        file.writeText(build(cues, mode), Charsets.UTF_8)
    }

    /** WebVTT időbélyeg (pont a milliszekundumnál). */
    fun formatTimeVtt(ms: Long): String {
        val h = TimeUnit.MILLISECONDS.toHours(ms)
        val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        val milli = ms % 1000
        return String.format(Locale.US, "%02d:%02d:%02d.%03d", h, m, s, milli)
    }

    /** WebVTT felirat (HTML5 / YouTube / böngésző kompatibilis). */
    fun buildVtt(cues: List<Cue>, mode: Mode): String {
        val sb = StringBuilder()
        sb.append("WEBVTT\n\n")
        var idx = 1
        for (c in cues) {
            val body = when (mode) {
                Mode.SOURCE_ONLY -> prefixSpeaker(c, SubFormat.wrapBalanced(c.source).joinToString("\n"))
                Mode.TARGET_ONLY -> prefixSpeaker(c, SubFormat.wrapBalanced(c.target).joinToString("\n"))
                Mode.BOTH -> {
                    val src = SubFormat.wrapBalanced(c.source).joinToString("\n")
                    val tgt = SubFormat.wrapBalanced(c.target).joinToString("\n")
                    val merged = if (src.isBlank()) tgt
                    else if (tgt.isBlank()) src
                    else "$src\n$tgt"
                    prefixSpeaker(c, merged)
                }
            }
            if (body.isBlank()) continue
            val end = if (c.endMs <= c.startMs) c.startMs + 2000 else c.endMs
            sb.append(idx).append('\n')
            sb.append(formatTimeVtt(c.startMs)).append(" --> ").append(formatTimeVtt(end)).append('\n')
            sb.append(body).append("\n\n")
            idx++
        }
        return sb.toString()
    }

    private val STAMP_LINE by lazy { Regex("""^\[(\d{2}):(\d{2}):(\d{2})\]\s?(.*)$""") }
    private val SPEAKER_PREFIX by lazy { Regex("""^\{([^\}]*)\}\s*""") }

    /** Átirat szöveg → Cue lista (sor alapú, a {Beszélő} jelölést is érti). */
    fun parseCuesFromTranscript(text: String): List<Cue> {
        data class Raw(val start: Long, val source: String, var target: String, val speaker: String)

        val raws = mutableListOf<Raw>()
        var cur: Raw? = null
        for (line in text.lines()) {
            val m = STAMP_LINE.find(line)
            if (m != null) {
                val start = (m.groupValues[1].toLong() * 3600 + m.groupValues[2].toLong() * 60 +
                    m.groupValues[3].toLong()) * 1000
                var src = m.groupValues[4].trim()
                var spk = ""
                SPEAKER_PREFIX.find(src)?.let { sm ->
                    spk = sm.groupValues[1].trim()
                    src = src.substring(sm.value.length).trim()
                }
                cur = Raw(start, src, "", spk)
                raws.add(cur)
            } else if (cur != null && line.isNotBlank() && (line.startsWith(" ") || line.startsWith("\t"))) {
                cur.target = if (cur.target.isEmpty()) line.trim() else cur.target + " " + line.trim()
            }
        }
        if (raws.isEmpty()) {
            val lines = text.lines().filter { it.isNotBlank() }
            return lines.mapIndexed { i, line ->
                val start = i * 3000L
                Cue(start, start + 3000, line, "")
            }
        }
        return raws.mapIndexed { i, r ->
            val end = if (i + 1 < raws.size) raws[i + 1].start else r.start + 3000
            Cue(r.start, end, r.source, r.target, r.speaker)
        }
    }

    /** Cue lista → átirat szöveg (TranscriptStore formátum). */
    fun toTranscriptText(cues: List<Cue>): String {
        val sb = StringBuilder()
        for (c in cues) {
            val total = (c.startMs / 1000).coerceAtLeast(0)
            val stamp = String.format(
                Locale.US, "%02d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60
            )
            val sp = if (c.speaker.isBlank()) "" else "{" + c.speaker.replace("{", "").replace("}", "") + "} "
            sb.append('[').append(stamp).append("] ").append(sp)
                .append(c.source.ifBlank { "…" }.replace('\n', ' ')).append("\n           ")
                .append(c.target.ifBlank { "…" }.replace('\n', ' ')).append("\n\n")
        }
        return sb.toString()
    }

    /**
     * Egyszerű szöveges átiratból (TranscriptStore formátum) SRT-t próbál készíteni.
     * A [HH:mm:ss] bélyegeket használja; a hossz a következő bejegyzésig, vagy 3 mp.
     */
    fun fromTranscriptText(text: String, mode: Mode = Mode.BOTH): String {
        val cues = parseCuesFromTranscript(text)
        return if (cues.any { it.target.isNotBlank() }) build(cues, mode)
        else build(cues, Mode.SOURCE_ONLY)
    }

    fun vttFromTranscriptText(text: String, mode: Mode = Mode.BOTH): String {
        val cues = parseCuesFromTranscript(text)
        return if (cues.any { it.target.isNotBlank() }) buildVtt(cues, mode)
        else buildVtt(cues, Mode.SOURCE_ONLY)
    }
}
