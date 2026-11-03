package hu.angolmagyar.fordito

/**
 * Az értesítés folyamatsávjának terve (Android 16 ProgressStyle): a lépések súlyai szegmensek,
 * a felismerés szegmensében a darabhatárok jelölőpontok. Tiszta logika, külön tesztelhető.
 */
object NotificationPlan {
    data class Seg(val length: Int, val color: Int)
    data class Plan(
        val segments: List<Seg>,
        val points: List<Int>,
        val progress: Int,
        val max: Int,
        val indeterminate: Boolean,
        val shortText: String
    )

    const val MAX_POINTS = 10
    private const val RECOG_STEP = 3

    fun build(weights: IntArray, colors: IntArray, overall: Int, chunkTotal: Int): Plan {
        val w = weights.map { it.coerceAtLeast(1) }
        val max = w.sum()
        val segs = w.mapIndexed { i, len -> Seg(len, colors.getOrElse(i) { 0xFF8B9CB3.toInt() }) }
        val progress = (overall.coerceIn(0, 100) * max / 100).coerceIn(0, max)
        val points = ArrayList<Int>()
        if (chunkTotal in 2..1000 && weights.size > RECOG_STEP) {
            val start = w.take(RECOG_STEP).sum()
            val len = w[RECOG_STEP]
            val wanted = minOf(chunkTotal - 1, MAX_POINTS)
            for (k in 1..wanted) {
                // egyenletesen elosztva, ha több a darab, mint a megjeleníthető pont
                val chunkIndex = if (chunkTotal - 1 <= MAX_POINTS) k else k * chunkTotal / (wanted + 1)
                val pos = start + len * chunkIndex / chunkTotal
                if (pos in 1 until max && (points.isEmpty() || pos > points.last())) points.add(pos)
            }
        }
        val short = if (overall in 1..99) "$overall%" else ""
        return Plan(segs, points, progress, max, overall <= 0, short)
    }
}
