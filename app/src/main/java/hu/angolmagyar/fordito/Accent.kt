package hu.angolmagyar.fordito

import android.content.Context

/**
 * Választható kiemelőszín-témák. Minden régi (View) és új (Compose) képernyő ugyanezt használja,
 * így az egész app egységes marad. (világos / sötét változat)
 */
object Accent {
    data class Theme(val id: String, val name: String, val light: Int, val dark: Int, val glowLight: Int, val glowDark: Int)

    val ALL = listOf(
        Theme("indigo", "Indigó", 0xFF4F46E5.toInt(), 0xFF6366F1.toInt(), 0xFF6366F1.toInt(), 0xFF818CF8.toInt()),
        Theme("emerald", "Smaragd", 0xFF047857.toInt(), 0xFF10B981.toInt(), 0xFF10B981.toInt(), 0xFF34D399.toInt()),
        Theme("orange", "Narancs", 0xFFC2410C.toInt(), 0xFFF97316.toInt(), 0xFFF97316.toInt(), 0xFFFB923C.toInt()),
        Theme("rose", "Rózsa", 0xFFBE185D.toInt(), 0xFFEC4899.toInt(), 0xFFEC4899.toInt(), 0xFFF472B6.toInt()),
        Theme("sky", "Ég", 0xFF0369A1.toInt(), 0xFF0EA5E9.toInt(), 0xFF0EA5E9.toInt(), 0xFF38BDF8.toInt()),
        // Gyémánt: hideg türkiz főszín, kékes-lila fény; a meglévő felületeken is egységesen működik.
        Theme("diamond", "Gyémánt", 0xFF087E8B.toInt(), 0xFF22D3EE.toInt(), 0xFF4F46E5.toInt(), 0xFFA78BFA.toInt()),
        // Új visszafogott prémium paletta: platina felülethez illő, hűvös ezüst-kék hangsúly.
        Theme("platinum", "Platina", 0xFF475569.toInt(), 0xFFCBD5E1.toInt(), 0xFF64748B.toInt(), 0xFFE2E8F0.toInt()),
        // Silver & Gold luxuspaletta: elegáns pezsgőarany főszín és platina-ezüst ragyogás.
        Theme("luxury", "Silver & Gold", 0xFF8A6418.toInt(), 0xFFE6C875.toInt(), 0xFF64748B.toInt(), 0xFFDDE5EF.toInt()),
        // Kombinált Platina–Gyémánt: hűvös platina alapszín, kristályos cián fény és mély indigó kontraszt.
        Theme("platinum_diamond", "Platina Gyémánt", 0xFF526579.toInt(), 0xFFB9E7F2.toInt(), 0xFF0891B2.toInt(), 0xFF818CF8.toInt())
    )

    fun id(ctx: Context): String = try { Prefs(ctx).raw().getString("accent", "luxury") ?: "luxury" } catch (_: Exception) { "luxury" }
    fun set(ctx: Context, id: String) { Prefs(ctx).raw().edit().putString("accent", id).apply() }
    fun current(ctx: Context): Theme = ALL.firstOrNull { it.id == id(ctx) } ?: ALL[0]

    fun isNight(ctx: Context): Boolean =
        (ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    fun primary(ctx: Context): Int = current(ctx).let { if (isNight(ctx)) it.dark else it.light }
    fun glow(ctx: Context): Int = current(ctx).let { if (isNight(ctx)) it.glowDark else it.glowLight }
}
