package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryPlanTest {
    private val mb = 1024L * 1024L

    @Test fun biggerHeapAllowsLongerChunks() {
        assertTrue(MemoryPlan.maxChunkMs(512 * mb) > MemoryPlan.maxChunkMs(128 * mb))
    }

    @Test fun alwaysWithinBounds() {
        for (h in listOf(0L, 16 * mb, 64 * mb, 256 * mb, 1024 * mb, 8192 * mb)) {
            val v = MemoryPlan.maxChunkMs(h)
            assertTrue(v in MemoryPlan.MIN_CHUNK_MS..MemoryPlan.MAX_CHUNK_MS)
        }
    }

    @Test fun moreWorkersMeansSmallerChunks() {
        assertTrue(MemoryPlan.maxChunkMs(512 * mb, workers = 4) <= MemoryPlan.maxChunkMs(512 * mb, workers = 1))
    }

    @Test fun typicalPhoneKeepsFiveMinuteChunks() {
        assertTrue(MemoryPlan.maxChunkMs(512 * mb) >= 5 * 60_000L)
    }

    @Test fun lowMemoryDetection() {
        assertTrue(MemoryPlan.isLowMemory(128 * mb))
        assertFalse(MemoryPlan.isLowMemory(512 * mb))
        assertFalse(MemoryPlan.isLowMemory(0))
        assertEquals(MemoryPlan.MIN_CHUNK_MS, MemoryPlan.maxChunkMs(-1))
    }
}
