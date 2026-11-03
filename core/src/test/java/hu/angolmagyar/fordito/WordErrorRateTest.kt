package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Test

class WordErrorRateTest {
    @Test fun identicalTextIsZero() {
        assertEquals(0.0, WordErrorRate.compare("Hello, world!", "hello world").rate, 1e-9)
    }

    @Test fun oneSubstitutionOutOfFour() {
        val r = WordErrorRate.compare("a b c d", "a b x d")
        assertEquals(1, r.substitutions)
        assertEquals(0.25, r.rate, 1e-9)
    }

    @Test fun deletionAndInsertion() {
        assertEquals(1, WordErrorRate.compare("a b c", "a c").deletions)
        assertEquals(1, WordErrorRate.compare("a c", "a b c").insertions)
    }

    @Test fun emptyReferenceHandled() {
        assertEquals(0.0, WordErrorRate.compare("", "").rate, 1e-9)
        assertEquals(1.0, WordErrorRate.compare("", "x").rate, 1e-9)
    }

    @Test fun hungarianAccentsAreKept() {
        assertEquals(1.0 / 2, WordErrorRate.compare("árvíztűrő tükörfúrógép", "arvizturo tükörfúrógép").rate, 1e-9)
    }
}
