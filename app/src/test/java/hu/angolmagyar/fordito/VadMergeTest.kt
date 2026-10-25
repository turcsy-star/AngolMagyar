package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Test

class VadMergeTest {
    private val sr = 16000
    private fun seg(a: Int, b: Int) = Vad.Seg(a * sr, b * sr)
    private val min = 120L * sr
    private val max = 300L * sr
    private val span = 15L * 60 * sr

    @Test fun tinyPiecesAreAbsorbed() {
        val fine = listOf(seg(0, 200), seg(300, 303), seg(600, 606), seg(900, 902))
        val pieces = listOf(seg(0, 210), seg(290, 310), seg(590, 615), seg(870, 890))
        val m = Vad.mergeSmallPieces(pieces, fine, min, max, span)
        assertEquals(1, m.size)
        assertEquals(0, m[0].start)
        assertEquals(890 * sr, m[0].end)
    }

    @Test fun twoBigPiecesStaySeparate() {
        val fine = listOf(seg(0, 200), seg(300, 500))
        val m = Vad.mergeSmallPieces(listOf(seg(0, 210), seg(290, 510)), fine, min, max, span)
        assertEquals(2, m.size)
    }

    @Test fun spanLimitStopsMerging() {
        val fine = listOf(seg(0, 5), seg(1000, 1005), seg(2000, 2005))
        val pieces = listOf(seg(0, 10), seg(995, 1010), seg(1995, 2010))
        val m = Vad.mergeSmallPieces(pieces, fine, min, max, 1100L * sr)
        assertEquals(2, m.size)  // 0..1010 még belefér az 1100 mp-es határba, 2010 már nem
        assertEquals(1010 * sr, m[0].end)
    }

    @Test fun speechLimitStopsMerging() {
        val fine = listOf(seg(0, 100), seg(150, 290), seg(300, 400))
        val m = Vad.mergeSmallPieces(listOf(seg(0, 110), seg(140, 295), seg(295, 410)), fine, min, 230L * sr, span)
        assertEquals(3, m.size)
    }
}
