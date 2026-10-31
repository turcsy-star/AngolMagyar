package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CompactTest {
    private val r = 16000

    @Test fun removesSilenceAndMapsTimesBack() {
        val slice = ShortArray(10 * r) { 1000 }
        val start = 100 * r
        val fine = listOf(Vad.Seg(start + 1 * r, start + 2 * r), Vad.Seg(start + 8 * r, start + 9 * r))
        val c = Compact.build(slice, start, fine)
        assertNotNull(c)
        // 1,5 s + 0,3 s szünet + 1,5 s
        assertEquals((3.3 * r).toInt(), c!!.first.size)
        assertEquals(100_750L, c.second.toOrig(0))
        assertEquals(107_950L, c.second.toOrig(2000))
    }

    @Test fun noGainReturnsNull() {
        val slice = ShortArray(10 * r) { 1000 }
        val fine = listOf(Vad.Seg(0, 10 * r))
        assertNull(Compact.build(slice, 0, fine))
    }
}
