package hu.angolmagyar.fordito
object DualTranslation {
    data class Comparison(
        val identical: Boolean,
        val similarityPercent: Int,
        val common: List<String>,
        val onlyA: List<String>,
        val onlyB: List<String>,
        val wordsA: Int,
        val wordsB: Int,
        val numbersAgree: Boolean,
        val findingsA: List<TranslationQualityAudit.Finding>,
        val findingsB: List<TranslationQualityAudit.Finding>,
        val formalA: Boolean,
        val formalB: Boolean,
        val notes: List<String>
    )
    private val WORD = Regex("[\\p{L}\\p{N}]+")
    private val NUMBER = Regex("\\d+(?:[.,]\\d+)?")
    private fun words(s: String): List<String> = WORD.findAll(s.lowercase()).map { it.value }.toList()
    private fun numbers(s: String): Set<String> = NUMBER.findAll(s).map { it.value.replace(',', '.') }.toSet()
    fun compare(source: String, a: String, b: String): Comparison {
        val wa = words(a)
        val wb = words(b)
        val setA = wa.toCollection(LinkedHashSet())
        val setB = wb.toCollection(LinkedHashSet())
        val common = setA.filter { it in setB }
        val onlyA = setA.filter { it !in setB }
        val onlyB = setB.filter { it !in setA }
        val union = (setA + setB).size
        val similarity = if (union == 0) 100 else common.size * 100 / union
        val identical = wa == wb
        val numA = numbers(a)
        val numB = numbers(b)
        val numSrc = numbers(source)
        val findingsA = TranslationQualityAudit.inspect(source, a).findings
        val findingsB = TranslationQualityAudit.inspect(source, b).findings
        val formalA = TextPolish.looksFormal(a)
        val formalB = TextPolish.looksFormal(b)
        val notes = ArrayList<String>()
        if (identical) {
            notes.add("A két változat szó szerint azonos.")
        } else {
            when {
                similarity >= 85 -> notes.add("Szinte azonos: csak apró eltérések vannak.")
                similarity < 40 -> notes.add("Jelentősen eltérő megfogalmazás: olvasd át mindkettőt a jelentés szempontjából.")
            }
            val longer = maxOf(wa.size, wb.size)
            val shorter = minOf(wa.size, wb.size)
            if (shorter > 0 && longer * 100 / shorter >= 140)
                notes.add("Az egyik változat lényegesen hosszabb (${wa.size} és ${wb.size} szó).")
        }
        if (numA != numB) notes.add("A két változat számai eltérnek: ${numA.sorted().joinToString(", ").ifEmpty { "–" }} és ${numB.sorted().joinToString(", ").ifEmpty { "–" }}.")
        if (numSrc.isNotEmpty()) {
            if (numA != numSrc) notes.add("Az A változat számai nem egyeznek a forrással.")
            if (numB != numSrc) notes.add("A B változat számai nem egyeznek a forrással.")
        }
        when {
            formalA && formalB -> notes.add("Mindkét változat magázó alakot tartalmaz; tegezőre alakítható.")
            formalA != formalB -> notes.add("Csak az ${if (formalA) "A" else "B"} változatban van magázó alak: a hangnem nem egységes.")
        }
        if (findingsA.size != findingsB.size)
            notes.add("A szabályalapú jelzések száma eltér (A: ${findingsA.size}, B: ${findingsB.size}); ez nem minőségi rangsor.")
        return Comparison(identical, similarity, common, onlyA, onlyB, wa.size, wb.size, numA == numB,
            findingsA, findingsB, formalA, formalB, notes)
    }
}
