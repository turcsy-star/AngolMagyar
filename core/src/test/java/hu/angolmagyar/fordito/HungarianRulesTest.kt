package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Test

class HungarianRulesTest {
    private fun fix(s: String) = HungarianRules.apply(s)

    @Test fun articleBeforeVowel() {
        assertEquals("Az alma az asztalon van.", fix("A alma a asztalon van."))
        assertEquals("Megláttam az ördögöt.", fix("Megláttam a ördögöt."))
        assertEquals("Ő az újságíró.", fix("Ő a újságíró."))
    }

    @Test fun articleBeforeConsonantUntouched() {
        assertEquals("A ház nagy.", fix("A ház nagy."))
        assertEquals("Az nem igaz.", fix("Az nem igaz."))
    }

    @Test fun articleBeforeAcronymUntouched() {
        assertEquals("a EU döntése", fix("a EU döntése"))
    }

    @Test fun straightQuotes() {
        assertEquals("Azt mondta: „gyere ide”.", fix("Azt mondta: \"gyere ide\"."))
    }

    @Test fun curlyQuotes() {
        assertEquals("Ő „nem” jön.", fix("Ő “nem” jön."))
    }

    @Test fun oddQuotesAndHtmlLeftAlone() {
        assertEquals("Ő azt mondta: \"jó", fix("Ő azt mondta: \"jó"))
        assertEquals("<font color=\"#fff\">Szia</font>", fix("<font color=\"#fff\">Szia</font>"))
    }

    @Test fun thousandsSeparator() {
        assertEquals("1 000 000 dollár", fix("1,000,000 dollár"))
        assertEquals("3,5 kg", fix("3,5 kg"))
    }

    @Test fun decimalDotBeforeUnit() {
        assertEquals("3,5 %", fix("3.5 %"))
        assertEquals("2,5 kg", fix("2.5kg"))
        assertEquals("Verzió 3.5 megjelent", fix("Verzió 3.5 megjelent"))
    }

    @Test fun unitSpacing() {
        assertEquals("Az 5 kg nehéz.", fix("Az 5kg nehéz."))
    }

    @Test fun missingSpaceAfterPunctuation() {
        assertEquals("Nos, rendben! Jó? Igen.", fix("Nos,rendben!Jó?Igen."))
        assertEquals("3,5 és 4,25", fix("3,5 és 4,25"))
    }

    @Test fun monthsAndDaysLowercaseMidSentence() {
        assertEquals("Jövő héten, kedden találkozunk szeptember végén.", fix("Jövő héten, Kedden találkozunk Szeptember végén."))
        assertEquals("Hétfő van.", fix("Hétfő van."))
        assertEquals("Ott volt Péntek Júlia.", fix("Ott volt Péntek Júlia."))
    }

    @Test fun blankAndIdempotent() {
        assertEquals("", fix(""))
        val once = fix("A alma \"jó\", 1,000,000 db.")
        assertEquals(once, fix(once))
    }
}
