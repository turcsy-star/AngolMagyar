package hu.angolmagyar.fordito.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared layout tokens for a gradual migration of legacy screens to Compose. */
data class PremiumSpacing(val xSmall: Dp = 4.dp, val small: Dp = 8.dp, val medium: Dp = 12.dp, val large: Dp = 20.dp, val xLarge: Dp = 28.dp)
data class PremiumMotion(val shortMillis: Int = 160, val standardMillis: Int = 280, val longMillis: Int = 420)

val LocalSpacing = compositionLocalOf { PremiumSpacing() }
val LocalMotion = compositionLocalOf { PremiumMotion() }

@Composable
fun ProvidePremiumDesignSystem(spacing: PremiumSpacing = PremiumSpacing(), motion: PremiumMotion = PremiumMotion(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSpacing provides spacing, LocalMotion provides motion, content = content)
}

@Composable
fun PremiumCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(PremiumShapes.card),
        color = colors.surfaceContainerLow,
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, colors.outline.copy(alpha = PremiumPalette.cardStrokeAlpha)),
        tonalElevation = 1.dp,
        shadowElevation = 1.dp
    ) {
        Box(Modifier.padding(LocalSpacing.current.large)) { content() }
    }
}

/** Small, consistent heading for grouped controls on premium screens. */
@Composable
fun PremiumSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    androidx.compose.foundation.layout.Column(modifier = modifier) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

@Composable
fun StatusPill(label: String, modifier: Modifier = Modifier, accent: Color = MaterialTheme.colorScheme.primary) {
    val spacing = LocalSpacing.current
    Surface(modifier = modifier.semantics { contentDescription = "Állapot: $label" }, color = accent.copy(alpha = 0.13f), contentColor = accent, shape = RoundedCornerShape(PremiumShapes.pill), tonalElevation = 1.dp) {
        Text(label, modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.xSmall), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun PrimaryAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit = {}) {
    val spacing = LocalSpacing.current
    Button(onClick = onClick, enabled = enabled, modifier = modifier, shape = RoundedCornerShape(PremiumShapes.button)) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.small), verticalAlignment = Alignment.CenterVertically) {
            content()
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
