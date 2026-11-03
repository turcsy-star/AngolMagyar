package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportTest {
    private val err = IllegalStateException("kaboom", RuntimeException("gyökérok"))

    @Test fun formatContainsEssentials() {
        val s = CrashReport.format("2026-11-03 12:00:00", "main", err, mapOf("Verzió" to "4.5.0"))
        assertTrue(s.startsWith(CrashReport.SEPARATOR))
        assertTrue(s.contains("Verzió: 4.5.0"))
        assertTrue(s.contains("IllegalStateException: kaboom"))
        assertTrue(s.contains("Oka: java.lang.RuntimeException: gyökérok"))
    }

    @Test fun appendKeepsNewestWithinLimit() {
        var log = ""
        for (i in 1..200) {
            log = CrashReport.append(log, CrashReport.format("t$i", "main", err), maxChars = 5_000)
        }
        assertTrue(log.length <= 5_000)
        assertTrue(log.contains("Idő: t200"))
        assertTrue(!log.contains("Idő: t1\n"))
        assertTrue(log.startsWith(CrashReport.SEPARATOR))
    }

    @Test fun countAndSummary() {
        val log = CrashReport.append(
            CrashReport.format("t1", "a", err),
            CrashReport.format("t2", "b", NullPointerException("x"))
        )
        assertEquals(2, CrashReport.entryCount(log))
        val sum = CrashReport.lastSummary(log)
        assertTrue(sum, sum.startsWith("t2"))
        assertTrue(sum, sum.contains("NullPointerException"))
        assertEquals(0, CrashReport.entryCount(""))
    }

    @Test fun deepStackIsTruncated() {
        fun deep(n: Int): Nothing = if (n == 0) throw RuntimeException("mély") else deep(n - 1)
        val t = try { deep(200) } catch (e: RuntimeException) { e }
        val s = CrashReport.stack(t)
        assertTrue(s.lines().size < 80)
        assertTrue(s.contains("még"))
    }
}
