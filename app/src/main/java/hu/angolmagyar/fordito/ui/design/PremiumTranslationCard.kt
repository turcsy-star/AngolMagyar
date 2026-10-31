package hu.angolmagyar.fordito.ui.design

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
 *
 * Optional intelligence layer (P2/P3):
 * - memorySuggestion / onAcceptMemory: show approved TM suggestion; never auto-applied.
 * - qualityLabels: deterministic offline flags (numbers, negation, names).
 * - contextNote: short ambiguity / tone note from SmartContextTranslation.
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
    onSaveTranslation: (() -> Unit)? = null,
    memorySuggestion: String? = null,
    onAcceptMemory: (() -> Unit)? = null,
    qualityLabels: List<String> = emptyList(),
    contextNote: String? = null
) {
    PremiumCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TranslationTextBlock(label = sourceLabel, value = sourceText, emptyText = "Nincs megadva forrásszöveg.")
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TranslationTextBlock(label = targetLabel, value = translatedText, emptyText = "A fordítás itt jelenik meg.")

            if (!memorySuggestion.isNullOrBlank() || qualityLabels.isNotEmpty() || !contextNote.isNullOrBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!memorySuggestion.isNullOrBlank()) {
                        Text(
                            text = "Memóriajavaslat (jóváhagyott)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = memorySuggestion,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.semantics {
                                contentDescription = "Memóriajavaslat: $memorySuggestion"
                                liveRegion = LiveRegionMode.Polite
                            }
                        )
                        if (onAcceptMemory != null) {
                            TextButton(onClick = onAcceptMemory) {
                                Text("Javaslat elfogadása")
                            }
                        }
                    }
                    if (qualityLabels.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            qualityLabels.forEach { label ->
                                AssistChip(
                                    onClick = {},
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        labelColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                )
                            }
                        }
                    }
                    if (!contextNote.isNullOrBlank()) {
                        Text(
                            text = contextNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onCopySource, enabled = sourceText.isNotBlank()) { Text("Forrás másolása") }
                TextButton(onClick = onCopyTranslation, enabled = translatedText.isNotBlank()) { Text("Fordítás másolása") }
                if (onSpeakTranslation != null) {
                    TextButton(onClick = onSpeakTranslation, enabled = translatedText.isNotBlank()) { Text("Felolvasás") }
                }
                if (onSaveTranslation != null) {
                    TextButton(onClick = onSaveTranslation, enabled = translatedText.isNotBlank()) { Text("Mentés") }
                }
            }
        }
    }
}

@Composable
private fun TranslationTextBlock(label: String, value: String, emptyText: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value.ifBlank { emptyText },
            style = MaterialTheme.typography.bodyLarge,
            color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = if (value.isBlank()) emptyText else "$label: $value"
                    liveRegion = LiveRegionMode.Polite
                }
        )
    }
}
