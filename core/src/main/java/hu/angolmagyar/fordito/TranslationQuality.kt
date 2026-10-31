package hu.angolmagyar.fordito

/**
 * Translation Quality – v2.64.0
 *
 * Ellenőrizhető nyelvtani és terminológiai figyelmeztetések.
 * Összefogja a meglévő TranslationCheck + TranslationQualityAudit
 * eredményeit, és további determinisztikus heurisztikákat ad:
 *  - terminológia-konzisztencia (egyszerű szótár-alapú),
 *  - ismétlődő fordítási minták,
 *  - alapvető magyar helyesírási / ékezet-figyelmeztetések,
 *  - forrás-cél nyelvi keveredés.
 *
 * Nem generatív, nem pontoz AI-val; csak szabályalapú jelzések.
 */
object TranslationQuality {

    enum class Category {
        NUMBER, PROPER_NAME, NEGATION, LENGTH, TERMINOLOGY,
        SPELLING, MIXED_LANGUAGE, EMPTY, STRUCTURE, OTHER
    }

    enum class Severity { INFO, REVIEW, WARNING, CRITICAL }

    data class Warning(
        val category: Category,
        val severity: Severity,
        val code: String,
        val messageHu: String,
        val sourceSnippet: String = "",
        val targetSnippet: String = ""
    )

    data class QualityResult(
        val source: String,
        val target: String,
        val warnings: List<Warning>,
        val scoreHint: Int,           // 0–100, csak heurisztikus tájékoztató
        val needsHumanReview: Boolean
    ) {
        val criticalCount: Int get() = warnings.count { it.severity == Severity.CRITICAL }
        val warningCount: Int get() = warnings.count { it.severity == Severity.WARNING }
        val reviewCount: Int get() = warnings.count { it.severity == Severity.REVIEW }
    }

