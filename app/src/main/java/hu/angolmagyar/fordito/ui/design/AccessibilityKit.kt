package hu.angolmagyar.fordito.ui.design
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hu.angolmagyar.fordito.Prefs
import kotlin.math.max
object AccessibilityKit {
    val MinTouchTarget: Dp = 48.dp
    data class Snapshot(
        val fontScale: Float,
        val largestText: Boolean,
        val reducedMotion: Boolean,
        val touchExplorationEnabled: Boolean,
        val highContrastPreferred: Boolean,
        val oledBlack: Boolean,
        val nightMode: Boolean
    )
    fun snapshot(context: Context): Snapshot {
        val cfg = context.resources.configuration
        val fontScale = cfg.fontScale
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val prefs = Prefs(context)
        val night = (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return Snapshot(
            fontScale = fontScale,
            largestText = fontScale >= 1.3f,
            reducedMotion = PremiumMotion.reduced(context),
            touchExplorationEnabled = am?.isTouchExplorationEnabled == true,
            highContrastPreferred = prefs.highContrast || fontScale >= 1.3f,
            oledBlack = prefs.oledBlack,
            nightMode = night
        )
    }
    fun contrastRatio(foreground: Color, background: Color): Double {
        val l1 = max(foreground.luminance().toDouble(), background.luminance().toDouble())
        val l2 = kotlin.math.min(foreground.luminance().toDouble(), background.luminance().toDouble())
        return (l1 + 0.05) / (l2 + 0.05)
    }
    fun passesNormalTextContrast(foreground: Color, background: Color): Boolean =
        contrastRatio(foreground, background) >= 4.5
    fun passesLargeTextContrast(foreground: Color, background: Color): Boolean =
        contrastRatio(foreground, background) >= 3.0
    fun checklist(context: Context): List<String> {
        val s = snapshot(context)
        return buildList {
            add("Betűméret-skála: ${"%.2f".format(s.fontScale)}${if (s.largestText) " (nagy)" else ""}")
            add("Csökkentett mozgás: ${if (s.reducedMotion) "be" else "ki"}")
            add("TalkBack / felfedezés: ${if (s.touchExplorationEnabled) "aktív" else "inaktív"}")
            add("OLED fekete: ${if (s.oledBlack) "be" else "ki"}")
            add("Éjszakai mód: ${if (s.nightMode) "igen" else "nem"}")
            add("Min. érintési cél: ${MinTouchTarget.value.toInt()} dp")
            if (Build.VERSION.SDK_INT >= 29) {
                add("Rendszer gesztusok / navigáció: API ${Build.VERSION.SDK_INT}")
            }
        }
    }
}
@Composable
fun Modifier.accessibleTouchTarget(minSize: Dp = AccessibilityKit.MinTouchTarget): Modifier {
    return this.sizeIn(minWidth = minSize, minHeight = minSize)
}
@Composable
fun rememberAccessibilitySnapshot(): AccessibilityKit.Snapshot {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(configuration.fontScale, configuration.uiMode) {
        AccessibilityKit.snapshot(context)
    }
}
fun Modifier.accessibleDescription(text: String?): Modifier {
    if (text.isNullOrBlank()) return this
    return this.semantics { contentDescription = text }
}
