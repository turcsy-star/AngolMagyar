package hu.angolmagyar.fordito.ultimate
import kotlin.math.roundToInt
data class SkillSnapshot(
    val vocabulary: Int = 35,
    val grammar: Int = 35,
    val listening: Int = 35,
    val speaking: Int = 35,
    val reading: Int = 35,
    val writing: Int = 35
) {
    fun average(): Int = listOf(vocabulary, grammar, listening, speaking, reading, writing).average().roundToInt()
    fun weakest(): String = listOf(
        "Szókincs" to vocabulary, "Nyelvtan" to grammar, "Hallásértés" to listening,
        "Beszéd" to speaking, "Olvasás" to reading, "Írás" to writing
    ).minBy { it.second }.first
    fun suggestedCefr(): String = when (average()) {
        in 0..19 -> "A1"; in 20..34 -> "A2"; in 35..49 -> "B1"
        in 50..64 -> "B2"; in 65..79 -> "C1"; else -> "C2"
    }
}
data class Scenario(val title: String, val prompt: String, val target: String, val hint: String)
object UltimateLearningEngine {
    val scenarios = listOf(
        Scenario("Étterem", "You are at a restaurant. Ask for a table for two and the menu.", "Udvarias kérés és kérdő mondatok", "Could we have a table for two, please?"),
        Scenario("Állásinterjú", "The interviewer asks: Tell me about yourself.", "Bemutatkozás és múltbeli tapasztalat", "I have experience in…"),
        Scenario("Utazás", "Your train has been cancelled. Ask staff about another connection.", "Problémamegoldás és feltételes kérés", "Could you tell me when the next train leaves?"),
        Scenario("Üzleti tárgyalás", "Suggest a new delivery date and explain the reason.", "Javaslattétel és indoklás", "I would suggest moving the delivery to…"),
        Scenario("Telefon", "Call a company and ask to speak to the accounts department.", "Telefonos fordulatok", "Could I speak to someone in accounts, please?")
    )
    fun wordCount(text: String): Int = Regex("\\b[\\p{L}\\p{N}’'-]+\\b").findAll(text).count()
    fun estimateReadingMinutes(text: String): Int = (wordCount(text) / 200.0).let { kotlin.math.ceil(it).toInt().coerceAtLeast(if (text.isBlank()) 0 else 1) }
    fun classifyLevel(score: Int): String = when (score.coerceIn(0, 100)) {
        in 0..19 -> "A1"; in 20..34 -> "A2"; in 35..49 -> "B1"
        in 50..64 -> "B2"; in 65..79 -> "C1"; else -> "C2"
    }
}
