package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleStatsTest {
    @Test fun recordAndCandidates() {
        val m = RuleStats.parse("")
        RuleStats.record(m, "kell", "kellene")
        RuleStats.record(m, "kell", "kellene")
        RuleStats.record(m, "pia", "ital")
        assertEquals(listOf("kell=kellene"), RuleStats.candidates(m, ""))
        assertTrue(RuleStats.candidates(m, "Kell=Kellene").isEmpty())
    }
    @Test fun roundTrip() {
        val m = LinkedHashMap<String, Int>(); m["a=b"] = 3
        assertEquals(3, RuleStats.parse(RuleStats.serialize(m))["a=b"])
    }
    @Test fun capDropsRarest() {
        val m = LinkedHashMap<String, Int>()
        RuleStats.record(m, "a", "b", 2); RuleStats.record(m, "a", "b", 2); RuleStats.record(m, "c", "d", 2)
        RuleStats.record(m, "e", "f", 2)
        assertEquals(2, m.size)
        assertTrue(m.containsKey("a=b"))
    }
}
