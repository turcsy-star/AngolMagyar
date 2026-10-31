package hu.angolmagyar.fordito

import org.junit.Assert.*
import org.junit.Test

class ArchiveSafetyPolicyTest {
    @Test fun acceptsNormalArchiveEntries() {
        assertNull(ArchiveSafetyPolicy.validate(listOf(
            ArchiveSafetyPolicy.EntryMetadata("model/am/final.mdl", 1024, 900),
            ArchiveSafetyPolicy.EntryMetadata("model/conf/model.conf", 128, 100)
        )))
    }

    @Test fun rejectsTraversalAndAbsolutePaths() {
        listOf("../escape", "model/../../escape", "/absolute", "C:/drive", "model\\escape").forEach { path ->
            assertFalse(path, ArchiveSafetyPolicy.isSafeRelativePath(path))
        }
    }

    @Test fun rejectsOversizedAndExcessiveArchives() {
        val limits = ArchiveSafetyPolicy.Limits(maxEntries = 1, maxTotalBytes = 100, maxEntryBytes = 100)
        assertEquals(ArchiveSafetyPolicy.Rejection.TOO_MANY_ENTRIES, ArchiveSafetyPolicy.validate(listOf(
            ArchiveSafetyPolicy.EntryMetadata("a", 1, 1), ArchiveSafetyPolicy.EntryMetadata("b", 1, 1)
        ), limits))
        assertEquals(ArchiveSafetyPolicy.Rejection.TOO_LARGE, ArchiveSafetyPolicy.validate(listOf(
            ArchiveSafetyPolicy.EntryMetadata("a", 101, 100)
        ), limits))
    }

    @Test fun rejectsSuspiciousCompressionRatio() {
        assertEquals(ArchiveSafetyPolicy.Rejection.ZIP_BOMB, ArchiveSafetyPolicy.validate(listOf(
            ArchiveSafetyPolicy.EntryMetadata("model/data", 1_000_000, 1)
        )))
    }
}
