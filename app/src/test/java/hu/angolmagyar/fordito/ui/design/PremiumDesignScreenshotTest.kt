package hu.angolmagyar.fordito.ui.design

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Renders and captures the shared premium Compose surface during the JVM test suite. */
@RunWith(RobolectricTestRunner::class)
class PremiumDesignScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun premiumCardRendersAndCapturesScreenshot() {
        composeRule.setContent {
            ProvidePremiumDesignSystem {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    PremiumCard {
                        Column {
                            StatusPill("Készen áll")
                            Text("Angol–Magyar", style = MaterialTheme.typography.headlineSmall)
                            Text("Platina • Gyémánt • Silver & Gold")
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(filePath = "build/outputs/roborazzi/PremiumDesignScreenshotTest_premiumCardRendersAndCapturesScreenshot.png")
    }
}
