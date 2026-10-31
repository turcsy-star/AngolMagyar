package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordErrorRateTest {
    @Test fun punctuationAndCaseDoNotCountAsErrors() {
        val r = WordErrorRate.compare("Hello, WORLD!", "hello world")
        assertEquals(0, r.errors)
        assertEquals(0.0, r.rate, 0.0)
    }

    @Test fun countsSubstitutionDeletionAndInsertion() {
        val sub = WordErrorRate.compare("egy kettő három", "egy négy három")
        assertEquals(1, sub.substitutions)
        val del = WordErrorRate.compare("egy kettő három", "egy három")
        assertEquals(1, del.deletions)
        val ins = WordErrorRate.compare("egy három", "egy kettő három")
        assertEquals(1, ins.insertions)
    }

    @Test fun hungarianAccentsAreMeaningful() {
        val r = WordErrorRate.compare("kerek", "kérek")
        assertEquals(1, r.substitutions)
        assertTrue(r.rate > 0.0)
    }
}
