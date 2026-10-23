package hu.angolmagyar.fordito

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor

/**
 * Hang dekódolás és 16 kHz mono 16-bit PCM konverzió fájlokhoz (SRT generálás).
 */
object AudioHelper {

    /**
     * Közvetlenül a fájlból (URI) dekódol: nincs másolás a gyorsítótárba, ezért nagy videóknál
     * is azonnal indul. Ha a közvetlen olvasás nem megy, a fájlt átmásolja, és onnan dolgozik.
     *
     * @param onProgress 0–100 a hang hossza alapján (ha a hossz ismert), különben nem hívódik
     */
    fun decodeUriToPcm16kMono(
        ctx: Context, uri: Uri, output: File,
        cancel: () -> Boolean = { false },
        onProgress: ((Int) -> Unit)? = null
    ): Long {
        // 1) közvetlen fájlleíró
        try {
            ctx.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val ex = MediaExtractor()
                try {
                    ex.setDataSource(pfd.fileDescriptor)
                    val n = decodeCore(ex, output, cancel, onProgress)
                    if (n >= 0) return n
                } finally {
                    try { ex.release() } catch (_: Exception) {}
                }
            }
        } catch (e: InterruptedException) {
            throw e
        } catch (_: Exception) {
            // tovább a másolós úton
        }
        if (cancel()) throw InterruptedException("Megszakítva")
        // 2) tartalék: másolás a cache-be
        val cache = File(ctx.cacheDir, "input_${System.currentTimeMillis()}")
        try {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                cache.outputStream().use { input.copyTo(it) }
            } ?: throw IllegalStateException("Nem olvasható a fájl.")
            if (cancel()) throw InterruptedException("Megszakítva")
            val ex = MediaExtractor()
            try {
                ex.setDataSource(cache.absolutePath)
                val n = decodeCore(ex, output, cancel, onProgress)
                if (n < 0) throw IllegalStateException("A fájlban nincs hangsáv, vagy nem támogatott a formátuma.")
                return n
            } finally {
                try { ex.release() } catch (_: Exception) {}
            }
        } finally {
            try { cache.delete() } catch (_: Exception) {}
        }
    }

    /**
     * Bármilyen támogatott hang/videó fájlból 16 kHz mono PCM (16-bit LE) raw fájlt készít.
     * Visszaadja a minta-számot.
     */
    fun decodeToPcm16kMono(input: File, output: File, cancel: () -> Boolean = { false }): Long {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(input.absolutePath)
            val n = decodeCore(extractor, output, cancel, null)
            if (n >= 0) return n
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }
        // Talán már raw PCM – másoljuk
        input.copyTo(output, overwrite = true)
        return output.length() / 2
    }

    /**
     * A közös dekódoló. A forrás (extractor) már be van állítva.
     * @return a kiírt minták száma, vagy -1, ha nincs hangsáv
     */
    private fun decodeCore(
        extractor: MediaExtractor, output: File,
        cancel: () -> Boolean, onProgress: ((Int) -> Unit)?
    ): Long {
        var track = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                track = i
                format = f
                break
            }
        }
        if (track < 0 || format == null) return -1L

        extractor.selectTrack(track)
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION))
            format.getLong(MediaFormat.KEY_DURATION) else 0L
        val fallbackRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE))
            format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
        val fallbackCh = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
            format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var totalSamples = 0L
        var lastPct = -1
        var resampler: Resampler? = null
        var resRate = -1
        var bytes = ByteArray(0)
        val outStream = BufferedOutputStream(FileOutputStream(output), 256 * 1024)

        try {
            while (!outputDone && !cancel()) {
                var fed = false
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(0)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val sampleSize = extractor.readSampleData(buf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val t = extractor.sampleTime
                            codec.queueInputBuffer(inIdx, 0, sampleSize, t, 0)
                            extractor.advance()
                            if (onProgress != null && durationUs > 0 && t >= 0) {
                                val pct = (t * 100L / durationUs).toInt().coerceIn(0, 100)
                                if (pct != lastPct) { lastPct = pct; onProgress(pct) }
                            }
                        }
                        fed = true
                    }
                }

                // ha most adtunk be adatot, ne várjunk a kimenetre; különben rövid várakozás
                val outIdx = codec.dequeueOutputBuffer(info, if (fed) 0 else 5_000)
                if (outIdx >= 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)
                    if (outBuf != null && info.size > 0) {
                        val fmt = codec.getOutputFormat(outIdx)
                        val rate = if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE))
                            fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) else fallbackRate
                        val ch = if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
                            fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else fallbackCh
                        if (resampler == null || resRate != rate) {
                            resampler = Resampler(rate)
                            resRate = rate
                        }
                        val mono = toMonoFloat(extractPcmSamples(outBuf, info, fmt), ch)
                        val shorts = resampler.process(mono)
                        if (shorts.isNotEmpty()) {
                            if (bytes.size < shorts.size * 2) bytes = ByteArray(shorts.size * 2)
                            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                            for (s in shorts) bb.putShort(s)
                            outStream.write(bytes, 0, shorts.size * 2)
                            totalSamples += shorts.size
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
            if (cancel()) throw InterruptedException("Megszakítva")
            onProgress?.invoke(100)
        } finally {
            try { outStream.close() } catch (_: Exception) {}
            try { codec.stop() } catch (_: Exception) {}
            try { codec.release() } catch (_: Exception) {}
        }
        return totalSamples
    }

    /** MediaCodec kimenet → FloatArray (normalizált -1..1). */
    private fun extractPcmSamples(buf: ByteBuffer, info: MediaCodec.BufferInfo, fmt: MediaFormat): FloatArray {
        buf.position(info.offset)
        buf.limit(info.offset + info.size)
        val encoding = if (fmt.containsKey(MediaFormat.KEY_PCM_ENCODING))
            fmt.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT

        return when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> {
                val n = info.size / 4
                val arr = FloatArray(n)
                buf.order(ByteOrder.nativeOrder()).asFloatBuffer().get(arr)
                arr
            }
            AudioFormat.ENCODING_PCM_8BIT -> {
                val n = info.size
                FloatArray(n) { i ->
                    ((buf.get(info.offset + i).toInt() and 0xff) - 128) / 128f
                }
            }
            else -> { // 16-bit
                val n = info.size / 2
                val arr = FloatArray(n)
                val sb = buf.order(ByteOrder.nativeOrder()).asShortBuffer()
                for (i in 0 until n) arr[i] = sb.get(i) / 32768f
                arr
            }
        }
    }

    /** Többcsatornás → mono (átlag). */
    private fun toMonoFloat(samples: FloatArray, channels: Int): FloatArray {
        if (channels <= 1) return samples
        val frames = samples.size / channels
        return FloatArray(frames) { i ->
            var s = 0f
            for (c in 0 until channels) s += samples[i * channels + c]
            s / channels
        }
    }

    /**
     * Folytonos (blokkhatárokon átívelő) lineáris újramintavételező 16 kHz-re. A korábbi, blokkonként
     * újrainduló változat minden blokknál egy kevés mintát elvesztett, ami hosszú felvételnél másodperceket
     * csúsztatott az időbélyegeken.
     */
    private class Resampler(srcRate: Int) {
        private val ratio = srcRate.toDouble() / 16000.0
        private var pos = 0.0
        private var prev = 0f

        fun process(x: FloatArray): ShortArray {
            val n = x.size
            if (n == 0) return ShortArray(0)
            val out = ShortArray((n / ratio).toInt() + 4)
            var k = 0
            while (true) {
                val i0 = floor(pos).toInt()
                if (i0 + 1 >= n) break
                val a = if (i0 < 0) prev else x[i0]
                val b = x[i0 + 1]
                val frac = (pos - i0).toFloat()
                val v = a * (1f - frac) + b * frac
                if (k >= out.size) break
                out[k++] = (v * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                pos += ratio
            }
            prev = x[n - 1]
            pos -= n
            return if (k == out.size) out else out.copyOf(k)
        }
    }
}
