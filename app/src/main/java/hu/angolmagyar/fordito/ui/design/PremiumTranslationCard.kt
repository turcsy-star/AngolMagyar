package hu.angolmagyar.fordito.ui.design

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Reusable, accessible source/translation card for translation and transcript screens.
 * Platform actions (clipboard and text-to-speech) are injected by the caller so this
 * presentation component does not own Android services or leak Activity references.
 */
@Composable
fun PremiumTranslationCard(
    sourceLabel: String,
    targetLabel: String,
    sourceText: String,
    translatedText: String,
    onCopySource: () -> Unit,
    onCopyTranslation: () -> Unit,
    modifier: Modifier = Modifier,
    onSpeakTranslation: (() -> Unit)? = null,
    onSaveTranslation: (() -> Unit)? = null
) {
    PremiumCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TranslationTextBlock(label = sourceLabel, value = sourceText, emptyText = "Nincs megadva forrásszöveg.")
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TranslationTextBlock(label = targetLabel, value = translatedText, emptyText = "A fordítás itt jelenik meg.")
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCopySource, enabled = sourceText.isNotBlank()) { Text("Forrás másolása") }
                TextButton(onClick = onCopyTranslation, enabled = translatedText.isNotBlank()) { Text("Fordítás másolása") }
                if (onSpeakTranslation != null) {
                    TextButton(onClick = onSpeakTranslation, enabled = translatedText.isNotBlank()) { Text("Felolvasás") }
                }
                if (onSaveTranslation != null) {
                    TextButton(onClick = onSaveTranslation, enabled = sourceText.isNotBlank() && translatedText.isNotBlank()) { Text("Mentés") }
                }
            }
        }
    }
}

@Composable
private fun TranslationTextBlock(label: String, value: String, emptyText: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value.ifBlank { emptyText },
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "$label: ${value.ifBlank { emptyText }}"
                    if (label.equals("Magyar", ignoreCase = true)) liveRegion = LiveRegionMode.Polite
                }
                .padding(vertical = 2.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
        )
    }
}
