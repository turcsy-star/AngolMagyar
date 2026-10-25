package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleSuggestTest {
    @Test fun middleDifference() {
        assertEquals("kell" to "kellene", RuleSuggest.diff("Ezt meg kell csinálni", "Ezt meg kellene csinálni"))
    }
    @Test fun punctuationTrimmed() {
        assertEquals("pia" to "ital", RuleSuggest.diff("Hozz pia, kérlek", "Hozz ital, kérlek"))
    }
    @Test fun identicalOrEmptyIsNull() {
        assertNull(RuleSuggest.diff("a b", "a b"))
        assertNull(RuleSuggest.diff("a b c", "a b"))
    }
    @Test fun tooLongIsNull() {
        assertNull(RuleSuggest.diff("a b c d e f g", "x y z w v u t"))
    }
    @Test fun addDeduplicates() {
        val (t1, n1) = RuleSuggest.add("", "x", "y")
        assertTrue(n1); assertEquals("x=y", t1)
        val (t2, n2) = RuleSuggest.add(t1, "X", "y")
        assertFalse(n2); assertEquals(t1, t2)
        val (t3, _) = RuleSuggest.add(t1, "a", "b")
        assertEquals("x=y\na=b", t3)
    }
}
