package hu.angolmagyar.fordito
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File
object OutputPrefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("srt_out", Context.MODE_PRIVATE)
    fun defaultTree(ctx: Context): Uri? = sp(ctx).getString("defaultTree", null)?.let { Uri.parse(it) }
    fun setDefaultTree(ctx: Context, uri: Uri?) = sp(ctx).edit().putString("defaultTree", uri?.toString()).apply()
    fun videoTree(ctx: Context): Uri? = sp(ctx).getString("videoTree", null)?.let { Uri.parse(it) }
    fun setVideoTree(ctx: Context, uri: Uri?) = sp(ctx).edit().putString("videoTree", uri?.toString()).apply()
    const val SAVE_BESIDE = 0
    const val SAVE_DEFAULT = 1
    fun saveMode(ctx: Context): Int = sp(ctx).getInt("saveMode", SAVE_BESIDE)
    fun setSaveMode(ctx: Context, v: Int) = sp(ctx).edit().putInt("saveMode", v).apply()
    fun autoClose(ctx: Context): Boolean = sp(ctx).getBoolean("autoClose", false)
    fun setAutoClose(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("autoClose", v).apply()
}
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
    fun allFilesDeclared(ctx: Context): Boolean = try {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
        pi.requestedPermissions?.contains("android.permission.MANAGE_EXTERNAL_STORAGE") == true
    } catch (_: Exception) { false }
    fun hasAllFiles(): Boolean =
        Build.VERSION.SDK_INT >= 30 && try { Environment.isExternalStorageManager() } catch (_: Exception) { false }
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
    @Suppress("DEPRECATION")
    private fun dirFromMediaStore(ctx: Context, id: Long): File? {
        val uri = android.content.ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), id)
        ctx.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.VOLUME_NAME),
            null, null, null
        )?.use { c ->
            if (!c.moveToFirst()) return null
            val data = c.getString(0)
            if (!data.isNullOrBlank()) File(data).parentFile?.let { return it }
            val rel = c.getString(1)?.trimEnd('/') ?: return null
            val vol = c.getString(2)
            val base = if (vol == null || vol == "external_primary" || vol == "external")
                Environment.getExternalStorageDirectory() else File("/storage/$vol")
            return File(base, rel)
        }
        return null
    }
    @Suppress("DEPRECATION")
    fun parentDir(ctx: Context, video: Uri): File? {
        try {
            if (video.scheme == "file") return video.path?.let { File(it).parentFile }
            val auth = video.authority ?: return null
            when (auth) {
                EXT_STORAGE -> {
                    val docId = DocumentsContract.getDocumentId(video)
                    return fileFor(parentOf(docId))
                }
                MEDIA_DOCS -> {
                    val id = DocumentsContract.getDocumentId(video).substringAfter(':').toLongOrNull()
                    if (id != null) dirFromMediaStore(ctx, id)?.let { return it }
                }
                DOWNLOAD_DOCS -> {
                    val docId = DocumentsContract.getDocumentId(video)
                    if (docId.startsWith("raw:")) return File(docId.removePrefix("raw:")).parentFile
                    val id = docId.substringAfter(':').toLongOrNull()
                    if (id != null) dirFromMediaStore(ctx, id)?.let { return it }
                }
                "media" -> {
                    val id = android.content.ContentUris.parseId(video)
                    if (id > 0) dirFromMediaStore(ctx, id)?.let { return it }
                }
            }
            ctx.contentResolver.query(video, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val d = c.getString(0)
                    if (!d.isNullOrBlank() && d.startsWith("/")) return File(d).parentFile
                }
            }
        } catch (e: Exception) { DiagLog.swallow("OutputWriter", e) }
        return null
    }
    private fun docIdOf(dir: File): String? {
        val p = dir.absolutePath
        val root = Environment.getExternalStorageDirectory().absolutePath
        if (p == root) return "primary:"
        if (p.startsWith("$root/")) return "primary:" + p.removePrefix("$root/")
        if (p.startsWith("/storage/")) {
            val rest = p.removePrefix("/storage/")
            val vol = rest.substringBefore('/')
            if (vol == "emulated" || vol == "self") return null
            return "$vol:" + rest.substringAfter('/', "")
        }
        return null
    }
    fun dirLabel(dir: File): String = docIdOf(dir)?.let { docIdLabel(it) } ?: dir.absolutePath
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
    fun saveInTree(ctx: Context, tree: Uri, name: String, mime: String, bytes: ByteArray): String? {
        return try {
            val root = DocumentsContract.getTreeDocumentId(tree)
            if (writeInTree(ctx, tree, root, name, mime, bytes)) folderLabel(tree) else null
        } catch (_: Exception) {
            null
        }
    }
    fun saveBesideVideo(ctx: Context, video: Uri, name: String, mime: String, bytes: ByteArray): String? {
        val dir = parentDir(ctx, video) ?: return null
        if (hasAllFiles() || (Build.VERSION.SDK_INT < 30 && dir.canWrite())) {
            try {
                if (dir.isDirectory) {
                    val f = uniqueFile(dir, name)
                    f.writeBytes(bytes)
                    return "a videó mellé: " + dirLabel(dir) + "/" + f.name
                }
            } catch (e: Exception) { DiagLog.swallow("OutputWriter", e) }
        }
        val parent = docIdOf(dir) ?: return null
        val tree = OutputPrefs.videoTree(ctx) ?: return null
        if (!treeContains(tree, parent)) return null
        return if (writeInTree(ctx, tree, parent, name, mime, bytes)) "a videó mellé: " + docIdLabel(parent) + "/" + name else null
    }
    fun besideVideoStatus(ctx: Context): String = when {
        hasAllFiles() -> "✓ teljes fájlhozzáférés"
        OutputPrefs.videoTree(ctx) != null -> "✓ engedélyezett mappa: " + folderLabel(OutputPrefs.videoTree(ctx)!!)
        else -> "✗ nincs engedély (mentés-ablak jelenik meg)"
    }
}
