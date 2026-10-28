package hu.angolmagyar.fordito

/**
 * Token-szintű Whisper-pontszámok óvatos értelmezése.
 * A token valószínűsége nem azonos a szó helyességének valószínűségével;
 * kizárólag kézi ellenőrzésre rangsorol, automatikusan nem ír át vagy töröl szöveget.
 */
object TokenQuality {
    const val REVIEW_PROBABILITY = 0.35f

    data class Token(val text: String, val probability: Float, val startMs: Long, val endMs: Long)
    data class ReviewSpan(val startMs: Long, val endMs: Long, val text: String, val minProbability: Float)

    fun needsReview(probability: Float, threshold: Float = REVIEW_PROBABILITY): Boolean =
        !probability.isFinite() || probability < threshold

    fun reviewSpans(tokens: List<Token>, threshold: Float = REVIEW_PROBABILITY): List<ReviewSpan> =
        tokens.filter { needsReview(it.probability, threshold) && it.text.isNotBlank() }
            .map { ReviewSpan(it.startMs, it.endMs, it.text, it.probability) }
}
