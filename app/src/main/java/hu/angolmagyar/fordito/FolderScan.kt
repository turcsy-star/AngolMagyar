package hu.angolmagyar.fordito

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/** Egy mappa (és almappái) videóinak / hangfájljainak keresése, amelyekhez még nincs .srt. */
object FolderScan {
    private val EXT = setOf(
        "mp4", "mkv", "avi", "mov", "webm", "m4v", "3gp", "ts",
        "mp3", "wav", "m4a", "flac", "ogg", "opus", "aac", "wma"
    )

    data class Found(val uri: Uri, val name: String)

    /** @return a feldolgozatlan (nincs mellette azonos nevű kezdetű .srt) fájlok, legfeljebb [max] db */
    fun scan(ctx: Context, tree: Uri, max: Int = 300): List<Found> {
        val out = ArrayList<Found>()
        try {
            val rootId = DocumentsContract.getTreeDocumentId(tree)
            walk(ctx, tree, rootId, out, max, 0)
        } catch (_: Exception) {
        }
        return out
    }

    private fun walk(ctx: Context, tree: Uri, parentId: String, out: MutableList<Found>, max: Int, depth: Int) {
        if (depth > 6 || out.size >= max) return
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val files = ArrayList<Pair<String, String>>() // (docId, name)
        val dirs = ArrayList<String>()
        ctx.contentResolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val mime = c.getString(2) ?: ""
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) dirs.add(id) else files.add(id to name)
            }
        }
        val srts = files.map { it.second.lowercase() }.filter { it.endsWith(".srt") }
        for ((id, name) in files.sortedBy { it.second.lowercase() }) {
            if (out.size >= max) return
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext !in EXT) continue
            val base = name.substringBeforeLast('.').lowercase()
            if (srts.any { it.startsWith(base) }) continue
            out.add(Found(DocumentsContract.buildDocumentUriUsingTree(tree, id), name))
        }
        for (d in dirs) walk(ctx, tree, d, out, max, depth + 1)
    }
}
