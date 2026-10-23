package hu.angolmagyar.fordito

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * Háttérfutás-kényszerítés agresszív energiakezelésű telefonokra (Honor, Oppo, Xiaomi…): a rendszer ezeken
 * zárolt képernyőnél az előtér-szolgáltatást és a wake lockot is lefagyaszthatja, a hangot lejátszó appot viszont
 * nem. Ezért a feldolgozás alatt egy gyakorlatilag néma (±1 LSB), kis mintavételű hangfolyam megy.
 * Nem kér hangfókuszt, így más appok lejátszását nem szakítja meg.
 */
object KeepAlive {
    @Volatile private var running = false
    private var thread: Thread? = null

    @Synchronized
    fun start() {
        if (running) return
        running = true
        DiagLog.log("KEEPALIVE: néma hangfolyam indul")
        thread = Thread({ loop() }, "srt-keepalive").also { it.isDaemon = true; it.start() }
    }

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        thread?.interrupt()
        thread = null
        DiagLog.log("KEEPALIVE: leáll")
    }

    private fun loop() {
        var track: AudioTrack? = null
        try {
            val rate = 8000
            val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                .coerceAtLeast(4096)
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(rate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(min)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track.play()
            val buf = ShortArray(rate / 4) { if (it % 2 == 0) 1 else -1 }
            while (running) {
                val n = track.write(buf, 0, buf.size)
                if (n < 0) {
                    try { Thread.sleep(500) } catch (_: InterruptedException) { break }
                }
            }
        } catch (e: Throwable) {
            DiagLog.log("KEEPALIVE hiba: ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            try { track?.stop() } catch (_: Throwable) {}
            try { track?.release() } catch (_: Throwable) {}
        }
    }
}
