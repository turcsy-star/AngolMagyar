package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpellClusterTest {
    @Test fun rareVariantMapsToFrequent() {
        val t = listOf(
            "Hey Nina, come here.", "Thanks, Nina, really.", "Oh Nina, wait.", "You know, Nina, sure.",
            "Then Nino, left."
        )
        val m = SpellCluster.find(t)
        assertEquals("Nina", m["Nino"])
        assertEquals("Hey Nina", SpellCluster.apply("Hey Nino", m))
    }

    @Test fun equalFrequencyNamesAreKept() {
        val t = listOf("Hey Jake, go.", "Hi, Jake.", "Yo Jake, ok.", "Hey Jack, go.", "Hi, Jack.", "Yo Jack, ok.")
        assertTrue(SpellCluster.find(t).isEmpty())
    }
}
