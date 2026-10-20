package hu.angolmagyar.fordito

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Kedvenc mondatok: a feliraton hosszú nyomással menthető (angol + fordítás). */
object Favorites {

    data class Item(val stamp: String, val source: String, val target: String)

    private fun file(ctx: Context) = File(ctx.filesDir, "favorites.tsv")

    @Synchronized
    fun add(ctx: Context, source: String, target: String) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        try {
            file(ctx).appendText(
                "$stamp\t${source.replace('\t', ' ').replace('\n', ' ')}\t${target.replace('\t', ' ').replace('\n', ' ')}\n"
            )
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun list(ctx: Context): List<Item> {
        val f = file(ctx)
        if (!f.exists()) return emptyList()
        return try {
            f.readLines().mapNotNull { l ->
                val p = l.split('\t')
                if (p.size >= 3) Item(p[0], p[1], p[2]) else null
            }.reversed()
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun remove(ctx: Context, item: Item) {
        val f = file(ctx)
        if (!f.exists()) return
        try {
            val line = "${item.stamp}\t${item.source}\t${item.target}"
            val kept = f.readLines().filter { it != line }
            f.writeText(if (kept.isEmpty()) "" else kept.joinToString("\n") + "\n")
        } catch (_: Exception) {
        }
    }

    fun asText(items: List<Item>): String =
        items.joinToString("\n\n") { "${it.stamp}\n${it.source}\n${it.target}" }
}
