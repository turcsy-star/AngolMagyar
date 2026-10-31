package hu.angolmagyar.fordito.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Shared visual tokens. Components use MaterialTheme for adaptive contrast and these tokens for geometry. */
object PremiumShapes {
    val card = 24.dp
    val button = 18.dp
    val pill = 50.dp
    val compactCard = 18.dp
}

object PremiumPalette {
    /** Subtle outline opacity keeps cards distinct without a heavy metallic border. */
    const val cardStrokeAlpha = 0.22f
    val electricBlue = Color(0xFF60A5FA)
    val cyanGlow = Color(0xFF22D3EE)
    val deepNavy = Color(0xFF070B16)
    val ice = Color(0xFFEEF5FF)
}
