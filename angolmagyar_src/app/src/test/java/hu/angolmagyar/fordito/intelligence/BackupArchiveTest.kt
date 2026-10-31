package hu.angolmagyar.fordito.intelligence

import hu.angolmagyar.fordito.core.db.DictionaryEntity
import hu.angolmagyar.fordito.core.db.ReviewScheduleEntity
import hu.angolmagyar.fordito.core.db.TranslationEntity
import org.junit.Assert.*
import org.junit.Test

class BackupArchiveTest {
    @Test fun roundTripPreservesAllLearningRecords() {
        val source = LearningBackup(
            createdAt = 1234L,
            dictionary = listOf(DictionaryEntity(7, "hello", "hello", "szia", "Hello!", 99)),
            translations = listOf(TranslationEntity(8, "good morning", "Good morning", "Jó reggelt", "greeting", 3, 100)),
            reviews = listOf(ReviewScheduleEntity("hello", 2, 4, 2.3, 500, 200))
        )
        val restored = BackupArchive.decode(BackupArchive.encode(source))
        assertEquals(source, restored)
    }

    @Test fun rejectsUnknownFormat() {
        try {
            BackupArchive.decode("{\"format\":\"other\",\"version\":1}")
            fail("Unknown backup format must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("Ismeretlen"))
        }
    }

    @Test fun rejectsUnsupportedVersion() {
        try {
            BackupArchive.decode("{\"format\":\"angolmagyar-infinity-backup\",\"version\":99}")
            fail("Unsupported version must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("verzió"))
        }
    }
}
