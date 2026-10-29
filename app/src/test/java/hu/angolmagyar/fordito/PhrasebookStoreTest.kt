package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PhrasebookStoreTest {
    private lateinit var store: PhrasebookStore

    @Before fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("local_phrasebook_v1", 0).edit().clear().commit()
        store = PhrasebookStore(context)
    }

    @Test fun savesDeduplicatesAndDeletesEntries() {
        assertTrue(store.save("Good morning", "Jó reggelt"))
        assertFalse(store.save("Good morning", "Jó reggelt"))
        assertEquals(1, store.all().size)
        store.delete(store.all().first().id)
        assertTrue(store.all().isEmpty())
    }

    @Test fun reviewSchedulesAgainAndIncreasesIntervalWhenRemembered() {
        store.save("Good morning", "Jó reggelt")
        val entry = store.all().first()
        assertTrue(store.review(entry.id, true))
        val first = store.all().first()
        assertEquals(1, first.intervalDays)
        assertEquals(1, first.reviewCount)
        assertTrue(first.nextReviewAt > System.currentTimeMillis())
        assertTrue(store.due().isEmpty())
        assertTrue(store.review(entry.id, true))
        assertEquals(3, store.all().first().intervalDays)
    }

    @Test fun refusesBlankPhrasesAndClearsLocally() {
        assertFalse(store.save(" ", "Jó reggelt"))
        assertFalse(store.save("Hello", " "))
        store.save("Hello", "Szia")
        store.clear()
        assertEquals(0, store.all().size)
    }
}
