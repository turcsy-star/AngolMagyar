package hu.angolmagyar.fordito.ui.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PremiumTranslationCardTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun showsBothTextsAndEnablesActionsForNonBlankValues() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                PremiumTranslationCard(
                    sourceLabel = "Angol",
                    targetLabel = "Magyar",
                    sourceText = "Good morning",
                    translatedText = "Jó reggelt",
                    onCopySource = {},
                    onCopyTranslation = {},
                    onSpeakTranslation = {}
                )
            }
        }
        composeRule.onNodeWithText("Good morning").assertExists()
        composeRule.onNodeWithText("Jó reggelt").assertExists()
        composeRule.onNodeWithText("Forrás másolása").assertIsEnabled()
        composeRule.onNodeWithText("Fordítás másolása").assertIsEnabled()
        composeRule.onNodeWithText("Felolvasás").assertIsEnabled()
    }

    @Test fun showsSaveActionWhenConfigured() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                PremiumTranslationCard(
                    sourceLabel = "Angol", targetLabel = "Magyar",
                    sourceText = "Thank you", translatedText = "Köszönöm",
                    onCopySource = {}, onCopyTranslation = {}, onSaveTranslation = {}
                )
            }
        }
        composeRule.onNodeWithText("Mentés").assertExists().assertIsEnabled()
    }

    @Test fun disablesActionsWhenTextIsBlank() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                PremiumTranslationCard(
                    sourceLabel = "Angol",
                    targetLabel = "Magyar",
                    sourceText = "",
                    translatedText = "",
                    onCopySource = {},
                    onCopyTranslation = {}
                )
            }
        }
        composeRule.onNodeWithText("Forrás másolása").assertIsNotEnabled()
        composeRule.onNodeWithText("Fordítás másolása").assertIsNotEnabled()
    }
}
