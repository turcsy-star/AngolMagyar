package hu.angolmagyar.fordito

import java.text.Normalizer
import java.util.LinkedHashMap

/** Fordítási memória: ugyanaz az angol mondat mindig ugyanúgy fordul; a kézzel javított sor felülírja a gépit. */
class TranslationMemory(private val map: LinkedHashMap<String, String> = LinkedHashMap(), private val cap: Int = 5000) {

    companion object {
        private val NON = Regex("[^\\p{L}\\p{N}]+")
        fun key(en: String): String = Normalizer.normalize(en, Normalizer.Form.NFC)
            .lowercase().replace(NON, " ").trim()

        fun fromText(t: String): TranslationMemory {
            val m = LinkedHashMap<String, String>()
            val memory = TranslationMemory(m)
            for (line in t.lineSequence()) {
                if (line.isBlank()) continue
                val i = line.indexOf('\t')
                if (i <= 0 || i == line.lastIndex) continue
                // Import through put(): normalize external source phrases exactly like lookup().
                // This also applies capacity limits and rejects blank targets consistently.
                memory.put(line.substring(0, i), line.substring(i + 1))
            }
            return memory
        }
    }

    val size: Int get() = map.size

    fun lookup(en: String): String? {
        val k = key(en)
        if (k.length < 3) return null
        return map[k]?.takeIf { it.isNotBlank() }
    }

    fun put(en: String, hu: String) {
        val k = key(en)
        val v = hu.replace('\t', ' ').replace('\n', ' ').trim()
        if (k.length < 3 || v.isBlank()) return
        map.remove(k) // a legutóbb használt a lista végére kerül
        map[k] = v
        while (map.size > cap) map.remove(map.keys.first())
    }

    fun toText(): String = map.entries.joinToString("\n") { it.key + "\t" + it.value }
}
