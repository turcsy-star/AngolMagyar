package hu.angolmagyar.fordito.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PronunciationAssessmentTest {
    @Test fun identicalTranscriptIsPerfect() {
        assertEquals(100, PronunciationAssessment.assess("Hello world", "hello world").scorePercent)
    }

    @Test fun errorsReduceScore() {
        assertTrue(PronunciationAssessment.assess("hello world", "hello").scorePercent < 100)
    }

    @Test fun countsSubstitutionDeletionAndInsertion() {
        val sub = PronunciationAssessment.assess("one two three", "one four three")
        assertEquals(1, sub.substitutions); assertEquals(0, sub.deletions); assertEquals(0, sub.insertions)
        val del = PronunciationAssessment.assess("one two three", "one three")
        assertEquals(1, del.deletions)
        val ins = PronunciationAssessment.assess("one three", "one two three")
        assertEquals(1, ins.insertions)
    }

    @Test fun curlyApostropheEqualsStraightApostrophe() {
        assertEquals(100, PronunciationAssessment.assess("I don't know", "I don’t know").scorePercent)
    }

    @Test fun emptyRecognitionIsAllDeletions() {
        val r = PronunciationAssessment.assess("good morning", "")
        assertEquals(2, r.deletions); assertEquals(0, r.scorePercent)
    }

    @Test fun emptyReferenceNeverDivides() {
        val r = PronunciationAssessment.assess("", "hello")
        assertEquals(0, r.scorePercent); assertEquals(1, r.insertions)
    }
}
