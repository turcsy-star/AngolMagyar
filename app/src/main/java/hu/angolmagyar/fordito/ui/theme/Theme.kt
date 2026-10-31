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

/**
 * A Compose témát ugyanazokból az erőforrás-színekből és ugyanabból a kiemelőszínből építi, mint a régi (View)
 * képernyők ([hu.angolmagyar.fordito.Ui]), így a kezdőlap és a többi képernyő egy stílusú.
 */
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
        // Sötét prémium mód: a kiválasztott Infinity paletta vezérli a teljes Compose Material 3 témát.
        darkColorScheme(
            primary = luxury.accent,
            secondary = luxury.glow,
            tertiary = luxury.edge,
            background = if (isTrueBlack) Color.Black else luxury.background,
            surface = if (isTrueBlack) Color(0xFF050505) else luxury.surface,
            surfaceVariant = if (isTrueBlack) Color(0xFF101010) else luxury.surface.copy(alpha = 0.92f),
            primaryContainer = luxury.accent.copy(alpha = 0.20f),
            onPrimaryContainer = luxury.text,
            onPrimary = Color(0xFF07130F),
            onSecondary = Color(0xFF07130F),
            onBackground = luxury.text,
            onSurface = luxury.text,
            onSurfaceVariant = luxury.text.copy(alpha = 0.72f),
            outline = luxury.edge.copy(alpha = 0.58f),
            error = Color(0xFFFF7777)
        )
    } else if (prefs.platinumTheme) {
        // Világos prémium mód: semleges, magas kontrasztú felületek; a választott paletta adja a kiemelést.
        val lightPrimary = if (prefs.luxuryThemeId == "forest") Color(0xFF286448) else Color(0xFF34536B)
        lightColorScheme(
            primary = lightPrimary,
            secondary = if (prefs.luxuryThemeId == "forest") Color(0xFF4E8066) else Color(0xFF587D9D),
            tertiary = if (prefs.luxuryThemeId == "forest") Color(0xFF7BA88D) else Color(0xFF7897B4),
            background = Color(0xFFF3F7F4),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFE8EFEA),
            primaryContainer = lightPrimary.copy(alpha = 0.12f),
            onPrimaryContainer = Color(0xFF183A29),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = Color(0xFF17231C),
            onSurface = Color(0xFF17231C),
            onSurfaceVariant = Color(0xFF4D5E53),
            outline = Color(0xFFB7C7BC),
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
