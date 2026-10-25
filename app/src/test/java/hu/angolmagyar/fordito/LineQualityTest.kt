package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LineQualityTest {
    private fun c(s: Long, e: Long, en: String, hu: String, lp: Float = 1f) = SrtExporter.Cue(s, e, en, hu, "", lp)

    @Test fun cleanLineHasNoFlags() {
        val cue = c(0, 3000, "Are you feeling better?", "Jobban érzed magad?")
        assertTrue(LineQuality.flags(null, cue, null, true).isEmpty() || LineQuality.flags(null, cue, null, true) == listOf("magányos").filter { false })
    }

    @Test fun formalAndUntranslatedAndLowConf() {
        val f = LineQuality.flags(null, c(0, 3000, "Please help me with this thing", "Segítsen nekem ebben", -1.4f), c(4000, 6000, "x", "y"), true)
        assertTrue(f.contains("magázó alak"))
        assertTrue(f.contains("bizonytalan felismerés"))
        val g = LineQuality.flags(null, c(0, 3000, "Hello there friend", "Hello there friend"), null, true)
        assertTrue(g.contains("fordítatlan"))
    }

    @Test fun repeatFragmentAndRatio() {
        val a = c(0, 2000, "Oh yes indeed", "Ó, igen valóban")
        val b = c(2100, 4000, "Oh yes indeed", "Ó, igen valóban")
        assertTrue(LineQuality.flags(a, b, null, true).contains("ismétlődés"))
        val frag = c(5000, 5800, "And then,", "És aztán,")
        assertTrue(LineQuality.flags(null, frag, c(5900, 8000, "we left", "elmentünk"), true).contains("töredék"))
        val long = c(0, 9000, "Come here right now please", "Gyere ide azonnal kérlek ez egy nagyon hosszú és felesleges mondat lett belőle, sokkal hosszabb mint kellene")
        assertTrue(LineQuality.flags(null, long, null, true).contains("hossz-arány"))
    }

    @Test fun isolatedLine() {
        val a = c(10_000, 12_000, "Bye.", "Szia.")
        val b = c(400_000, 402_000, "Oh yes.", "Ó, igen.")
        val d = c(900_000, 902_000, "Hmm.", "Hm.")
        assertEquals(true, LineQuality.flags(a, b, d, true).contains("magányos"))
        // az első blokk is magányos, ha a következő csak percekkel később jön; ha közel van, nem az
        val near = c(14_000, 16_000, "Ok.", "Oké.")
        assertEquals(false, LineQuality.flags(null, a, near, true).contains("magányos"))
    }

    @Test fun sourceOnlyModeIgnoresTranslationFlags() {
        val f = LineQuality.flags(null, c(0, 3000, "Hello there friend", ""), null, false)
        assertTrue(!f.contains("fordítatlan") && !f.contains("üres"))
    }
}
