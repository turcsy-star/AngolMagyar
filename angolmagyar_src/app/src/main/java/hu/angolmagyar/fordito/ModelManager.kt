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
        val optional: Boolean = false,
        /** Egyfájlos modell (Whisper ggml): a fájl neve; null = Vosk ZIP-mappa. */
        val file: String? = null,
        val approxMb: Int = 0
    ) {
        val isWhisper: Boolean get() = file != null
    }

    private const val HF = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/"

    val WH_BASE = Spec("wh_base", "Whisper base (~57 MB)", HF + "ggml-base-q5_1.bin",
        "wh-base", "multi", file = "ggml-base-q5_1.bin", approxMb = 57)
    val WH_SMALL = Spec("wh_small", "Whisper small (~181 MB)", HF + "ggml-small-q5_1.bin",
        "wh-small", "multi", file = "ggml-small-q5_1.bin", approxMb = 181)
    val WH_TURBO = Spec("wh_turbo", "Whisper large-v3-turbo (~574 MB)", HF + "ggml-large-v3-turbo-q5_0.bin",
        "wh-turbo", "multi", file = "ggml-large-v3-turbo-q5_0.bin", approxMb = 574)
    val WH_MEDIUM = Spec("wh_medium", "Whisper medium (~515 MB)", HF + "ggml-medium-q5_0.bin",
        "wh-medium", "multi", file = "ggml-medium-q5_0.bin", approxMb = 515)
    val WH_LARGE = Spec("wh_large", "Whisper large-v3 (~1,1 GB)", HF + "ggml-large-v3-q5_0.bin",
        "wh-large", "multi", file = "ggml-large-v3-q5_0.bin", approxMb = 1080)

    val WHISPER = listOf(WH_SMALL, WH_TURBO, WH_MEDIUM, WH_LARGE, WH_BASE)

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
        SMALL, LARGE, HU_LARGE,
        DE_SMALL, FR_SMALL, ES_SMALL, IT_SMALL, UK_SMALL
    )

    /** Nyelvenként ajánlott (small) modell. */
    fun forSourceLang(lang: String, preferLargeEn: Boolean = false, ctx: Context? = null): Spec {
        return when (lang) {
            "hu" -> HU_LARGE
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

    fun byId(id: String?): Spec =
        ALL.firstOrNull { it.id == id } ?: WHISPER.firstOrNull { it.id == id } ?: SMALL

    /** A telepített, legnagyobb használható Whisper modell (preferált id előnyben), vagy null. */
    fun bestWhisper(ctx: Context, preferredId: String?): Spec? {
        WHISPER.firstOrNull { it.id == preferredId && isInstalled(ctx, it) }?.let { return it }
        return listOf(WH_LARGE, WH_TURBO, WH_MEDIUM, WH_SMALL, WH_BASE).firstOrNull { isInstalled(ctx, it) }
    }

    fun forLang(lang: String, preferLarge: Boolean, ctx: Context): Spec =
        forSourceLang(lang, preferLarge && lang == "en", ctx)

    fun dir(ctx: Context, spec: Spec): File =
        if (spec.file != null) File(File(File(ctx.filesDir, "models"), "whisper"), spec.file)
        else File(File(ctx.filesDir, "models"), spec.dirName)

    fun isInstalled(ctx: Context, spec: Spec): Boolean =
        if (spec.file != null) dir(ctx, spec).let { it.isFile && it.length() > 20L * 1024 * 1024 || (it.isFile && spec.approxMb < 20) }
        else File(dir(ctx, spec), "am").isDirectory

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

    fun installedSpecs(ctx: Context): List<Spec> = (ALL + WHISPER).filter { isInstalled(ctx, it) }

    /** Szüneteltetés / megszakítás jelzése a letöltő szálnak. */
    class DlControl {
        @Volatile var paused = false
        @Volatile var conn: HttpURLConnection? = null

        fun pause() {
            paused = true
            val c = conn
            if (c != null) Thread { try { c.disconnect() } catch (e: Exception) { DiagLog.swallow("ModelManager", e) } }.start()
        }
    }

    class PausedException : Exception("szüneteltetve")

    private fun partFile(ctx: Context, spec: Spec): File {
        val d = File(ctx.filesDir, "dl")
        d.mkdirs()
        return File(d, "${spec.dirName}.zip.part")
    }

    /** Félbehagyott (folytatható) letöltés mérete bájtban. */
    fun partialBytes(ctx: Context, spec: Spec): Long = partFile(ctx, spec).let { if (it.exists()) it.length() else 0L }

    /** Egyszerű változat (Beállítások): folyamat 0–100%. */
    fun download(ctx: Context, spec: Spec, onProgress: (Int, String) -> Unit) {
        download(ctx, spec, DlControl(), { done, total ->
            val mb = done / (1024 * 1024)
            onProgress(if (total > 0) (done * 80 / total).toInt() else 0, "${spec.label}: $mb MB")
        }, { onProgress(80, it) })
        onProgress(100, "Kész: ${spec.label}")
    }

    /**
     * Folytatható letöltés (HTTP Range): a félbehagyott fájl a "dl" mappában marad,
     * újraindításkor onnan megy tovább. Kapcsolat-megszakadásnál magától újrapróbál.
     */
    fun download(
        ctx: Context, spec: Spec, ctl: DlControl,
        onBytes: (done: Long, total: Long) -> Unit, onStage: (String) -> Unit
    ) {
        if (spec.url.isBlank()) {
            throw IllegalStateException("Nincs közvetlen URL. Telepítsd ZIP-ből (Beállítások).")
        }
        File(ctx.filesDir, "models").mkdirs()
        val part = partFile(ctx, spec)
        var attempt = 0
        while (true) {
            try {
                fetchPart(spec, part, ctl, onBytes)
                break
            } catch (e: PausedException) {
                throw e
            } catch (e: java.io.IOException) {
                if (ctl.paused) throw PausedException()
                attempt++
                if (attempt >= 4) throw e
                onStage("Megszakadt a kapcsolat, újrapróbálom ($attempt/3)…")
                try { Thread.sleep(2000L * attempt) } catch (_: InterruptedException) {}
            }
        }
        if (spec.file != null) {
            val target = dir(ctx, spec)
            target.parentFile?.mkdirs()
            target.delete()
            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true)
                part.delete()
            }
            return
        }
        onStage("Kicsomagolás…")
        try {
            installFromZip(ctx, spec, part) { _, m -> onStage(m) }
        } catch (e: Exception) {
            part.delete() // sérült letöltés: következőre tiszta lappal indul
            throw e
        }
        part.delete()
    }

    private fun fetchPart(spec: Spec, part: File, ctl: DlControl, onBytes: (Long, Long) -> Unit) {
        var have = if (part.exists()) part.length() else 0L
        val conn = URL(spec.url).openConnection() as HttpURLConnection
        ctl.conn = conn
        conn.connectTimeout = 20_000
        conn.readTimeout = 30_000
        if (have > 0) conn.setRequestProperty("Range", "bytes=$have-")
        try {
            if (ctl.paused) throw PausedException()
            conn.connect()
            val code = conn.responseCode
            if (code == 416) { // már teljes
                onBytes(have, have)
                return
            }
            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                throw IllegalStateException("HTTP $code: ${spec.label}")
            }
            val append = code == HttpURLConnection.HTTP_PARTIAL
            if (!append) have = 0
            val len = conn.contentLengthLong
            val total = if (len > 0) have + len else -1L
            var done = have
            conn.inputStream.use { input ->
                java.io.FileOutputStream(part, append).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var last = 0L
                    while (true) {
                        if (ctl.paused) throw PausedException()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val now = System.nanoTime()
                        if (now - last > 250_000_000L) {
                            last = now
                            onBytes(done, total)
                        }
                    }
                }
            }
            if (total > 0 && done < total) throw java.io.IOException("hiányos letöltés")
            onBytes(done, total)
        } finally {
            try { conn.disconnect() } catch (e: Exception) { DiagLog.swallow("ModelManager", e) }
            ctl.conn = null
        }
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
