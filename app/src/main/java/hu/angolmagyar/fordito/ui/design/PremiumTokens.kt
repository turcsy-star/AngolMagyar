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
    val platinum = Color(0xFFD8DEE8)
    val champagneGold = Color(0xFFD6B56D)
    val deepGraphite = Color(0xFF171A20)
    val pearl = Color(0xFFF6F3ED)
}
