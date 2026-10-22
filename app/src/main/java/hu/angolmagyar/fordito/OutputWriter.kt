package hu.angolmagyar.fordito

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File

/** A mentés helyének beállításai (a szolgáltatás is olvassa, ezért külön, Activity-független). */
object OutputPrefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("srt_out", Context.MODE_PRIVATE)

    fun defaultTree(ctx: Context): Uri? = sp(ctx).getString("defaultTree", null)?.let { Uri.parse(it) }
    fun setDefaultTree(ctx: Context, uri: Uri?) = sp(ctx).edit().putString("defaultTree", uri?.toString()).apply()

    fun videoTree(ctx: Context): Uri? = sp(ctx).getString("videoTree", null)?.let { Uri.parse(it) }
    fun setVideoTree(ctx: Context, uri: Uri?) = sp(ctx).edit().putString("videoTree", uri?.toString()).apply()

    fun autoClose(ctx: Context): Boolean = sp(ctx).getBoolean("autoClose", false)
    fun setAutoClose(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("autoClose", v).apply()

    /** Preferált mentés: true = videó mellé (alapértelmezett), false = alapértelmezett mappa. */
    fun preferBeside(ctx: Context): Boolean = sp(ctx).getBoolean("preferBeside", true)
    fun setPreferBeside(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("preferBeside", v).apply()
}

/**
 * Kimeneti fájlok írása felhasználó által választott helyre:
 *  - a videó mellé (teljes fájlhozzáférés VAGY egyszer engedélyezett mappa-hozzáférés kell hozzá),
 *  - az alapértelmezett mappába (egyszer választott mappa, tartós engedéllyel).
 * Minden függvény null-t ad, ha nem sikerült – ilyenkor a hívó tartalék megoldást használ.
 */
object OutputWriter {

    const val MIME_SRT = "application/x-subrip"
    private const val EXT_STORAGE = "com.android.externalstorage.documents"
    private const val MEDIA_DOCS = "com.android.providers.media.documents"
    private const val DOWNLOAD_DOCS = "com.android.providers.downloads.documents"

    fun displayName(ctx: Context, uri: Uri): String? = try {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }

    fun safeName(s: String): String =
        s.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trim('.').ifEmpty { "felirat" }

    fun baseName(fileName: String): String =
        safeName(fileName.substringBeforeLast('.', fileName))

    fun hasAllFiles(): Boolean =
        Build.VERSION.SDK_INT >= 30 && try { Environment.isExternalStorageManager() } catch (_: Exception) { false }

    // ---------------------------------------------------------------- mappa-címkék

    private fun docIdLabel(id: String): String {
        val vol = id.substringBefore(':')
        val rel = id.substringAfter(':', "")
        val v = if (vol == "primary") "Belső tárhely" else vol
        return if (rel.isEmpty()) v else "$v/$rel"
    }

    fun folderLabel(tree: Uri): String = try {
        docIdLabel(DocumentsContract.getTreeDocumentId(tree))
    } catch (_: Exception) {
        tree.toString()
    }

    // ------------------------------------------------------------ a videó szülőmappája

    /** A videó szülőmappájának "dokumentum-azonosítója" (pl. "primary:Movies/Sorozat"), ha kideríthető. */
    private fun parentDocId(ctx: Context, video: Uri): String? {
        try {
            val auth = video.authority ?: return null
            val docId = DocumentsContract.getDocumentId(video)
            return when (auth) {
                EXT_STORAGE -> parentOf(docId)
                MEDIA_DOCS -> {
                    if (Build.VERSION.SDK_INT < 29) return null
                    val kind = docId.substringBefore(':')
                    val id = docId.substringAfter(':').toLongOrNull() ?: return null
                    val coll = when (kind) {
                        "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                        else -> return null
                    }
                    ctx.contentResolver.query(
                        android.content.ContentUris.withAppendedId(coll, id),
                        arrayOf(MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.VOLUME_NAME),
                        null, null, null
                    )?.use {
                        if (!it.moveToFirst()) return null
                        val rel = (it.getString(0) ?: return null).trimEnd('/')
                        val vol = it.getString(1)
                        val v = if (vol == null || vol == "external_primary" || vol == "external") "primary" else vol
                        "$v:$rel"
                    }
                }
                DOWNLOAD_DOCS -> {
                    if (docId.startsWith("raw:")) {
                        val path = docId.removePrefix("raw:")
                        val root = Environment.getExternalStorageDirectory().absolutePath
                        if (path.startsWith("$root/")) parentOf("primary:" + path.removePrefix("$root/")) else null
                    } else null
                }
                else -> null
            }
        } catch (_: Exception) {
            return null
        }
    }

    private fun parentOf(docId: String): String {
        val i = docId.lastIndexOf('/')
        return if (i >= 0) docId.substring(0, i) else docId.substringBefore(':') + ":"
    }

    private fun fileFor(docId: String): File {
        val vol = docId.substringBefore(':')
        val rel = docId.substringAfter(':', "")
        val base = if (vol == "primary") Environment.getExternalStorageDirectory() else File("/storage/$vol")
        return if (rel.isEmpty()) base else File(base, rel)
    }

    private fun treeContains(tree: Uri, docId: String): Boolean = try {
        val t = DocumentsContract.getTreeDocumentId(tree)
        if (t.endsWith(":")) docId.startsWith(t) else docId == t || docId.startsWith("$t/")
    } catch (_: Exception) {
        false
    }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val base = name.substringBeforeLast('.', name)
        val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
        var n = 1
        while (f.exists()) { f = File(dir, "$base ($n)$ext"); n++ }
        return f
    }

    // ------------------------------------------------------------------- írás

    private fun writeInTree(ctx: Context, tree: Uri, parentDocId: String, name: String, mime: String, bytes: ByteArray): Boolean {
        return try {
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, parentDocId)
            val doc = DocumentsContract.createDocument(ctx.contentResolver, parent, mime, name) ?: return false
            ctx.contentResolver.openOutputStream(doc)?.use { it.write(bytes) } ?: return false
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Az alapértelmezett (vagy megadott) mappába ment. Ad: ember-olvasható hely, vagy null. */
    fun saveInTree(ctx: Context, tree: Uri, name: String, mime: String, bytes: ByteArray): String? {
        return try {
            val root = DocumentsContract.getTreeDocumentId(tree)
            if (writeInTree(ctx, tree, root, name, mime, bytes)) folderLabel(tree) else null
        } catch (_: Exception) {
            null
        }
    }

    /** A videó mellé ment (teljes fájlhozzáféréssel vagy engedélyezett mappán keresztül). */
    fun saveBesideVideo(ctx: Context, video: Uri, name: String, mime: String, bytes: ByteArray): String? {
        val parent = parentDocId(ctx, video) ?: return null
        if (hasAllFiles()) {
            try {
                val dir = fileFor(parent)
                if (dir.isDirectory) {
                    val f = uniqueFile(dir, name)
                    f.writeBytes(bytes)
                    return "a videó mellé: " + docIdLabel(parent)
                }
            } catch (_: Exception) {
            }
        }
        val tree = OutputPrefs.videoTree(ctx) ?: return null
        if (!treeContains(tree, parent)) return null
        return if (writeInTree(ctx, tree, parent, name, mime, bytes)) "a videó mellé: " + docIdLabel(parent) else null
    }

    /** Hozzáférés-állapot a képernyőn való kiíráshoz. */
    fun besideVideoStatus(ctx: Context): String = when {
        hasAllFiles() -> "✓ teljes fájlhozzáférés"
        OutputPrefs.videoTree(ctx) != null -> "✓ engedélyezett mappa: " + folderLabel(OutputPrefs.videoTree(ctx)!!)
        else -> "✗ nincs engedély (mentés-ablak jelenik meg)"
    }
}
