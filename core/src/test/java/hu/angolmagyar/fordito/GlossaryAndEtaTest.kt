package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlossaryAndEtaTest {
    @Test fun mergeAddsOnlyNewLinesCaseInsensitive() {
        val out = GlossaryMerge.merge("step=mostoha\nHello=Szia", "hello=szia\nBye=Viszlát\nbye=viszlát\n\n")
        assertEquals("step=mostoha\nHello=Szia\nBye=Viszlát", out)
    }

    @Test fun mergeIntoEmptyAndNoChange() {
        assertEquals("a=b", GlossaryMerge.merge("", "a=b"))
        assertEquals("a=b", GlossaryMerge.merge("a=b", "A=B"))
    }

    @Test fun etaFirstValueIsTakenAsIs() {
        assertEquals(60_000L, EtaSmoother().smooth("k", 60_000, 1_000))
    }

    @Test fun etaCountsDownAndBlendsSlowly() {
        val e = EtaSmoother()
        e.smooth("k", 60_000, 0)
        val v = e.smooth("k", 90_000, 1_000) // előrejelzés 59 000, új becslés 90 000
        assertTrue("v=$v", v in 59_000L..64_000L)
    }

    @Test fun etaSameTimestampIsStable() {
        val e = EtaSmoother()
        val a = e.smooth("k", 10_000, 5)
        assertEquals(a, e.smooth("k", 99_999, 5))
    }

    @Test fun etaResetsToNewEstimateWhenPredictionExpired() {
        val e = EtaSmoother()
        e.smooth("k", 1_000, 0)
        assertEquals(30_000L, e.smooth("k", 30_000, 5_000))
    }
}
