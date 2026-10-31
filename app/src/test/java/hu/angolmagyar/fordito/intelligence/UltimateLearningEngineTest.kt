package hu.angolmagyar.fordito.intelligence

import hu.angolmagyar.fordito.ultimate.UltimateLearningEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UltimateLearningEngineTest {
    @Test fun classifyLevelBoundaries() {
        assertEquals("A1", UltimateLearningEngine.classifyLevel(0))
        assertEquals("A2", UltimateLearningEngine.classifyLevel(20))
        assertEquals("B1", UltimateLearningEngine.classifyLevel(35))
        assertEquals("B2", UltimateLearningEngine.classifyLevel(50))
        assertEquals("C1", UltimateLearningEngine.classifyLevel(65))
        assertEquals("C2", UltimateLearningEngine.classifyLevel(100))
        assertEquals("C2", UltimateLearningEngine.classifyLevel(500))
        assertEquals("A1", UltimateLearningEngine.classifyLevel(-5))
    }

    @Test fun readingTimeRoundsUpAndBlankIsZero() {
        assertEquals(0, UltimateLearningEngine.estimateReadingMinutes(""))
        assertEquals(1, UltimateLearningEngine.estimateReadingMinutes("one two three"))
        assertEquals(2, UltimateLearningEngine.estimateReadingMinutes(List(201) { "word" }.joinToString(" ")))
    }

    @Test fun scenariosHaveHints() {
        assertTrue(UltimateLearningEngine.scenarios.all { it.hint.isNotBlank() && it.prompt.isNotBlank() })
    }
}
