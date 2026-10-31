package hu.angolmagyar.fordito

import org.junit.Assert.*
import org.junit.Test

class OfflineMemoryRankerTest {
    @Test fun ranksRelevantItemsFirst() {
        val items = listOf(
            OfflineMemoryRanker.Item("b", "The weather is sunny today", "Ma napos az idő", "general"),
            OfflineMemoryRanker.Item("a", "Open the checkpoint now", "Nyisd meg az ellenőrzőpontot", "hiking")
        )
        val ranked = OfflineMemoryRanker.rank("Open checkpoint", items)
        assertEquals("a", ranked.first().item.id)
    }

    @Test fun domainBoostBreaksCloseTie() {
        val items = listOf(
            OfflineMemoryRanker.Item("a", "open checkpoint", "A", "general"),
            OfflineMemoryRanker.Item("b", "open checkpoint", "B", "hiking")
        )
        assertEquals("b", OfflineMemoryRanker.rank("open checkpoint", items, "hiking").first().item.id)
    }

    @Test fun rejectsBlankAndNonPositiveLimit() {
        val item = OfflineMemoryRanker.Item("a", "hello world", "szia világ")
        assertTrue(OfflineMemoryRanker.rank("  ", listOf(item)).isEmpty())
        assertTrue(OfflineMemoryRanker.rank("hello", listOf(item), limit = 0).isEmpty())
    }
}
