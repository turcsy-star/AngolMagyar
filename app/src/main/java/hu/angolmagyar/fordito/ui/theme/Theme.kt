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
            surfaceContainerLowest = if (isTrueBlack) Color(0xFF000000) else luxury.background,
            surfaceContainerLow = if (isTrueBlack) Color(0xFF080B10) else luxury.surface,
            surfaceContainer = if (isTrueBlack) Color(0xFF0D1118) else luxury.surface.copy(alpha = 0.98f),
            surfaceContainerHigh = if (isTrueBlack) Color(0xFF121821) else luxury.surface.copy(alpha = 0.92f),
            surfaceContainerHighest = if (isTrueBlack) Color(0xFF171F2A) else luxury.surface.copy(alpha = 0.86f),
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
        // A világos változat is a kiválasztott kilenc paletta akcentusából épül fel.
        // A sötétítés biztosítja a megfelelő kontrasztot fehér felületen is.
        val lightPrimary = luxury.accent.copy(
            red = (luxury.accent.red * 0.68f).coerceIn(0f, 1f),
            green = (luxury.accent.green * 0.68f).coerceIn(0f, 1f),
            blue = (luxury.accent.blue * 0.68f).coerceIn(0f, 1f)
        )
        val lightSecondary = luxury.glow.copy(
            red = (luxury.glow.red * 0.72f).coerceIn(0f, 1f),
            green = (luxury.glow.green * 0.72f).coerceIn(0f, 1f),
            blue = (luxury.glow.blue * 0.72f).coerceIn(0f, 1f)
        )
        lightColorScheme(
            primary = lightPrimary,
            secondary = lightSecondary,
            tertiary = luxury.edge.copy(
                red = (luxury.edge.red * 0.72f).coerceIn(0f, 1f),
                green = (luxury.edge.green * 0.72f).coerceIn(0f, 1f),
                blue = (luxury.edge.blue * 0.72f).coerceIn(0f, 1f)
            ),
            background = Color(0xFFF4F7FC),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFEAF0F8),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF7F9FC),
            surfaceContainer = Color(0xFFF1F5FA),
            surfaceContainerHigh = Color(0xFFEAF0F8),
            surfaceContainerHighest = Color(0xFFE2EAF4),
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
