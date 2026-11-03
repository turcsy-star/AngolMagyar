package hu.angolmagyar.fordito.ui.design
import androidx.compose.ui.graphics.Color
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
        LuxuryPalette("forest", "Forest Graphite", Color(0xFF07130F), Color(0xFF12231B), Color(0xFF62A982), Color(0xFF2F7657), Color(0xFFB7E0C7), Color(0xFFF0F8F2)),
        LuxuryPalette("platinum", "Platinum Silver", Color(0xFF080B10), Color(0xFF171D26), Color(0xFFC8D0DA), Color(0xFF8EA6C0), Color(0xFFE6EDF5), Color(0xFFF3F6FA)),
        LuxuryPalette("obsidian", "Obsidian Black", Color(0xFF030405), Color(0xFF111315), Color(0xFFB6BEC8), Color(0xFF737F8C), Color(0xFFD7DEE7), Color(0xFFF5F6F8)),
        LuxuryPalette("gold", "Royal Gold", Color(0xFF100D08), Color(0xFF21190F), Color(0xFFD6B36A), Color(0xFF9F7A36), Color(0xFFF0D79A), Color(0xFFFFF7E7)),
        LuxuryPalette("arctic", "Arctic Diamond", Color(0xFF071018), Color(0xFF10232E), Color(0xFF8ADCF2), Color(0xFF4FAFCB), Color(0xFFD5F7FF), Color(0xFFF0FBFF)),
        LuxuryPalette("sapphire", "Midnight Sapphire", Color(0xFF050A18), Color(0xFF0C1830), Color(0xFF78B8FF), Color(0xFF53E7FF), Color(0xFFB9E8FF), Color(0xFFF1F8FF)),
        LuxuryPalette("neon", "Neon Pulse", Color(0xFF08091B), Color(0xFF151632), Color(0xFF8B7CFF), Color(0xFF36D9FF), Color(0xFFC6C0FF), Color(0xFFF4F2FF)),
        LuxuryPalette("cyber", "Cyber Horizon", Color(0xFF06131A), Color(0xFF102630), Color(0xFF35C9D0), Color(0xFF4D8DFF), Color(0xFF9BF5F0), Color(0xFFEFFFFF)),
        LuxuryPalette("violet", "Cosmic Violet", Color(0xFF10091D), Color(0xFF211431), Color(0xFFB18CFF), Color(0xFFEC79D8), Color(0xFFE3C9FF), Color(0xFFFFF3FF)),
        LuxuryPalette("matrix", "Matrix Emerald", Color(0xFF04130F), Color(0xFF0D251D), Color(0xFF48D597), Color(0xFF39C9B0), Color(0xFFB0FFE0), Color(0xFFEFFFF7))
    )
    fun byId(id: String): LuxuryPalette = all.firstOrNull { it.id == id } ?: all.first()
}
