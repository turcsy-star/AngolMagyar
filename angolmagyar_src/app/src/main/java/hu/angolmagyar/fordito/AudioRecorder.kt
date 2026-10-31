package hu.angolmagyar.fordito

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * A felismerőnek átadott 16 kHz-es mono hang tömörített (AAC-LC, 24 kbps ≈ 11 MB/óra) mentése.
 * Nyers ADTS folyam: nincs konténer, ezért ha a folyamatot kilövik, a fájl akkor is lejátszható.
 * A kódolás külön szálon fut, a felismerést nem lassítja; ha lemarad, a hangot eldobja.
 */
class AudioRecorder(private val file: File, private val sampleRate: Int = 16000) {

    private val queue = ArrayBlockingQueue<ShortArray>(400)
    private val end = ShortArray(0)

    @Volatile private var running = false
    private var thread: Thread? = null

    private var codec: MediaCodec? = null
    private var out: BufferedOutputStream? = null
    private var samplesFed = 0L
    private var eosSeen = false

    fun start(): Boolean {
        return try {
            file.parentFile?.mkdirs()
            val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 24_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
            }
            val c = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            c.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            c.start()
            codec = c
            out = BufferedOutputStream(FileOutputStream(file, true), 16_384)
            running = true
            thread = Thread({ loop() }, "aac-encoder").also { it.start() }
            true
        } catch (e: Throwable) {
            AppState.addLog("AudioRecorder: ${e.message}")
            release()
            false
        }
    }

    /** A hívó szálon gyorsan visszatér; a hangot másolja. */
    fun write(data: ShortArray, len: Int) {
        if (!running || len <= 0) return
        queue.offer(data.copyOf(len)) // tele sor esetén eldobjuk
    }

    /** Lezárás (nem blokkolja a hívót). */
    fun stop() {
        if (!running) return
        running = false
        if (!queue.offer(end)) {
            // tele a sor: a ciklus a running=false miatt úgyis kilép, ha kiürült
        }
    }

    private fun loop() {
        try {
            while (true) {
                val chunk = queue.poll(250, TimeUnit.MILLISECONDS)
                if (chunk == null) {
                    if (!running) break
                    continue
                }
                if (chunk === end) break
                feed(chunk)
            }
            finish()
        } catch (e: Throwable) {
            AppState.addLog("AudioRecorder loop: ${e.message}")
        } finally {
            release()
        }
    }

    private fun feed(chunk: ShortArray) {
        val c = codec ?: return
        val bytes = ByteArray(chunk.size * 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(chunk)
        var off = 0
        while (off < bytes.size) {
            val idx = c.dequeueInputBuffer(10_000)
            if (idx < 0) {
                drain()
                continue
            }
            val buf = c.getInputBuffer(idx) ?: return
            buf.clear()
            val n = minOf(buf.capacity(), bytes.size - off)
            buf.put(bytes, off, n)
            val pts = samplesFed * 1_000_000L / sampleRate
            c.queueInputBuffer(idx, 0, n, pts, 0)
            samplesFed += n / 2
            off += n
            drain()
        }
    }

    private fun finish() {
        val c = codec ?: return
        var tries = 0
        while (tries < 100) {
            val idx = c.dequeueInputBuffer(20_000)
            if (idx >= 0) {
                val pts = samplesFed * 1_000_000L / sampleRate
                c.queueInputBuffer(idx, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                break
            }
            drain()
            tries++
        }
        var waits = 0
        while (!eosSeen && waits < 100) {
            drain()
            if (!eosSeen) Thread.sleep(20)
            waits++
        }
        try {
            out?.flush()
        } catch (_: Exception) {
        }
    }

    private fun drain() {
        val c = codec ?: return
        val info = MediaCodec.BufferInfo()
        var wrote = false
        while (true) {
            val oi = c.dequeueOutputBuffer(info, 0)
            if (oi >= 0) {
                val isConfig = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                if (!isConfig && info.size > 0) {
                    val ob = c.getOutputBuffer(oi)
                    if (ob != null) {
                        ob.position(info.offset)
                        ob.limit(info.offset + info.size)
                        val frame = ByteArray(info.size)
                        ob.get(frame)
                        out?.write(adtsHeader(frame.size))
                        out?.write(frame)
                        wrote = true
                    }
                }
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) eosSeen = true
                c.releaseOutputBuffer(oi, false)
            } else if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                continue
            } else {
                break
            }
        }
        if (wrote) {
            try {
                out?.flush()
            } catch (_: Exception) {
            }
        }
    }

    /** 7 bájtos ADTS fejléc: MPEG-4, AAC-LC, 16 kHz (index 8), mono. */
    private fun adtsHeader(aacLen: Int): ByteArray {
        val frameLen = aacLen + 7
        val freqIdx = 8 // 16000 Hz
        val profile = 2 // AAC-LC
        val chan = 1
        return byteArrayOf(
            0xFF.toByte(),
            0xF1.toByte(),
            (((profile - 1) shl 6) or (freqIdx shl 2) or (chan shr 2)).toByte(),
            (((chan and 3) shl 6) or (frameLen shr 11)).toByte(),
            ((frameLen shr 3) and 0xFF).toByte(),
            (((frameLen and 7) shl 5) or 0x1F).toByte(),
            0xFC.toByte()
        )
    }

    private fun release() {
        try {
            codec?.stop()
        } catch (_: Exception) {
        }
        try {
            codec?.release()
        } catch (_: Exception) {
        }
        codec = null
        try {
            out?.close()
        } catch (_: Exception) {
        }
        out = null
        running = false
    }
}
