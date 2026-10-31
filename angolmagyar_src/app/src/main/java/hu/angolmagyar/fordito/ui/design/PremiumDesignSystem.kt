package hu.angolmagyar.fordito.ui.design

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hu.angolmagyar.fordito.Prefs

/** Shared layout tokens for a gradual migration of legacy screens to Compose. */
data class PremiumSpacing(val xSmall: Dp = 4.dp, val small: Dp = 8.dp, val medium: Dp = 12.dp, val large: Dp = 20.dp, val xLarge: Dp = 28.dp)
data class PremiumMotionDurations(val shortMillis: Int = 160, val standardMillis: Int = 280, val longMillis: Int = 420)

val LocalSpacing = compositionLocalOf { PremiumSpacing() }
val LocalMotion = compositionLocalOf { PremiumMotionDurations() }

@Composable
fun ProvidePremiumDesignSystem(spacing: PremiumSpacing = PremiumSpacing(), motion: PremiumMotionDurations = PremiumMotionDurations(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSpacing provides spacing, LocalMotion provides motion, content = content)
}

@Composable
fun PremiumCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val luxuryPalette = remember(context) { LuxuryPalettes.byId(Prefs(context).luxuryThemeId) }
    val reducedMotion = remember(context) {
        Prefs(context).reduceMotion ||
            ((context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager)?.isPowerSaveMode == true)
    }
    val diamondTransition = rememberInfiniteTransition(label = "diamondCardShimmer")
    val shimmerPosition = if (reducedMotion) 0.5f else {
        val shimmer by diamondTransition.animateFloat(
            initialValue = -0.25f,
            targetValue = 1.25f,
            animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart),
            label = "diamondCardShimmerPosition"
        )
        shimmer
    }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val reveal by animateFloatAsState(
        targetValue = if (entered || reducedMotion) 1f else 0f,
        animationSpec = tween(if (reducedMotion) 0 else 360, easing = FastOutSlowInEasing),
        label = "premiumCardReveal"
    )
    Surface(
        modifier = modifier.graphicsLayer { alpha = reveal; translationY = (1f - reveal) * 12.dp.toPx() },
        shape = RoundedCornerShape(PremiumShapes.card),
        color = Color.Transparent,
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, Brush.linearGradient(
            colors = listOf(
                luxuryPalette.accent.copy(alpha = 0.30f),
                luxuryPalette.edge.copy(alpha = if (reducedMotion) 0.42f else 0.72f),
                colors.primary.copy(alpha = 0.24f),
                luxuryPalette.glow.copy(alpha = 0.38f),
                luxuryPalette.accent.copy(alpha = 0.30f)
            ),
            start = Offset(shimmerPosition * 900f, 0f),
            end = Offset(shimmerPosition * 900f + 360f, 560f)
        )),
        tonalElevation = 2.dp,
        shadowElevation = 3.dp
    ) {
        Box(
            Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            luxuryPalette.surface.copy(alpha = 0.98f),
                            luxuryPalette.background.copy(alpha = 0.99f),
                            luxuryPalette.accent.copy(alpha = 0.16f)
                        )
                    ),
                    RoundedCornerShape(PremiumShapes.card)
                )
                .padding(LocalSpacing.current.large)
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.075f),
                                Color.Transparent,
                                luxuryPalette.glow.copy(alpha = 0.035f)
                            )
                        ),
                        RoundedCornerShape(PremiumShapes.card)
                    )
            )
            if (!reducedMotion) {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color.Transparent, luxuryPalette.text.copy(alpha = 0.055f), Color.Transparent),
                                start = Offset(shimmerPosition * 1100f, 0f),
                                end = Offset(shimmerPosition * 1100f + 180f, 520f)
                            ),
                            RoundedCornerShape(PremiumShapes.card)
                        )
                )
            }
            Box { content() }
        }
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
