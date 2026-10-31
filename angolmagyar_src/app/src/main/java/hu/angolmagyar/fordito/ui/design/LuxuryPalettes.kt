package hu.angolmagyar.fordito.ui.design

import androidx.compose.ui.graphics.Color

/** App-wide premium palettes. Values are deliberately restrained to preserve text contrast. */
data class LuxuryPalette(
    val id: String,
    val title: String,
    val background: Color,
    val surface: Color,
    val accent: Color,
    val glow: Color,
    val edge: Color,
    val text: Color
)

object LuxuryPalettes {
    val all = listOf(
        LuxuryPalette("platinum", "Platinum Silver", Color(0xFF080B10), Color(0xFF171D26), Color(0xFFC8D0DA), Color(0xFF8EA6C0), Color(0xFFE6EDF5), Color(0xFFF3F6FA)),
        LuxuryPalette("obsidian", "Obsidian Black", Color(0xFF030405), Color(0xFF111315), Color(0xFFB6BEC8), Color(0xFF737F8C), Color(0xFFD7DEE7), Color(0xFFF5F6F8)),
        LuxuryPalette("gold", "Royal Gold", Color(0xFF100D08), Color(0xFF21190F), Color(0xFFD6B36A), Color(0xFF9F7A36), Color(0xFFF0D79A), Color(0xFFFFF7E7)),
        LuxuryPalette("arctic", "Arctic Diamond", Color(0xFF071018), Color(0xFF10232E), Color(0xFF8ADCF2), Color(0xFF4FAFCB), Color(0xFFD5F7FF), Color(0xFFF0FBFF)),
        LuxuryPalette("sapphire", "Midnight Sapphire", Color(0xFF050A18), Color(0xFF0C1830), Color(0xFF78B8FF), Color(0xFF53E7FF), Color(0xFFB9E8FF), Color(0xFFF1F8FF))
    )
    fun byId(id: String): LuxuryPalette = all.firstOrNull { it.id == id } ?: all.first()
}
