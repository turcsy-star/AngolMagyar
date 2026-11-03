package hu.angolmagyar.fordito.ui.theme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import hu.angolmagyar.fordito.R
import hu.angolmagyar.fordito.Prefs
import hu.angolmagyar.fordito.ui.design.LuxuryPalettes
@Composable
fun AngolMagyarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isTrueBlack: Boolean = false,
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val ctx = LocalContext.current
    val prefs = Prefs(ctx)
    val luxury = LuxuryPalettes.byId(prefs.luxuryThemeId)
    val primary = Color(0xFF60A5FA)
    val glow = Color(0xFF22D3EE)
    val bg = colorResource(R.color.bg)
    val card = colorResource(R.color.card)
    val textP = colorResource(R.color.textPrimary)
    val textS = colorResource(R.color.textSecondary)
    val stroke = colorResource(R.color.stroke)
    val err = colorResource(R.color.err)
    val scheme = if (prefs.platinumTheme && darkTheme) {
        // A kiválasztott paletta a teljes Compose-felületet vezérli; az OLED mód csak a felületeket sötétíti.
        darkColorScheme(
            primary = luxury.accent,
            secondary = luxury.glow,
            tertiary = luxury.edge,
            background = if (isTrueBlack) Color.Black else luxury.background,
            surface = if (isTrueBlack) Color(0xFF05070B) else luxury.surface,
            surfaceVariant = if (isTrueBlack) Color(0xFF10141B) else luxury.surface.copy(alpha = 0.96f),
            primaryContainer = luxury.accent.copy(alpha = 0.20f),
            onPrimaryContainer = luxury.text,
            onPrimary = Color(0xFF06111B),
            onSecondary = Color(0xFF06111B),
            onBackground = luxury.text,
            onSurface = luxury.text,
            onSurfaceVariant = luxury.text.copy(alpha = 0.76f),
            outline = luxury.edge.copy(alpha = 0.58f),
            error = Color(0xFFFF7777)
        )
    } else if (prefs.platinumTheme) {
        // Világos módban is megmarad a választott neon/cyber akcentus, de a felületek olvashatóak maradnak.
        val lightPrimary = when (prefs.luxuryThemeId) {
            "forest", "matrix" -> Color(0xFF176B4B)
            "violet", "neon" -> Color(0xFF5140A8)
            "cyber", "arctic" -> Color(0xFF176B82)
            "gold" -> Color(0xFF80601D)
            else -> Color(0xFF245B91)
        }
        lightColorScheme(
            primary = lightPrimary,
            secondary = lightPrimary.copy(red = (lightPrimary.red * 0.78f).coerceIn(0f, 1f), green = (lightPrimary.green * 0.92f).coerceIn(0f, 1f)),
            tertiary = luxury.glow,
            background = Color(0xFFF4F7FC),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFEAF0F8),
            primaryContainer = lightPrimary.copy(alpha = 0.12f),
            onPrimaryContainer = Color(0xFF14243B),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = Color(0xFF101B2B),
            onSurface = Color(0xFF101B2B),
            onSurfaceVariant = Color(0xFF4D5D73),
            outline = Color(0xFFB8C7D9),
            error = Color(0xFFB3261E)
        )
    } else if (darkTheme) {
        darkColorScheme(
            primary = primary, secondary = glow, tertiary = glow,
            background = if (isTrueBlack) Color.Black else bg,
            surface = if (isTrueBlack) Color(0xFF0A0A0A) else card,
            surfaceVariant = if (isTrueBlack) Color(0xFF111111) else card,
            primaryContainer = primary.copy(alpha = 0.22f),
            onPrimaryContainer = textP,
            onPrimary = Color.White, onSecondary = Color.White,
            onBackground = textP, onSurface = textP, onSurfaceVariant = textS,
            outline = stroke, error = err
        )
    } else {
        lightColorScheme(
            primary = primary, secondary = glow, tertiary = glow,
            background = bg, surface = card, surfaceVariant = card,
            primaryContainer = primary.copy(alpha = 0.14f),
            onPrimaryContainer = textP,
            onPrimary = Color.White, onSecondary = Color.White,
            onBackground = textP, onSurface = textP, onSurfaceVariant = textS,
            outline = stroke, error = err
        )
    }
    MaterialTheme(colorScheme = scheme, typography = Typography, content = content)
}
