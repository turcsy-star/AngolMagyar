package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPlanTest {
    private val w = intArrayOf(2, 8, 2, 86, 2)
    private val c = intArrayOf(1, 2, 3, 4, 5)

    @Test fun segmentsMirrorWeights() {
        val p = NotificationPlan.build(w, c, 50, 4)
        assertEquals(w.toList(), p.segments.map { it.length })
        assertEquals(c.toList(), p.segments.map { it.color })
        assertEquals(100, p.max)
        assertEquals(50, p.progress)
        assertEquals("50%", p.shortText)
    }

    @Test fun pointsAreChunkBoundariesInsideRecognition() {
        val p = NotificationPlan.build(w, c, 40, 4)
        assertEquals(3, p.points.size)
        assertTrue(p.points.all { it in 13..99 })
        assertEquals(p.points.sorted(), p.points)
    }

    @Test fun manyChunksAreThinnedToLimit() {
        val p = NotificationPlan.build(w, c, 40, 200)
        assertTrue(p.points.size <= NotificationPlan.MAX_POINTS)
        assertEquals(p.points.distinct(), p.points)
    }

    @Test fun noPointsForSingleOrUnknownChunk() {
        assertTrue(NotificationPlan.build(w, c, 40, 1).points.isEmpty())
        assertTrue(NotificationPlan.build(w, c, 40, 0).points.isEmpty())
    }

    @Test fun indeterminateAtStartAndClamped() {
        val a = NotificationPlan.build(w, c, 0, 0)
        assertTrue(a.indeterminate); assertEquals("", a.shortText)
        val b = NotificationPlan.build(w, c, 250, 0)
        assertEquals(100, b.progress); assertEquals("", b.shortText)
        assertEquals(0, NotificationPlan.build(w, c, -5, 0).progress)
    }

    @Test fun shortTextFitsTheSystemLimit() {
        for (o in 1..99) assertTrue(NotificationPlan.build(w, c, o, 3).shortText.length <= 7)
    }
}
