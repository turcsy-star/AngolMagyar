package hu.angolmagyar.fordito

import android.content.Context

/** Az SRT végső összeállítása a beállítások szerint (a feldolgozó szolgáltatás és a szerkesztő is ezt használja). */
object SrtBuild {
    fun build(ctx: Context, cues: List<SrtExporter.Cue>, mode: SrtExporter.Mode): String {
        val fin = if (SubPrefs.reflow(ctx)) SubFormat.polish(cues) else cues
        val colors = if (SubPrefs.speakerColors(ctx)) SubFormat.speakerColors(fin) else null
        return SrtExporter.build(fin, mode, colors)
    }
}
