package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Test

class InformalTest {
    private fun f(s: String) = TextPolish.informalHungarian(s)

    @Test fun imperativesBecomeInformal() {
        assertEquals("Segíts nekem.", f("Segítsen nekem."))
        assertEquals("Hozz nekem egy könyvet.", f("Hozzon nekem egy könyvet."))
        assertEquals("Olvasd el nekem.", f("Olvassa el nekem."))
        assertEquals("Zárd be az ajtót.", f("Zárja be az ajtót."))
        assertEquals("Kérlek, segíts.", f("Kérem, segítsen."))
    }

    @Test fun formalQuestionsBecomeInformal() {
        assertEquals("Tudsz olvasni egy történetet?", f("Tud olvasni egy történetet?"))
        assertEquals("Szükséged van segítségre?", f("Szüksége van segítségre?"))
        assertEquals("Remélem, jobban érzed magad.", f("Remélem, jobban érzi magát."))
        assertEquals("Jobban érzed magad?", f("Jobban érzi magát?"))
    }

    @Test fun pronounsAreFixed() {
        assertEquals("Téged keresnek.", f("Önt keresnek."))
        assertEquals("Ez neked szól.", f("Ez az Ön számára szól."))
    }

    @Test fun thirdPersonAndStatementsAreUntouched() {
        assertEquals("Ő olvassa a könyvet.", f("Ő olvassa a könyvet."))
        assertEquals("Mit akar a bálna?", f("Mit akar a bálna?"))
        assertEquals("Moby Dick a kedvenc történetem.", f("Moby Dick a kedvenc történetem."))
        assertEquals("Kérem a számlát.", f("Kérem a számlát."))
        assertEquals("A tudás hatalom.", f("A tudás hatalom."))
        assertEquals("Ő tud úszni.", f("Ő tud úszni."))
    }
}
