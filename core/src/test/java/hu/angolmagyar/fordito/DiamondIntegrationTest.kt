package hu.angolmagyar.fordito

import org.junit.Assert.*
import org.junit.Test

class DiamondIntegrationTest {
    @Test
    fun checklistNotEmpty() {
        val items = DiamondIntegration.checklist()
        assertTrue(items.size >= 10)
        assertTrue(items.any { it.id == "hybrid" && it.done })
        assertTrue(items.any { it.id == "device_qa" && !it.done })
    }

    @Test
    fun evaluateWithRegression() {
        val gate = DiamondIntegration.evaluate(runRegression = true)
        assertEquals("3.00.0", gate.versionName)
        assertEquals(30000, gate.versionCode)
        assertNotNull(gate.regression)
        assertTrue(gate.regression!!.allPassed)
        assertTrue(gate.doneCount >= 8)
        assertTrue(gate.summaryLine().contains("Diamond"))
    }
}
