package hu.angolmagyar.fordito

import android.content.Context

/** Melyik beszédfelismerő motor dolgozzon a fájl-átiratnál. */
object AsrPrefs {
    const val AUTO = "auto"
    const val WHISPER = "whisper"
    const val VOSK = "vosk"

    private fun p(ctx: Context) = Prefs(ctx).raw()

    fun engine(ctx: Context): String = p(ctx).getString("asrEngine", AUTO) ?: AUTO
    fun setEngine(ctx: Context, v: String) { p(ctx).edit().putString("asrEngine", v).apply() }

    fun whisperId(ctx: Context): String? = p(ctx).getString("whisperModel", null)
    fun setWhisperId(ctx: Context, id: String) { p(ctx).edit().putString("whisperModel", id).apply() }

    /** Beam-keresés szélessége (1 = gyors/greedy, 3–5 = pontosabb, lassabb). */
    fun beam(ctx: Context): Int = p(ctx).getInt("whisperBeam", 1).coerceIn(1, 5)
    fun setBeam(ctx: Context, v: Int) { p(ctx).edit().putInt("whisperBeam", v.coerceIn(1, 5)).apply() }
}
