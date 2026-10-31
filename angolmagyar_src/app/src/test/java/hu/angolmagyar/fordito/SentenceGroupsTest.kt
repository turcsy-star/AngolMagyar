package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceGroupsTest {
    private fun item(s: Long, e: Long, t: String) = SentenceGroups.Item(s, e, t)

    @Test fun groupsEndAtSentenceEnd() {
        val g = SentenceGroups.group(listOf(
            item(0, 1000, "I was going to"), item(1000, 2000, "the store."), item(2100, 3000, "Then I left.")
        ))
        assertEquals(listOf(0..1, 2..2), g)
    }

    @Test fun falseWhisperStopsAreBridged() {
        assertTrue(SentenceGroups.falseStop("Oh, so what are you."))
        assertTrue(SentenceGroups.falseStop("do you have to be like that, you know? I."))
        assertTrue(SentenceGroups.falseStop("Top of everything else and the."))
        assertTrue(!SentenceGroups.falseStop("I love taking care of you."))
        assertTrue(!SentenceGroups.falseStop("Okay."))
        val g = SentenceGroups.group(listOf(
            item(0, 5000, "Oh, so what are you."), item(5100, 8000, "Doing later? Do you need help?"), item(9000, 10000, "Okay.")
        ))
        assertEquals(listOf(0..1, 2..2), g)
    }

    @Test fun groupsBreakOnLongGap() {
        val g = SentenceGroups.group(listOf(item(0, 1000, "one two"), item(9000, 10000, "three four")))
        assertEquals(2, g.size)
    }

    @Test fun groupsCoverEveryItemExactlyOnce() {
        val items = (0 until 25).map { item(it * 1000L, it * 1000L + 900, "part $it") }
        val g = SentenceGroups.group(items)
        assertEquals((0 until 25).toList(), g.flatMap { it.toList() })
        assertTrue(g.all { it.count() <= 6 })
    }

    @Test fun distributeKeepsAllWordsInOrder() {
        val target = "Mentem a boltba, aztán hazamentem és aludtam egyet"
        val parts = SentenceGroups.distribute(target, listOf(14, 10, 12))
        assertEquals(3, parts.size)
        assertEquals(target, parts.joinToString(" ").trim().replace(Regex("\\s+"), " "))
    }

    @Test fun distributePrefersSentenceEndOverDanglingComma() {
        val target = "Ó, ez, ez nem jó. Ó, mit csinálsz később? Szükséged van bármilyen segítségre?"
        val parts = SentenceGroups.distribute(target, listOf(78, 60))
        assertEquals(listOf("Ó, ez, ez nem jó. Ó, mit csinálsz később?", "Szükséged van bármilyen segítségre?"), parts)
    }

    @Test fun distributeNeverLeavesAPartEmptyWhenWordsAreEnough() {
        val parts = SentenceGroups.distribute("Egy kettő három négy", listOf(1, 1, 100, 1))
        assertEquals(4, parts.size)
        assertTrue(parts.all { it.isNotBlank() })
        assertEquals("Egy kettő három négy", parts.joinToString(" "))
    }

    @Test fun distributeSingleAndEmpty() {
        assertEquals(listOf("Szia világ"), SentenceGroups.distribute("Szia világ", listOf(10)))
        assertEquals(listOf("", ""), SentenceGroups.distribute("", listOf(5, 5)))
        assertTrue(SentenceGroups.distribute("x", emptyList()).isEmpty())
    }

    @Test fun fragmentWithLowercaseContinuationBridgesLongerGap() {
        val g = SentenceGroups.group(listOf(item(0, 1000, "And then we went to"), item(3500, 4500, "the old house.")))
        assertEquals(listOf(0..1), g)
    }

    @Test fun capitalizedNextBlockAfterLongGapStillBreaks() {
        val g = SentenceGroups.group(listOf(item(0, 1000, "And then we went to"), item(3500, 4500, "The house.")))
        assertEquals(2, g.size)
    }

    @Test fun ellipsisWithLowercaseContinuationIsOneSentence() {
        val g = SentenceGroups.group(listOf(item(0, 1000, "I thought…"), item(1100, 2000, "maybe we could go."), item(2100, 3000, "Sure.")))
        assertEquals(listOf(0..1, 2..2), g)
    }
}
