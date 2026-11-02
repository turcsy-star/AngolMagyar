package hu.angolmagyar.fordito.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignAuditTest {
    @Test
    fun everyPaletteIsAudited() {
        assertEquals(LuxuryPalettes.all.size, DesignAudit.paletteRows().size)
    }

    @Test
    fun defaultSapphirePalettePasses() {
        val row = DesignAudit.paletteRows().first { it.id == "sapphire" }
        assertTrue("sapphire szöveg-kontraszt: ${row.textRatio}", row.textRatio >= 4.5)
        assertTrue("sapphire kiemelés-kontraszt: ${row.accentRatio}", row.accentRatio >= 3.0)
        assertTrue(row.passes)
    }

    @Test
    fun summaryMentionsCounts() {
        val rows = DesignAudit.paletteRows()
        assertTrue(DesignAudit.summaryLine(rows).startsWith("${DesignAudit.passedCount(rows)}/${rows.size}"))
    }
}
