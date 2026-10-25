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
