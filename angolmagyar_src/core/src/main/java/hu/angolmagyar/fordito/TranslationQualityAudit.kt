package hu.angolmagyar.fordito

/**
 * Determinisztikus fordítási ellenőrző az EN→HU munkafolyamathoz.
 * Heurisztikus figyelmeztetéseket ad, nem állít elő valószínűségi pontosságot,
 * és nem írja át automatikusan a fordítást.
 */
object TranslationQualityAudit {
    enum class Severity { INFO, REVIEW, WARNING }
    data class Finding(val severity: Severity, val code: String, val message: String)
    data class Report(val findings: List<Finding>) {
        val needsReview: Boolean get() = findings.any { it.severity != Severity.INFO }
        val isEmpty: Boolean get() = findings.isEmpty()
    }

    fun inspect(sourceEnglish: String, translationHungarian: String): Report {
        val src = sourceEnglish.trim()
        val dst = translationHungarian.trim()
        if (src.isEmpty() && dst.isEmpty()) return Report(emptyList())
        val findings = mutableListOf<Finding>()
        if (src.isNotEmpty() && dst.isEmpty()) {
            findings += Finding(Severity.WARNING, "EMPTY_TRANSLATION", "Az eredeti szöveghez nem készült fordítás.")
            return Report(findings)
        }
        if (src.isEmpty() && dst.isNotEmpty()) {
            findings += Finding(Severity.REVIEW, "EMPTY_SOURCE", "A fordításnak nincs összehasonlítható forrásszövege.")
            return Report(findings)
        }

        TranslationCheck.check(src, dst).forEach { issue ->
            val code = when (issue) {
                "szám eltér" -> "NUMBER_MISMATCH"
                "név hiányzik" -> "PROPER_NAME_MISSING"
                "tagadás eltér" -> "NEGATION_MISMATCH"
                else -> "TRANSLATION_REVIEW"
            }
            val message = when (issue) {
                "szám eltér" -> "Ellenőrizd, hogy a számok és mennyiségek változatlanul szerepelnek-e."
                "név hiányzik" -> "Ellenőrizd a tulajdonnevek és betűszavak átkerülését."
                "tagadás eltér" -> "Lehetséges tagadásbeli eltérés: ellenőrizd a mondat jelentését."
                else -> "A fordítás további ellenőrzést igényel."
            }
            findings += Finding(Severity.WARNING, code, message)
        }

        // A nagy hosszeltérés csak ellenőrzési jelzés: rövid angol és magyar mondatok
        // természetes hossza jelentősen eltérhet, ezért ez soha nem minősül hibabizonyítéknak.
        val srcWords = src.split(Regex("\\s+")).count { it.isNotBlank() }
        val dstWords = dst.split(Regex("\\s+")).count { it.isNotBlank() }
        if (srcWords >= 8 && dstWords >= 1 && (dstWords.toDouble() / srcWords < 0.35 || dstWords.toDouble() / srcWords > 2.8)) {
            findings += Finding(Severity.REVIEW, "LENGTH_OUTLIER", "A forrás és a fordítás hossza szokatlanul eltér; ellenőrizd, nem maradt-e ki részlet.")
        }
        return Report(findings.distinctBy { it.code })
    }
}