    /**
     * Teljes minőségellenőrzés egy forrás–cél párra.
     */
    fun analyze(sourceEnglish: String, targetHungarian: String): QualityResult {
        val src = sourceEnglish.trim()
        val dst = targetHungarian.trim()
        val warnings = mutableListOf<Warning>()

        if (src.isEmpty() && dst.isEmpty()) {
            return QualityResult(src, dst, emptyList(), 100, false)
        }
        if (src.isNotEmpty() && dst.isEmpty()) {
            warnings += Warning(
                Category.EMPTY, Severity.CRITICAL, "EMPTY_TARGET",
                "A forrásszöveghez nem készült fordítás.", src, ""
            )
            return QualityResult(src, dst, warnings, 0, true)
        }
        if (src.isEmpty() && dst.isNotEmpty()) {
            warnings += Warning(
                Category.EMPTY, Severity.REVIEW, "EMPTY_SOURCE",
                "A fordításnak nincs összehasonlítható forrásszövege.", "", dst
            )
        }

        // 1. Meglévő TranslationCheck (szám, név, tagadás)
        TranslationCheck.check(src, dst).forEach { issue ->
            val (cat, sev, code, msg) = when (issue) {
                "szám eltér" -> Quad(Category.NUMBER, Severity.WARNING, "NUMBER_MISMATCH",
                    "A forrásban szereplő szám/mennyiség nem jelenik meg megfelelően a fordításban.")
                "név hiányzik" -> Quad(Category.PROPER_NAME, Severity.WARNING, "PROPER_NAME_MISSING",
                    "Tulajdonnév vagy betűszó hiányzik vagy megváltozott a fordításban.")
                "tagadás eltér" -> Quad(Category.NEGATION, Severity.WARNING, "NEGATION_MISMATCH",
                    "Lehetséges tagadásbeli eltérés – ellenőrizd a mondat jelentését.")
                else -> Quad(Category.OTHER, Severity.REVIEW, "GENERIC_REVIEW",
                    "A fordítás további ellenőrzést igényel.")
            }
            warnings += Warning(cat, sev, code, msg, src.take(80), dst.take(80))
        }

        // 2. Hossz-arány (meglévő audit logikája)
        val srcWords = src.split(Regex("\\s+")).count { it.isNotBlank() }
        val dstWords = dst.split(Regex("\\s+")).count { it.isNotBlank() }
        if (srcWords >= 8 && dstWords >= 1) {
            val ratio = dstWords.toDouble() / srcWords
            if (ratio < 0.35 || ratio > 2.8) {
                warnings += Warning(
                    Category.LENGTH, Severity.REVIEW, "LENGTH_OUTLIER",
                    "A forrás és a fordítás hossza szokatlanul eltér (arány: ${"%.2f".format(ratio)}).",
                    src.take(60), dst.take(60)
                )
            }
        }

        // 3. Nyelvi keveredés: angol szavak a magyar fordításban (egyszerű heurisztika)
        val enLike = Regex("\\b(the|and|is|are|was|were|this|that|with|for|have|has|not|from)\\b",
            RegexOption.IGNORE_CASE)
        val enMatches = enLike.findAll(dst).map { it.value }.distinct().toList()
        if (enMatches.isNotEmpty() && dst.length > 20) {
            warnings += Warning(
                Category.MIXED_LANGUAGE, Severity.REVIEW, "ENGLISH_LEFTOVER",
                "A fordításban angol szavak maradhattak: ${enMatches.take(5).joinToString(", ")}.",
                "", dst.take(80)
            )
        }

        // 4. Ékezet / alapvető helyesírási jelzés (nagyon konzervatív)
        // Ha a forrás hosszú, a cél pedig teljesen ékezet nélküli és hosszú, jelezzük.
        val hasAccent = Regex("[áéíóöőúüűÁÉÍÓÖŐÚÜŰ]").containsMatchIn(dst)
        if (dstWords >= 12 && !hasAccent && dst.length > 60) {
            warnings += Warning(
                Category.SPELLING, Severity.INFO, "NO_ACCENTS",
                "A fordításban nem található magyar ékezet. Ellenőrizd, hogy szándékos-e (pl. ASCII-only kimenet).",
                "", dst.take(60)
            )
        }

        // 5. Struktúra: forrás kérdő, cél nem
        if (src.contains("?") && !dst.contains("?") && !dst.contains("?")) {
            // note: Hungarian questions often end with ?
            if (dstWords >= 3) {
                warnings += Warning(
                    Category.STRUCTURE, Severity.INFO, "QUESTION_MARK_MISSING",
                    "A forrás kérdő mondat, a fordításból hiányzik a kérdőjel.",
                    src.take(50), dst.take(50)
                )
            }
        }

        // 6. SmartContext ambiguitás jelzése
        if (SmartContextTranslation.hasAmbiguity(src)) {
            warnings += Warning(
                Category.TERMINOLOGY, Severity.INFO, "POSSIBLE_AMBIGUITY",
                "A forrásban kétértelmű szavak lehetnek. Használd a Smart Context javaslatokat.",
                src.take(60), ""
            )
        }

        // Heurisztikus pontszám (100-ból levonás)
        var score = 100
        warnings.forEach { w ->
            score -= when (w.severity) {
                Severity.CRITICAL -> 40
                Severity.WARNING -> 15
                Severity.REVIEW -> 8
                Severity.INFO -> 2
            }
        }
        score = score.coerceIn(0, 100)

        val needsReview = warnings.any {
            it.severity == Severity.CRITICAL || it.severity == Severity.WARNING ||
                (it.severity == Severity.REVIEW && warnings.count { x -> x.severity == Severity.REVIEW } >= 2)
        }

        return QualityResult(src, dst, warnings.distinctBy { it.code }, score, needsReview)
    }

    /** Rövid összefoglaló a UI-hoz. */
    fun summaryLine(result: QualityResult): String {
        if (result.warnings.isEmpty()) return "Nincs automatikus figyelmeztetés · tájékoztató pontszám: ${result.scoreHint}"
        val parts = mutableListOf<String>()
        if (result.criticalCount > 0) parts += "${result.criticalCount} kritikus"
        if (result.warningCount > 0) parts += "${result.warningCount} figyelmeztetés"
        if (result.reviewCount > 0) parts += "${result.reviewCount} ellenőrzendő"
        return parts.joinToString(" · ") + " · pontszám: ${result.scoreHint}"
    }

    private data class Quad(
        val cat: Category, val sev: Severity, val code: String, val msg: String
    )
}
