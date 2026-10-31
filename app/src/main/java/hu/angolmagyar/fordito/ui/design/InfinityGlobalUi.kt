package hu.angolmagyar.fordito.ui.design

import androidx.compose.ui.graphics.Color

/**
 * Infinity Global UI – v3.00.0
 *
 * Központi design tokenek és paletta-migrációs segéd.
 * A klasszikus View → Compose teljes migrációja folyamatos.
 */
object InfinityGlobalUi {

    enum class MigrationStatus { NOT_STARTED, PARTIAL, COMPLETE }

    data class Palette(
        val name: String,
        val accent: Color,
        val accentDim: Color,
        val surface: Color,
        val surfaceElevated: Color,
        val onSurface: Color,
        val onSurfaceVariant: Color,
        val success: Color,
        val warning: Color,
        val error: Color
    )

    val PlatinumDark = Palette(
        name = "Platinum Dark",
        accent = Color(0xFFB8C0CC),
        accentDim = Color(0xFF8A929E),
        surface = Color(0xFF0E1116),
        surfaceElevated = Color(0xFF1A1F28),
        onSurface = Color(0xFFE8ECF2),
        onSurfaceVariant = Color(0xFF9AA3B2),
        success = Color(0xFF5CDB95),
        warning = Color(0xFFE8B86D),
        error = Color(0xFFE07A7A)
    )

    val DiamondBlue = Palette(
        name = "Diamond Blue",
        accent = Color(0xFF6EB5FF),
        accentDim = Color(0xFF4A8FD4),
        surface = Color(0xFF0A0F18),
        surfaceElevated = Color(0xFF121A28),
        onSurface = Color(0xFFE6F0FF),
        onSurfaceVariant = Color(0xFF8BA3C4),
        success = Color(0xFF5CDB95),
        warning = Color(0xFFE8B86D),
        error = Color(0xFFE07A7A)
    )

    val migrationStatus: MigrationStatus = MigrationStatus.PARTIAL

    val migrationChecklist: List<Pair<String, Boolean>> = listOf(
        "Színsémák központi tokenekbe szervezve" to true,
        "PremiumCard / PremiumMotion egységes használata" to true,
        "Klasszikus View képernyők teljes Compose-migrációja" to false,
        "Egységes navigációs átmenetek (NavTransitions)" to true,
        "Akadálymentességi audit (kontraszt, focus)" to false,
        "Dark / OLED / csökkentett mozgás protokoll" to true,
        "Intelligence hub + Global Dashboard Compose" to true,
        "Diamond kiadási kapu (checklist + regresszió)" to true
    )

    fun completedCount(): Int = migrationChecklist.count { it.second }
    fun totalCount(): Int = migrationChecklist.size
    fun progressLabel(): String = "${completedCount()}/${totalCount()} · ${migrationStatus.name}"
}
