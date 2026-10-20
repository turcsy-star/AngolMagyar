package hu.angolmagyar.fordito

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile

/**
 * Natív Vosk felismerő modellek több nyelvhez (small = mobilbarát).
 */
object ModelManager {

    data class Spec(
        val id: String,
        val label: String,
        val url: String,
        val dirName: String,
        val lang: String = "en",
        val optional: Boolean = false
    )

    val SMALL = Spec(
        "small", "Angol small (~40 MB)",
        "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
        "vosk-model-small-en-us-0.15", "en"
    )
    val LARGE = Spec(
        "large", "Angol nagy lgraph (~130 MB)",
        "https://alphacephei.com/vosk/models/vosk-model-en-us-0.22-lgraph.zip",
        "vosk-model-en-us-0.22-lgraph", "en"
    )
    val HU_SMALL = Spec(
        "hu_small", "Magyar small",
        "https://alphacephei.com/vosk/models/vosk-model-small-hu-0.15.zip",
        "vosk-model-small-hu-0.15", "hu", optional = true
    )
    val HU_LARGE = Spec(
        "hu_large", "Magyar nagy/egyedi (ZIP)",
        "", "vosk-model-hu-large", "hu", optional = true
    )
    val DE_SMALL = Spec(
        "de_small", "Német small (~44 MB)",
        "https://alphacephei.com/vosk/models/vosk-model-small-de-0.15.zip",
        "vosk-model-small-de-0.15", "de"
    )
    val FR_SMALL = Spec(
        "fr_small", "Francia small (~40 MB)",
        "https://alphacephei.com/vosk/models/vosk-model-small-fr-0.22.zip",
        "vosk-model-small-fr-0.22", "fr"
    )
    val ES_SMALL = Spec(
        "es_small", "Spanyol small (~38 MB)",
        "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip",
        "vosk-model-small-es-0.42", "es"
    )
    val IT_SMALL = Spec(
        "it_small", "Olasz small (~47 MB)",
        "https://alphacephei.com/vosk/models/vosk-model-small-it-0.22.zip",
        "vosk-model-small-it-0.22", "it"
    )
    val UK_SMALL = Spec(
        "uk_small", "Ukrán small (~137 MB)",
        "https://alphacephei.com/vosk/models/vosk-model-small-uk-v3-small.zip",
        "vosk-model-small-uk-v3-small", "uk"
    )

    val ALL = listOf(
        SMALL, LARGE, HU_SMALL, HU_LARGE,
        DE_SMALL, FR_SMALL, ES_SMALL, IT_SMALL, UK_SMALL
    )

    /** Nyelvenként ajánlott (small) modell. */
    fun forSourceLang(lang: String, preferLargeEn: Boolean = false, ctx: Context? = null): Spec {
        return when (lang) {
            "hu" -> {
                if (ctx != null && isInstalled(ctx, HU_LARGE)) HU_LARGE
                else HU_SMALL
            }
            "de" -> DE_SMALL
            "fr" -> FR_SMALL
            "es" -> ES_SMALL
            "it" -> IT_SMALL
            "uk" -> UK_SMALL
            else -> {
                if (preferLargeEn && ctx != null && isInstalled(ctx, LARGE)) LARGE
                else SMALL
            }
        }
    }

    fun byId(id: String?): Spec = ALL.firstOrNull { it.id == id } ?: SMALL

    fun forLang(lang: String, preferLarge: Boolean, ctx: Context): Spec =
        forSourceLang(lang, preferLarge && lang == "en", ctx)

    fun dir(ctx: Context, spec: Spec): File = File(File(ctx.filesDir, "models"), spec.dirName)

    fun isInstalled(ctx: Context, spec: Spec): Boolean = File(dir(ctx, spec), "am").isDirectory

    fun sizeMb(ctx: Context, spec: Spec): Long {
        val d = dir(ctx, spec)
        if (!d.exists()) return 0
        return d.walkTopDown().filter { it.isFile }.map { it.length() }.sum() / (1024 * 1024)
    }

    /** Modell mappa törlése – hely felszabadítás. */
    fun delete(ctx: Context, spec: Spec): Boolean {
        val d = dir(ctx, spec)
        if (!d.exists()) return true
        return d.deleteRecursively()
    }

    fun installedSpecs(ctx: Context): List<Spec> = ALL.filter { isInstalled(ctx, it) }

    fun download(ctx: Context, spec: Spec, onProgress: (Int, String) -> Unit) {
        if (spec.url.isBlank()) {
            throw IllegalStateException(
                "Nincs közvetlen URL. Telepítsd ZIP-ből (Beállítások)."
            )
        }
        File(ctx.filesDir, "models").mkdirs()
        val zipFile = File(ctx.cacheDir, "${spec.dirName}.zip")
        val conn = URL(spec.url).openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 180_000
        conn.connect()
        if (conn.responseCode != HttpURLConnection.HTTP_OK) {
            throw IllegalStateException("HTTP ${conn.responseCode}: ${spec.label}")
        }
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            zipFile.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                var lastPct = -1
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    val mb = done / (1024 * 1024)
                    val pct = if (total > 0) (done * 80 / total).toInt() else 0
                    if (pct != lastPct) {
                        lastPct = pct
                        onProgress(pct, "${spec.label}: $mb MB")
                    }
                }
            }
        }
        installFromZip(ctx, spec, zipFile, onProgress)
        zipFile.delete()
        onProgress(100, "Kész: ${spec.label}")
    }

    fun installFromZip(ctx: Context, spec: Spec, zipFile: File, onProgress: (Int, String) -> Unit) {
        onProgress(80, "Kicsomagolás…")
        val tmp = File(ctx.cacheDir, "unzip_tmp_${spec.id}")
        tmp.deleteRecursively()
        tmp.mkdirs()
        unzip(zipFile, tmp, onProgress)
        val found = tmp.walkTopDown().firstOrNull { File(it, "am").isDirectory }
            ?: throw IllegalStateException("Érvénytelen Vosk ZIP (nincs am mappa).")
        val target = dir(ctx, spec)
        target.deleteRecursively()
        found.copyRecursively(target, overwrite = true)
        tmp.deleteRecursively()
        if (!isInstalled(ctx, spec)) throw IllegalStateException("Telepítés sikertelen.")
    }

    private fun unzip(zip: File, dest: File, onProgress: (Int, String) -> Unit) {
        val destCanon = dest.canonicalPath
        ZipFile(zip).use { zf ->
            val entries = zf.entries().asSequence().toList()
            var i = 0
            for (e in entries) {
                val f = File(dest, e.name)
                if (!f.canonicalPath.startsWith(destCanon + File.separator) &&
                    f.canonicalPath != destCanon
                ) throw SecurityException("Érvénytelen ZIP bejegyzés")
                if (e.isDirectory) f.mkdirs()
                else {
                    f.parentFile?.mkdirs()
                    zf.getInputStream(e).use { inp -> f.outputStream().use { inp.copyTo(it) } }
                }
                i++
                if (i % 20 == 0) onProgress(80 + 20 * i / entries.size.coerceAtLeast(1), "Kicsomagolás…")
            }
        }
    }
}
