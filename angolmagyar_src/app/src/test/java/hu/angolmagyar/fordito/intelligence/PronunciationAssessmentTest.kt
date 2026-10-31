package hu.angolmagyar.fordito.intelligence
import org.junit.Assert.*
import org.junit.Test
class PronunciationAssessmentTest {
 @Test fun identicalTranscriptIsPerfect() { assertEquals(100, PronunciationAssessment.assess("Hello world", "hello world").scorePercent) }
 @Test fun errorsReduceScore() { assertTrue(PronunciationAssessment.assess("hello world", "hello").scorePercent < 100) }
}
