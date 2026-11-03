package hu.angolmagyar.fordito.ui.design
object DesignAudit {
    data class PaletteRow(
        val id: String,
        val title: String,
        val textRatio: Double,
        val accentRatio: Double
    ) {
        val passes: Boolean get() = textRatio >= 4.5 && accentRatio >= 3.0
    }
    fun paletteRows(): List<PaletteRow> = LuxuryPalettes.all.map { p ->
        PaletteRow(
            id = p.id,
            title = p.title,
            textRatio = AccessibilityKit.contrastRatio(p.text, p.background),
            accentRatio = AccessibilityKit.contrastRatio(p.accent, p.surface)
        )
    }
    fun passedCount(rows: List<PaletteRow> = paletteRows()): Int = rows.count { it.passes }
    fun summaryLine(rows: List<PaletteRow> = paletteRows()): String =
        "${passedCount(rows)}/${rows.size} paletta megfelel (szöveg ≥ 4,5:1, kiemelés ≥ 3:1)"
}
