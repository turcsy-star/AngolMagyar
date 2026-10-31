package hu.angolmagyar.fordito

import org.junit.Assert.*
import org.junit.Test

class RetranslationPlannerTest {
    @Test fun previewNeverAppliesChangesByItself() {
        val segment = RetranslationPlanner.Segment("s1", "Hello", "Szia")
        val preview = RetranslationPlanner.preview(listOf(segment), mapOf("s1" to "Helló"), RetranslationPlanner.Reason.STYLE_CHANGED)
        assertEquals(1, preview.changedCount)
        assertTrue(preview.requiresExplicitApproval)
        assertEquals("Szia", segment.currentTranslation)
    }

    @Test fun manuallyApprovedSegmentsAreProtectedByDefault() {
        val segment = RetranslationPlanner.Segment("s1", "Hello", "Kézzel jóváhagyott", manuallyApproved = true)
        val preview = RetranslationPlanner.preview(listOf(segment), mapOf("s1" to "Helló"), RetranslationPlanner.Reason.GLOSSARY_CHANGED)
        assertTrue(preview.changes.isEmpty())
        assertEquals(listOf("s1"), preview.skippedManualSegments)
    }

    @Test fun onlySelectedIdsAreApplied() {
        val a = RetranslationPlanner.Segment("a", "One", "Egy")
        val b = RetranslationPlanner.Segment("b", "Two", "Kettő")
        val preview = RetranslationPlanner.preview(listOf(a, b), mapOf("a" to "Egyik", "b" to "Kettes"), RetranslationPlanner.Reason.MANUAL_REQUEST)
        val result = RetranslationPlanner.applyApproved(listOf(a, b), preview, setOf("b"))
        assertEquals("Egy", result[0].currentTranslation)
        assertEquals("Kettes", result[1].currentTranslation)
        assertEquals(2, result[1].translationRevision)
    }

    @Test fun stalePreviewCannotOverwriteNewerEdit() {
        val old = RetranslationPlanner.Segment("a", "One", "Egy", translationRevision = 2)
        val preview = RetranslationPlanner.preview(listOf(old), mapOf("a" to "Első"), RetranslationPlanner.Reason.PROVIDER_CHANGED)
        val changedSincePreview = old.copy(currentTranslation = "Saját javítás", translationRevision = 3)
        val result = RetranslationPlanner.applyApproved(listOf(changedSincePreview), preview, setOf("a"))
        assertEquals("Saját javítás", result.single().currentTranslation)
    }

    @Test fun blankAndUnchangedCandidatesAreIgnored() {
        val a = RetranslationPlanner.Segment("a", "One", "Egy")
        val preview = RetranslationPlanner.preview(listOf(a), mapOf("a" to "   "), RetranslationPlanner.Reason.GLOSSARY_CHANGED)
        assertTrue(preview.isEmpty)
    }
}
