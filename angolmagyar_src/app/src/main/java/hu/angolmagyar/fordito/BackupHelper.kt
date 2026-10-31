package hu.angolmagyar.fordito

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Beállítások + átiratok mentése egyetlen zip fájlba, és visszaállítása. */
object BackupHelper {

    private const val PREFS_ENTRY = "prefs.json"
    private const val DIR_PREFIX = "transcripts/"

    fun export(ctx: Context, out: OutputStream): Int {
        var files = 0
        ZipOutputStream(out).use { zip ->
            val sp = ctx.applicationContext.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            val json = JSONObject()
            for ((k, v) in sp.all) {
                when (v) {
                    is Boolean -> json.put(k, JSONObject().put("t", "b").put("v", v))
                    is Int -> json.put(k, JSONObject().put("t", "i").put("v", v))
                    is Long -> json.put(k, JSONObject().put("t", "l").put("v", v))
                    is Float -> json.put(k, JSONObject().put("t", "f").put("v", v.toDouble()))
                    is String -> json.put(k, JSONObject().put("t", "s").put("v", v))
                }
            }
            zip.putNextEntry(ZipEntry(PREFS_ENTRY))
            zip.write(json.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            TranscriptStore.dir(ctx).listFiles()?.filter { it.isFile }?.forEach { f ->
                zip.putNextEntry(ZipEntry(DIR_PREFIX + f.name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                files++
            }
        }
        return files
    }

    /** @return a visszaállított átirat-fájlok száma */
    fun import(ctx: Context, input: InputStream): Int {
        var files = 0
        val dir = TranscriptStore.dir(ctx)
        val dirCanon = dir.canonicalPath
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.isDirectory) continue
                if (e.name == PREFS_ENTRY) {
                    val obj = JSONObject(String(zip.readBytes(), Charsets.UTF_8))
                    val ed = ctx.applicationContext.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit()
                    for (k in obj.keys()) {
                        val o = obj.getJSONObject(k)
                        when (o.optString("t")) {
                            "b" -> ed.putBoolean(k, o.getBoolean("v"))
                            "i" -> ed.putInt(k, o.getInt("v"))
                            "l" -> ed.putLong(k, o.getLong("v"))
                            "f" -> ed.putFloat(k, o.getDouble("v").toFloat())
                            "s" -> ed.putString(k, o.getString("v"))
                        }
                    }
                    ed.apply()
                } else if (e.name.startsWith(DIR_PREFIX)) {
                    val name = e.name.removePrefix(DIR_PREFIX)
                    val f = File(dir, name)
                    // zip-slip védelem
                    if (name.isBlank() || !f.canonicalPath.startsWith(dirCanon + File.separator)) continue
                    f.outputStream().use { zip.copyTo(it) }
                    files++
                }
            }
        }
        return files
    }

    /** Minden átirat törlése (a kuka is). */
    fun deleteAllTranscripts(ctx: Context): Int {
        var n = 0
        for (d in listOf(TranscriptStore.dir(ctx), TranscriptStore.trashDir(ctx))) {
            d.listFiles()?.forEach { if (it.isFile && it.delete()) n++ }
        }
        return n
    }
}
