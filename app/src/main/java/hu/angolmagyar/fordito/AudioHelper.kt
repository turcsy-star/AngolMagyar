package hu.angolmagyar.fordito

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/**
 * Hang dekódolás és 16 kHz mono 16-bit PCM konverzió fájlokhoz (SRT generálás).
 */
object AudioHelper {

    /**
     * Bármilyen támogatott hang/videó fájlból 16 kHz mono PCM (16-bit LE) raw fájlt készít.
     * Visszaadja a PCM fájlt és a minta-számot.
     */
    fun decodeToPcm16kMono(input: File, output: File, cancel: () -> Boolean = { false }): Long {
        val extractor = MediaExtractor()
        extractor.setDataSource(input.absolutePath)

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
        if (track < 0 || format == null) {
            extractor.release()
            // Talán már raw PCM – másoljuk
            input.copyTo(output, overwrite = true)
            return output.length() / 2
        }

        extractor.selectTrack(track)
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE))
            format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
        val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
            format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        // Ideiglenes: dekódolt minták (lehet float vagy 16-bit)
        val pcmChunks = ArrayList<ShortArray>()
        var totalSamples = 0L

        FileOutputStream(output).use { /* ensure file exists */ }
        // Először memóriába gyűjtjük a rövid fájlokhoz; hosszú fájlnál streamelünk
        val outStream = FileOutputStream(output)

        try {
            while (!outputDone && !cancel()) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val sampleSize = extractor.readSampleData(buf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIdx >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIdx)!!
                        if (info.size > 0) {
                            val pcm = extractPcmSamples(outBuf, info, codec.outputFormat)
                            // Resample + mono
                            val mono16k = toMono16k(pcm, sampleRateFrom(codec.outputFormat, sampleRate),
                                channelsFrom(codec.outputFormat, channelCount))
                            // write as little-endian shorts
                            val bytes = ByteArray(mono16k.size * 2)
                            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                            for (s in mono16k) bb.putShort(s)
                            outStream.write(bytes)
                            totalSamples += mono16k.size
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        // ok
                    }
                }
            }
        } finally {
            outStream.close()
            codec.stop()
            codec.release()
            extractor.release()
        }
        return totalSamples
    }

    private fun sampleRateFrom(fmt: MediaFormat, fallback: Int): Int =
        if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) else fallback

    private fun channelsFrom(fmt: MediaFormat, fallback: Int): Int =
        if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else fallback

    /** MediaCodec kimenet → FloatArray (normalizált -1..1) vagy Short tömb. */
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

    /** Multi-channel float → mono, majd 16 kHz-re simple decimation/interpolation. */
    private fun toMono16k(samples: FloatArray, srcRate: Int, channels: Int): ShortArray {
        // mono
        val mono = if (channels <= 1) {
            samples
        } else {
            val frames = samples.size / channels
            FloatArray(frames) { i ->
                var s = 0f
                for (c in 0 until channels) s += samples[i * channels + c]
                s / channels
            }
        }
        // resample to 16 kHz
        val targetRate = 16000
        if (srcRate == targetRate) {
            return FloatArray(mono.size) { mono[it] }.map { (it * 32767f).toInt().coerceIn(-32768, 32767).toShort() }.toShortArray()
        }
        val ratio = srcRate.toDouble() / targetRate
        val outLen = (mono.size / ratio).toInt().coerceAtLeast(1)
        val out = ShortArray(outLen)
        for (i in 0 until outLen) {
            val srcPos = i * ratio
            val i0 = srcPos.toInt().coerceIn(0, mono.size - 1)
            val i1 = min(i0 + 1, mono.size - 1)
            val frac = (srcPos - i0).toFloat()
            val v = mono[i0] * (1 - frac) + mono[i1] * frac
            out[i] = (v * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }
}
