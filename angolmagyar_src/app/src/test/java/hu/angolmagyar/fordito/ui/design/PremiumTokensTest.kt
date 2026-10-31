package hu.angolmagyar.fordito.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the shared visual tokens against accidental regressions. */
class PremiumTokensTest {
    @Test fun premiumGeometryRemainsConsistent() {
        assertEquals(24, PremiumShapes.card.value.toInt())
        assertEquals(18, PremiumShapes.button.value.toInt())
        assertEquals(18, PremiumShapes.compactCard.value.toInt())
        assertTrue(PremiumPalette.cardStrokeAlpha in 0f..1f)
    }

    @Test fun spacingAndMotionTokensArePositive() {
        val spacing = PremiumSpacing()
        val motion = PremiumMotion()
        assertTrue(spacing.xSmall.value > 0f)
        assertTrue(spacing.small >= spacing.xSmall)
        assertTrue(spacing.large >= spacing.medium)
        assertTrue(motion.shortMillis > 0)
        assertTrue(motion.standardMillis >= motion.shortMillis)
        assertTrue(motion.longMillis >= motion.standardMillis)
    }
}
