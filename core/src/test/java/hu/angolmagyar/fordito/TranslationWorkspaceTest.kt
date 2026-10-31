package hu.angolmagyar.fordito

import org.junit.Assert.*
import org.junit.Test

class TranslationWorkspaceTest {
    private fun variant(id: String, translation: String, parent: String? = null) = TranslationWorkspace.Variant(
        id = id, source = "Hello world", translation = translation,
        style = TranslationWorkspace.Style.NATURAL, providerId = "offline", parentId = parent
    )

    @Test fun revisionStoreRejectsDuplicateAndUnknownParent() {
        val store = TranslationWorkspace.RevisionStore()
        assertTrue(store.append(variant("v1", "Szia világ")))
        assertFalse(store.append(variant("v1", "Helló világ")))
        assertFalse(store.append(variant("v2", "Helló világ", "missing")))
        assertEquals(1, store.list("hello   world").variants.size)
    }

    @Test fun restoreCreatesNewRevisionAndPreservesOriginal() {
        val store = TranslationWorkspace.RevisionStore()
        store.append(variant("v1", "Szia világ"))
        store.append(variant("v2", "Helló világ", "v1"))
        val restored = store.restore("Hello world", "v1", "v3")
        assertNotNull(restored)
        assertEquals("Szia világ", restored?.translation)
        assertEquals("v1", restored?.parentId)
        assertEquals(listOf("v1", "v2", "v3"), store.list("Hello world").variants.map { it.id })
    }

    @Test fun historyIsBoundedWithoutChangingNewestRevision() {
        val store = TranslationWorkspace.RevisionStore(maxRevisionsPerSource = 2)
        store.append(variant("v1", "Egy"))
        store.append(variant("v2", "Kettő", "v1"))
        store.append(variant("v3", "Három", "v2"))
        assertEquals(listOf("v2", "v3"), store.list("Hello world").variants.map { it.id })
    }

    @Test fun diffReconstructsBothSides() {
        val parts = TranslationWorkspace.diff("Hello old world", "Hello new world!")
        assertEquals("Hello old world", parts.filter { it.kind != TranslationWorkspace.DiffPart.Kind.ADDED }.joinToString("") { it.text })
        assertEquals("Hello new world!", parts.filter { it.kind != TranslationWorkspace.DiffPart.Kind.REMOVED }.joinToString("") { it.text })
        assertTrue(parts.any { it.kind == TranslationWorkspace.DiffPart.Kind.REMOVED })
        assertTrue(parts.any { it.kind == TranslationWorkspace.DiffPart.Kind.ADDED })
    }

    @Test fun oversizedDiffUsesBoundedFallback() {
        val before = (1..401).joinToString(" ") { "a$it" }
        val after = (1..401).joinToString(" ") { "b$it" }
        val parts = TranslationWorkspace.diff(before, after)
        assertEquals(2, parts.size)
        assertEquals(TranslationWorkspace.DiffPart.Kind.REMOVED, parts[0].kind)
        assertEquals(TranslationWorkspace.DiffPart.Kind.ADDED, parts[1].kind)
    }
}
