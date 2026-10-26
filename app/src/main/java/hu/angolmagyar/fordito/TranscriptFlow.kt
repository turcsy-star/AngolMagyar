package hu.angolmagyar.fordito

import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.widget.TextView

/**
 * Élő átirat-folyam a hullámforma alatt: a legutóbb felismert sorok beszélőszínnel; a legfrissebb sor
 * betűnként "gépelődik", majd áttűnéssel megjelenik alatta a fordítás. A bizonytalan sorok sárgásan derengenek.
 */
class TranscriptFlow(private val tv: TextView) {

    private var shownCount = -1
    private var lastKey = ""
    private var anim: ValueAnimator? = null

    private val palette = intArrayOf(
        0xFF2F80ED.toInt(), 0xFFE67E22.toInt(), 0xFF27AE60.toInt(),
        0xFFD63384.toInt(), 0xFF8E44AD.toInt(), 0xFF16A085.toInt()
    )

    private fun colorFor(speaker: String): Int {
        val n = speaker.filter { it.isDigit() }.toIntOrNull() ?: return 0xFF888888.toInt()
        return palette[(n - 1).coerceAtLeast(0) % palette.size]
    }

    /** @return true, ha van mit mutatni. */
    fun update(cues: List<SrtExporter.Cue>, textColor: Int, accent: Int): Boolean {
        if (cues.isEmpty()) return false
        val last = cues.last()
        val key = "${last.startMs}|${last.source}|${last.target}"
        if (key == lastKey && cues.size == shownCount) return true
        val isNew = key != lastKey
        lastKey = key
        shownCount = cues.size
        anim?.cancel()
        val flags = try { LineQuality.summarize(cues) } catch (_: Exception) { cues.map { emptyList<String>() } }
        fun render(p: Float) {
            val sb = SpannableStringBuilder()
            for ((i, c) in cues.withIndex()) {
                val newest = i == cues.size - 1 && isNew
                val prog = if (newest) p else 1f
                val tagStart = sb.length
                val t = SrtExporter.formatTime(c.startMs).take(8)
                sb.append(t)
                if (c.speaker.isNotBlank()) sb.append("  ").append(c.speaker)
                sb.append("\n")
                sb.setSpan(ForegroundColorSpan(colorFor(c.speaker)), tagStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(StyleSpan(Typeface.BOLD), tagStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(RelativeSizeSpan(0.8f), tagStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                val src = c.source.ifBlank { c.target }
                val srcShown = if (newest) src.substring(0, (src.length * (prog / 0.6f).coerceIn(0f, 1f)).toInt()) else src
                val s0 = sb.length
                sb.append(srcShown)
                val shaky = flags.getOrNull(i)?.isNotEmpty() == true || c.lp < -1f
                if (shaky && srcShown.isNotEmpty()) {
                    sb.setSpan(BackgroundColorSpan(Color.argb(60, 255, 200, 0)), s0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                sb.append("\n")
                if (c.target.isNotBlank() && c.target != c.source) {
                    val a = if (newest) ((prog - 0.6f) / 0.4f).coerceIn(0f, 1f) else 1f
                    if (a > 0f) {
                        val t0 = sb.length
                        sb.append("→ ").append(c.target).append("\n")
                        val col = Color.argb((255 * a).toInt(), Color.red(accent), Color.green(accent), Color.blue(accent))
                        sb.setSpan(ForegroundColorSpan(col), t0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
                sb.append("\n")
            }
            tv.text = sb.subSequence(0, trimmedLen(sb))
        }
        if (isNew) {
            anim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 900L
                addUpdateListener { render(it.animatedValue as Float) }
                start()
            }
        } else render(1f)
        return true
    }

    private fun trimmedLen(s: CharSequence): Int {
        var e = s.length
        while (e > 0 && s[e - 1] == '\n') e--
        return e
    }

    fun stop() { anim?.cancel() }
}
