package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationCheckTest {
    @Test fun goodTranslationHasNoFlags() {
        assertEquals(emptyList<String>(),
            TranslationCheck.check("I paid 1,200 euros to Nina in London.", "1200 eurót fizettem Ninának Londonban."))
        assertEquals(emptyList<String>(),
            TranslationCheck.check("He did not come.", "Ő nem jött el."))
    }

    @Test fun numbersAreNormalized() {
        assertEquals(listOf("1200", "35"), TranslationCheck.numbers("1,200 and 3.5"))
        assertEquals(listOf("1200", "35"), TranslationCheck.numbers("1 200 és 3,5"))
    }

    @Test fun missingNumberIsFlagged() {
        assertTrue(TranslationCheck.check("We paid 250 dollars.", "Fizettünk dollárt.").contains("szám eltér"))
        assertTrue(TranslationCheck.check("The year was 1999 and 2004.", "Az év 1999 volt.").contains("szám eltér"))
        // kis szám szóval kiírva: nem riasztunk
        assertTrue(!TranslationCheck.check("We met 25 people.", "Huszonöt emberrel találkoztunk.").contains("szám eltér"))
    }

    @Test fun missingNameOrAcronymIsFlagged() {
        assertTrue(TranslationCheck.check("Ask John about it.", "Kérdezd meg erről.").contains("név hiányzik"))
        assertTrue(TranslationCheck.check("The NASA team won.", "A csapat nyert.").contains("név hiányzik"))
        assertTrue(!TranslationCheck.check("The NASA team won.", "A NASA csapata nyert.").contains("név hiányzik"))
    }

    @Test fun sentenceStartAndWeekdaysAreNotNames() {
        assertTrue(!TranslationCheck.check("Monday we left. Then it rained.", "Hétfőn elindultunk. Aztán esett.").contains("név hiányzik"))
        assertTrue(TranslationCheck.properNouns("Then we saw Anna and Bob in Paris.").containsAll(listOf("Anna", "Bob", "Paris")))
    }

    @Test fun negationLostIsFlagged() {
        assertTrue(TranslationCheck.check("He did not come.", "Ő eljött.").contains("tagadás eltér"))
        assertTrue(TranslationCheck.check("I don't know.", "Tudom.").contains("tagadás eltér"))
        assertTrue(!TranslationCheck.check("I don't know.", "Nem tudom.").contains("tagadás eltér"))
        assertTrue(!TranslationCheck.check("There is no way.", "Nincs rá mód.").contains("tagadás eltér"))
    }

    @Test fun lineQualityUsesIt() {
        val c = SrtExporter.Cue(0, 4000, "He did not come to London", "Ő eljött", "", 1f)
        val f = LineQuality.flags(null, c, null, true)
        assertTrue(f.contains("tagadás eltér"))
        assertTrue(f.contains("név hiányzik"))
    }
}
