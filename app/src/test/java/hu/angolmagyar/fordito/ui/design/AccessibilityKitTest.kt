package hu.angolmagyar.fordito.ui.design

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityKitTest {
    @Test
    fun blackOnWhitePassesNormalTextContrast() {
        assertTrue(AccessibilityKit.passesNormalTextContrast(Color.Black, Color.White))
    }

    @Test
    fun lightGrayOnWhiteFailsNormalTextContrast() {
        assertFalse(AccessibilityKit.passesNormalTextContrast(Color(0xFFCCCCCC), Color.White))
    }

    @Test
    fun contrastRatioIsSymmetricEnough() {
        val a = AccessibilityKit.contrastRatio(Color.Black, Color.White)
        val b = AccessibilityKit.contrastRatio(Color.White, Color.Black)
        assertTrue(kotlin.math.abs(a - b) < 0.01)
        assertTrue(a > 20.0)
    }
}
