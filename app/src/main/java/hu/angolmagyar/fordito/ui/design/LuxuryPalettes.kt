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
    // A 4.6.0 Ultimate készlet az elsődleges; a korábbi azonosítók kompatibilitás miatt megmaradnak.
    val ultimate = listOf(
        LuxuryPalette("bronze", "Bronze", Color(0xFF17100B), Color(0xFF2A1C12), Color(0xFFC58A52), Color(0xFF8D5A35), Color(0xFFE4B98A), Color(0xFFFFF4E8)),
        LuxuryPalette("silver", "Silver", Color(0xFF0B1018), Color(0xFF1B2532), Color(0xFFBFCBDA), Color(0xFF7894B2), Color(0xFFE7F0FA), Color(0xFFF5F8FC)),
        LuxuryPalette("gold", "Gold", Color(0xFF120E08), Color(0xFF261D10), Color(0xFFE0BD70), Color(0xFFAA813A), Color(0xFFF5DDA6), Color(0xFFFFF8E9)),
        LuxuryPalette("premium", "Premium", Color(0xFF08121B), Color(0xFF122536), Color(0xFF69B9D8), Color(0xFF3A86A8), Color(0xFFB5E8F8), Color(0xFFF1FAFF)),
        LuxuryPalette("ultra", "Ultra", Color(0xFF100B20), Color(0xFF211735), Color(0xFFAA91FF), Color(0xFF7658D6), Color(0xFFE0D5FF), Color(0xFFF8F4FF)),
        LuxuryPalette("diamond", "Diamond", Color(0xFF07131B), Color(0xFF102630), Color(0xFF8BE5F5), Color(0xFF49B6D0), Color(0xFFD1F8FF), Color(0xFFF1FDFF)),
        LuxuryPalette("ultimate", "Ultimate Neo", Color(0xFF070B14), Color(0xFF111B2B), Color(0xFF6CD7F5), Color(0xFF987BFF), Color(0xFFB9EFFF), Color(0xFFF4F8FF)),
        LuxuryPalette("aurora", "Aurora", Color(0xFF08151A), Color(0xFF10272B), Color(0xFF6EE7B7), Color(0xFF60A5FA), Color(0xFFB8F7E2), Color(0xFFF0FFF9)),
        LuxuryPalette("cyber", "Cyber", Color(0xFF061218), Color(0xFF10252D), Color(0xFF38E0D0), Color(0xFF4F8DFF), Color(0xFF9CF8F0), Color(0xFFEFFFFF))
    )

    // Korábbi témák: a már mentett beállítások és régebbi képernyők miatt nem töröljük őket.
    private val legacy = listOf(
        LuxuryPalette("forest", "Forest Graphite", Color(0xFF07130F), Color(0xFF12231B), Color(0xFF62A982), Color(0xFF2F7657), Color(0xFFB7E0C7), Color(0xFFF0F8F2)),
        LuxuryPalette("platinum", "Platinum Silver", Color(0xFF080B10), Color(0xFF171D26), Color(0xFFC8D0DA), Color(0xFF8EA6C0), Color(0xFFE6EDF5), Color(0xFFF3F6FA)),
        LuxuryPalette("obsidian", "Obsidian Black", Color(0xFF030405), Color(0xFF111315), Color(0xFFB6BEC8), Color(0xFF737F8C), Color(0xFFD7DEE7), Color(0xFFF5F6F8)),
        LuxuryPalette("arctic", "Arctic Diamond", Color(0xFF071018), Color(0xFF10232E), Color(0xFF8ADCF2), Color(0xFF4FAFCB), Color(0xFFD5F7FF), Color(0xFFF0FBFF)),
        LuxuryPalette("sapphire", "Midnight Sapphire", Color(0xFF050A18), Color(0xFF0C1830), Color(0xFF78B8FF), Color(0xFF53E7FF), Color(0xFFB9E8FF), Color(0xFFF1F8FF)),
        LuxuryPalette("neon", "Neon Pulse", Color(0xFF08091B), Color(0xFF151632), Color(0xFF8B7CFF), Color(0xFF36D9FF), Color(0xFFC6C0FF), Color(0xFFF4F2FF)),
        LuxuryPalette("violet", "Cosmic Violet", Color(0xFF10091D), Color(0xFF211431), Color(0xFFB18CFF), Color(0xFFEC79D8), Color(0xFFE3C9FF), Color(0xFFFFF3FF)),
        LuxuryPalette("matrix", "Matrix Emerald", Color(0xFF04130F), Color(0xFF0D251D), Color(0xFF48D597), Color(0xFF39C9B0), Color(0xFFB0FFE0), Color(0xFFEFFFF7))
    )

    val all = ultimate + legacy
    fun byId(id: String): LuxuryPalette = all.firstOrNull { it.id == id } ?: ultimate.first()
}
