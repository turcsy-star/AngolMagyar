package hu.angolmagyar.fordito
import android.content.Context
object SrtBuild {
    fun build(ctx: Context, cues: List<SrtExporter.Cue>, mode: SrtExporter.Mode): String {
        var work = cues
        if (SubPrefs.consistency(ctx)) work = TerminologyConsistency.apply(work).cues
        if (SubPrefs.dialogueDash(ctx)) work = DialogueDash.apply(work)
        val fin = if (SubPrefs.reflow(ctx)) SubFormat.polish(work) else work
        val colors = if (SubPrefs.speakerColors(ctx)) SubFormat.speakerColors(fin) else null
        return SrtExporter.build(fin, mode, colors)
    }
}
