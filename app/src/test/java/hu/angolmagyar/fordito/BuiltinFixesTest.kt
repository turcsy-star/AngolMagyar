package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Test

class BuiltinFixesTest {
    private fun f(s: String) = BuiltinFixes.target(s)

    @Test fun stepSiblingsBecomeMostoha() {
        assertEquals("Te vagy a legjobb mostohatestvér, akivel valaha is tudtam.", f("Te vagy a legjobb lépcső vagy testvér, akivel valaha is tudtam."))
        assertEquals("A mostohaanyja jött.", f("A lépcsőanyja jött."))
        assertEquals("A mostohaanyja jött.", f("A mostoha anyja jött."))
    }

    @Test fun careKitMistranslationIsFixed() {
        assertEquals("Csomag itt.", f("Hair csomag itt."))
    }

    @Test fun ordinaryTextIsUntouched() {
        assertEquals("A lépcső szép.", f("A lépcső szép."))
        assertEquals("Hozz egy csomagot.", f("Hozz egy csomagot."))
    }
}
