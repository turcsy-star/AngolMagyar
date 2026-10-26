package hu.angolmagyar.fordito

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.os.StatFs

/**
 * Melyik felismerő motor melyik nyelvhez tartozik.
 *  - Vosk (offline modell): angol, német, francia, spanyol, olasz, ukrán, + saját magyar ZIP
 *  - Android beépített felismerő: magyar (a Vosk-nak nincs magyar modellje)
 */
object Engines {

    /** A nyelv Vosk-modellje, vagy null, ha ezt a nyelvet a rendszer-felismerő végzi. */
    fun voskSpecFor(ctx: Context, prefs: Prefs, lang: String): ModelManager.Spec? = when (lang) {
        "hu" -> if (ModelManager.isInstalled(ctx, ModelManager.HU_LARGE)) ModelManager.HU_LARGE else null
        "en" -> {
            val wantLarge = prefs.modelId == "large"
            val small = ModelManager.isInstalled(ctx, ModelManager.SMALL)
            val large = ModelManager.isInstalled(ctx, ModelManager.LARGE)
            when {
                wantLarge && large -> ModelManager.LARGE
                small -> ModelManager.SMALL
                large -> ModelManager.LARGE
                wantLarge -> ModelManager.LARGE
                else -> ModelManager.SMALL
            }
        }
        else -> ModelManager.forSourceLang(lang)
    }

    const val K_WHISPER = 0
    const val K_VOSK = 1
    const val K_SYSTEM = 2

    /** A fájl-átirat felismerője egy nyelvhez. */
    data class Asr(val kind: Int, val vosk: ModelManager.Spec?, val whisper: ModelManager.Spec?) {
        val label: String
            get() = when (kind) {
                K_WHISPER -> whisper?.label?.substringBefore("(")?.trim() ?: "Whisper"
                K_VOSK -> vosk?.label?.substringBefore("(")?.trim() ?: "Vosk"
                else -> "Android beépített felismerő"
            }
        val key: String get() = when (kind) { K_WHISPER -> "w"; K_VOSK -> "v"; else -> "s" }
    }

    /** Whisper használható-e (natív könyvtár betölt + van letöltött modell). */
    fun whisperSpec(ctx: Context): ModelManager.Spec? {
        if (!WhisperNative.available()) return null
        return ModelManager.bestWhisper(ctx, AsrPrefs.whisperId(ctx))
    }

    fun asrFor(ctx: Context, prefs: Prefs, lang: String): Asr {
        val vosk = voskSpecFor(ctx, prefs, lang)
        val voskOk = vosk != null && ModelManager.isInstalled(ctx, vosk)
        val wh = whisperSpec(ctx)
        val r = when (AsrPrefs.engine(ctx)) {
            AsrPrefs.WHISPER -> Asr(K_WHISPER, vosk, wh)
            AsrPrefs.VOSK -> if (vosk != null) Asr(K_VOSK, vosk, wh) else Asr(K_SYSTEM, null, wh)
            else -> when {
                wh != null -> Asr(K_WHISPER, vosk, wh)
                vosk != null -> Asr(K_VOSK, vosk, null)
                else -> Asr(K_SYSTEM, null, null)
            }
        }
        // Vosk kiválasztva, de nincs letöltve: Whisperre váltunk, ha az megvan
        return if (r.kind == K_VOSK && !voskOk && wh != null) Asr(K_WHISPER, vosk, wh) else r
    }

    /** Élő módban mely nyelvi oldalak kellenek: (magyar oldal, másik oldal). */
    data class Sides(val hu: Boolean, val other: Boolean, val otherLang: String)

    fun sides(prefs: Prefs): Sides {
        val dir = prefs.translateDir
        val huSource = dir == Prefs.DIR_HU_EN || prefs.meetingMode ||
            (dir == Prefs.DIR_NONE && prefs.sourceLang == "hu")
        val conv = dir == Prefs.DIR_CONVERSATION
        val otherLang = if (prefs.sourceLang == "hu") "en" else prefs.sourceLang
        return Sides(hu = huSource || conv, other = !huSource || conv, otherLang = otherLang)
    }

    /** Hiányzó követelmény szövege, vagy null, ha az élő mód indítható. */
    fun missingForLive(ctx: Context, prefs: Prefs): String? {
        if (prefs.polyMode) {
            return if (whisperSpec(ctx) == null)
                "A többnyelvű módhoz Whisper modell kell (Modellek képernyő → Whisper small ajánlott)."
            else null
        }
        val s = sides(prefs)
        if (s.other) {
            val spec = voskSpecFor(ctx, prefs, s.otherLang)
            if (spec != null && !ModelManager.isInstalled(ctx, spec)) {
                return "Hiányzik a felismerő modell: ${spec.label}. Töltsd le a Modellek képernyőn."
            }
        }
        if (s.hu && voskSpecFor(ctx, prefs, "hu") == null) {
            // Whisper telepítve → élő magyar is megy (LiveWhisperAsr), nincs szükség rendszer ASR-re
            if (whisperSpec(ctx) != null) return null
            if (!SystemAsrSupport.audioSourceSupported()) {
                return "A magyar felismeréshez Android 13 vagy újabb kell, vagy töltsd le a Whisper modellt."
            }
            if (!SystemAsrSupport.onDeviceAvailable(ctx) &&
                !(prefs.allowOnlineAsr && SystemAsrSupport.anyAvailable(ctx))
            ) {
                return "Nincs magyar felismerő. Töltsd le a Whisper modellt (ajánlott) a Modellek képernyőn."
            }
        }
        return null
    }

    /** Élő magyar oldalhoz használt motor: whisper / vosk / system. */
    fun liveHuKind(ctx: Context, prefs: Prefs): Int {
        val vosk = voskSpecFor(ctx, prefs, "hu")
        if (vosk != null && ModelManager.isInstalled(ctx, vosk)) return K_VOSK
        if (whisperSpec(ctx) != null) return K_WHISPER
        return K_SYSTEM
    }

    // ------------------------------------------------------------ készülék-felmérés

    data class Device(
        val name: String,
        val ramMb: Long,
        val cores: Int,
        val freeMb: Long,
        val sdk: Int
    ) {
        /** true, ha a nagy angol modell kényelmesen fut. */
        val canRunLarge: Boolean get() = ramMb >= 5500 && cores >= 6
        val summary: String
            get() = "$name · ${ramMb / 1024} GB RAM · $cores mag · ${freeMb / 1024} GB szabad hely"
    }

    fun device(ctx: Context): Device {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val free = try {
            val st = StatFs(Environment.getDataDirectory().path)
            st.availableBytes / (1024 * 1024)
        } catch (_: Exception) {
            0L
        }
        val name = (Build.MANUFACTURER + " " + Build.MODEL).trim()
        return Device(
            name = name.replaceFirstChar { it.uppercase() },
            ramMb = mi.totalMem / (1024 * 1024),
            cores = Runtime.getRuntime().availableProcessors(),
            freeMb = free,
            sdk = Build.VERSION.SDK_INT
        )
    }

    /** Wi-Fi (vagy nem mért) hálózaton vagyunk-e. */
    fun onUnmeteredNetwork(ctx: Context): Boolean {
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    fun online(ctx: Context): Boolean {
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }
}
