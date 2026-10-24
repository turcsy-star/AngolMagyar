package hu.angolmagyar.fordito

/** Idő-leképezés: a csendek nélküli (összefűzött) darab ideje → az eredeti videó ideje (ms). */
class TimeMap(private val cStart: LongArray, private val oStart: LongArray, private val len: LongArray) {
    fun toOrig(t: Long): Long {
        if (cStart.isEmpty()) return t
        var k = -1
        for (i in cStart.indices) { if (cStart[i] <= t) k = i else break }
        if (k < 0) return oStart[0]
        val d = t - cStart[k]
        return if (d <= len[k]) oStart[k] + d else oStart[k] + len[k]
    }

    /** Ha a darabot félbevágjuk: a második fél idejének eltolása. */
    fun shifted(deltaMs: Long) = TimeMap(LongArray(cStart.size) { cStart[it] - deltaMs }, oStart, len)
}

/** A darabból kivágja a csendeket: csak a beszédrészek (kis ráhagyással) maradnak, rövid szünetekkel összefűzve. */
object Compact {
    /**
     * @param slice a darab hangja (a darab a [chunkStartSample]-nél kezdődik az eredeti hangban)
     * @param fine a VAD beszédszakaszai az egész hangban (sample-indexek, rendezve)
     * @return az összefűzött hang + idő-leképezés, vagy null, ha nincs érdemi nyereség
     */
    fun build(slice: ShortArray, chunkStartSample: Int, fine: List<Vad.Seg>, rate: Int = 16000): Pair<ShortArray, TimeMap>? {
        if (slice.isEmpty() || fine.isEmpty()) return null
        val cs = chunkStartSample
        val ce = cs + slice.size
        val local = ArrayList<IntArray>()
        for (s in fine) {
            if (s.end <= cs) continue
            if (s.start >= ce) break
            val a = maxOf(s.start, cs) - cs
            val b = minOf(s.end, ce) - cs
            if (b > a) local.add(intArrayOf(a, b))
        }
        if (local.isEmpty()) return null
        val pad = rate * 250 / 1000
        val joinGap = rate * 600 / 1000
        val silence = rate * 300 / 1000
        // közeli beszédrészek összevonása (a rövid szünet marad természetes hang)
        val m = ArrayList<IntArray>()
        var cur = local[0].copyOf()
        for (i in 1 until local.size) {
            val n = local[i]
            if (n[0] - cur[1] <= joinGap) cur[1] = maxOf(cur[1], n[1]) else { m.add(cur); cur = n.copyOf() }
        }
        m.add(cur)
        // ráhagyás: a szomszédos részek nem lóghatnak egymásba
        val a = IntArray(m.size)
        val b = IntArray(m.size)
        for (i in m.indices) {
            val lo = if (i == 0) 0 else (m[i - 1][1] + m[i][0]) / 2
            val hi = if (i == m.size - 1) slice.size else (m[i][1] + m[i + 1][0]) / 2
            a[i] = maxOf(m[i][0] - pad, lo)
            b[i] = minOf(m[i][1] + pad, hi)
        }
        var total = 0L
        for (i in m.indices) total += (b[i] - a[i]) + (if (i > 0) silence else 0)
        if (total > slice.size * 0.9) return null
        val out = ShortArray(total.toInt())
        val cStart = LongArray(m.size)
        val oStart = LongArray(m.size)
        val len = LongArray(m.size)
        var pos = 0
        val baseMs = cs.toLong() * 1000L / rate
        for (i in m.indices) {
            if (i > 0) pos += silence // nullák (a tömb alapból 0)
            System.arraycopy(slice, a[i], out, pos, b[i] - a[i])
            cStart[i] = pos.toLong() * 1000L / rate
            oStart[i] = baseMs + a[i].toLong() * 1000L / rate
            len[i] = (b[i] - a[i]).toLong() * 1000L / rate
            pos += b[i] - a[i]
        }
        return out to TimeMap(cStart, oStart, len)
    }
}
