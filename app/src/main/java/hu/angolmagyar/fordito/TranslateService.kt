package hu.angolmagyar.fordito

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.sqrt

private class AppFailure(val code: String, message: String) : Exception(message)

/**
 * Előtérszolgáltatás: hangot rögzít (belső hang vagy mikrofon), Vosk-kal angolul felismeri,
 * ML Kit-tel magyarra fordítja, és lebegő feliratként megjeleníti. Minden offline.
 */
class TranslateService : Service(), Overlay.Callbacks {

    companion object {
        const val ACTION_START = "hu.angolmagyar.fordito.START"
        const val ACTION_STOP = "hu.angolmagyar.fordito.STOP"
        const val ACTION_TOGGLE_PAUSE = "hu.angolmagyar.fordito.TOGGLE_PAUSE"
        const val EXTRA_MODE = "mode"
        const val EXTRA_MODEL_PATH = "model_path"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "projection_data"
        const val EXTRA_TEST = "test"
        const val MODE_INTERNAL = "internal"
        const val MODE_MIC = "mic"
        const val MODE_PTT = "ptt"  // push-to-talk mikrofon
        const val ACTION_PTT_DOWN = "hu.angolmagyar.fordito.PTT_DOWN"
        const val ACTION_PTT_UP = "hu.angolmagyar.fordito.PTT_UP"
        const val ACTION_SWITCH_DIR = "hu.angolmagyar.fordito.SWITCH_DIR"

        private const val CHANNEL_ID = "translate"
        private const val NOTIFICATION_ID = 1
        private const val CLEAR_AFTER_MS = 8000L
        private const val TEST_DURATION_MS = 10_000L
        private const val SILENCE_WARN_MS = 15_000L
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs

    @Volatile private var active = false
    private var cleaned = false
    private var isTest = false

    private var worker: Thread? = null
    private var projection: MediaProjection? = null
    private var overlay: Overlay? = null
    private var translator: Translator? = null
    private var store: TranscriptStore? = null
    private var audioRec: AudioRecorder? = null
    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var tts: TtsHelper? = null

    @Volatile private var translatorReady = false
    @Volatile private var paused = false
    @Volatile private var maxLevel = 0f
    @Volatile private var pttActive = false  // push-to-talk: csak true esetén dolgozunk
    @Volatile private var conversationHuToEn = false  // beszélgetés módban váltott irány
    @Volatile private var autoDual = false  // tolmács/auto: mindkét nyelv felismerője hallgat
    private val asrExec = Executors.newSingleThreadExecutor()
    private val delayQueue = ArrayDeque<Triple<Long, String, String>>()  // showAt, en, hu
    private var sentenceBuf: SentenceBuffer? = null
    private val delayRunnable = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            while (delayQueue.isNotEmpty() && delayQueue.first().first <= now) {
                val (_, en, hu) = delayQueue.removeFirst()
                commitFinalNow(en, hu)
            }
            if (delayQueue.isNotEmpty()) {
                val next = delayQueue.first().first - now
                main.postDelayed(this, next.coerceAtLeast(50))
            }
        }
    }

    private var enRules: List<Pair<Regex, String>> = emptyList()
    private var huRules: List<Pair<Regex, String>> = emptyList()
    private var vocabExtra: List<Pair<String, String>> = emptyList()
    private var partialIntervalMs = 900L

    // --- Felirat-állapot (csak a főszálon módosítjuk) ---
    private val reqCounter = AtomicInteger(0)
    private var lastFinalId = 0
    private val history = ArrayDeque<String>()
    private var curEn = ""
    private var curHu = ""
    private var lastEn = ""
    private var lastPartialAt = 0L
    private val warnings = LinkedHashMap<String, String>()
    private val testEn = ArrayList<String>()
    private val testHu = ArrayList<String>()

    private val clearRunnable = Runnable {
        history.clear()
        curEn = ""
        curHu = ""
        lastEn = ""
        overlay?.setContent("", if (paused) listOf("⏸ Szünet") else emptyList())
    }

    private val batteryRunnable = object : Runnable {
        override fun run() {
            checkBattery()
            main.postDelayed(this, 60_000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        WearBridge.start(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                shutdown()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_PAUSE -> {
                togglePause()
                return START_NOT_STICKY
            }
            ACTION_PTT_DOWN -> {
                pttActive = true
                main.post { overlay?.setPttActive(true) }
                return START_NOT_STICKY
            }
            ACTION_PTT_UP -> {
                pttActive = false
                main.post { overlay?.setPttActive(false) }
                return START_NOT_STICKY
            }
            ACTION_SWITCH_DIR -> {
                if (prefs.isConversation()) {
                    conversationHuToEn = !conversationHuToEn
                    main.post {
                        reinitTranslatorForConversation()
                        showMessage(if (conversationHuToEn) "Irány: Magyar → Angol" else "Irány: Angol → Magyar", 2000)
                        try {
                            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                                .notify(NOTIFICATION_ID, buildNotification())
                        } catch (_: Exception) {}
                    }
                }
                return START_NOT_STICKY
            }
        }
        if (active) return START_NOT_STICKY

        val mode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_MIC
        val modelPath = intent?.getStringExtra(EXTRA_MODEL_PATH)
        isTest = intent?.getBooleanExtra(EXTRA_TEST, false) ?: false
        AppState.lastError = null
        AppState.testResult = null

        createChannel()
        val type = if (mode == MODE_INTERNAL) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
        } catch (e: Exception) {
            AppState.lastError = AppError(
                if (mode == MODE_MIC) AppState.ERR_NO_MIC else AppState.ERR_OTHER,
                "Nem sikerült elindítani a szolgáltatást: ${e.message}"
            )
            stopSelf()
            return START_NOT_STICKY
        }

        if (!Settings.canDrawOverlays(this)) {
            fail(AppState.ERR_NO_OVERLAY, "Engedélyezd a megjelenítést más appok fölött.")
            return START_NOT_STICKY
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            fail(AppState.ERR_NO_MIC, "Hiányzik a mikrofon-engedély.")
            return START_NOT_STICKY
        }

        if (mode == MODE_INTERNAL) {
            val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
            val data = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_DATA, Intent::class.java) }
            if (data == null) {
                fail(AppState.ERR_PROJECTION, "Hiányzik a belső hang rögzítésének engedélye.")
                return START_NOT_STICKY
            }
            try {
                val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val p = mpm.getMediaProjection(resultCode, data)
                p.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        main.post {
                            AppState.lastError = AppError(
                                AppState.ERR_PROJECTION,
                                "A belső hang rögzítése megszakadt (Android leállította). Indítsd újra a belső hang módot."
                            )
                            setWarning("projection", "Belső hang megszakadt – indítsd újra.")
                            toast("A képernyő-/hangrögzítés leállt. Indítsd újra a fordítást.")
                            shutdown()
                        }
                    }
                }, main)
                projection = p
            } catch (e: Exception) {
                fail(AppState.ERR_PROJECTION, "A belső hang rögzítése nem indult el: ${e.message}")
                return START_NOT_STICKY
            }
        }

        active = true
        paused = false
        val usePtt = mode == MODE_PTT || (mode == MODE_MIC && prefs.pushToTalk)
        pttActive = !usePtt  // ha PTT, induláskor nem hallgat
        val sidesNow = Engines.sides(prefs)
        conversationHuToEn = if (prefs.isConversation()) prefs.convStartHu else sidesNow.hu
        autoDual = prefs.isConversation() && prefs.autoLangSwitch
        acquireWakeLock()
        AppState.running = true
        AppState.paused = false
        AppState.mode = mode
        AppState.updateLevel(0f)
        AppState.hint = null
        AppState.sessionStartedAt = SystemClock.elapsedRealtime()
        AppState.lastCaptionHu = ""
        AppState.lastCaptionEn = ""
        AppState.notifyListeners()
        AppState.notifyTile(this)
        if (!isTest) prefs.lastMode = mode

        prefs.forcedEco = false
        enRules = Fixes.parse(prefs.enFixes)
        huRules = Fixes.parse(prefs.huFixes)
        vocabExtra = TextPolish.parseVocab(prefs.customVocab)
        partialIntervalMs = if (prefs.eco || prefs.forcedEco) 2500L else 1200L
        sentenceBuf = SentenceBuffer(flushMs = if (prefs.eco) 2800L else 2000L) { txt, isFinal ->
            main.post {
                if (!active || paused) return@post
                // Beszélgetés mód: ML Kit Language ID + heurisztika (async)
                if (isFinal && prefs.isConversation() && prefs.autoLangSwitch && !autoDual) {
                    val snapHu = conversationHuToEn
                    val suggest: (String, Boolean, (Boolean?) -> Unit) -> Unit =
                        if (prefs.useMlLangId) LanguageIdHelper::suggestConversationHuToEnAsync
                        else { t, cur, cb -> cb(LanguageHint.suggestConversationHuToEn(t, cur)) }
                    suggest(txt, snapHu) { wantHu ->
                        if (wantHu == null || wantHu == conversationHuToEn || !active) return@suggest
                        main.post {
                            if (!active || wantHu == conversationHuToEn) return@post
                            conversationHuToEn = wantHu
                            reinitTranslatorForConversation()
                            showMessage(
                                if (wantHu) "Auto: Magyar → Angol" else "Auto: Angol → Magyar",
                                1800
                            )
                            try {
                                (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                                    .notify(NOTIFICATION_ID, buildNotification())
                            } catch (_: Exception) {}
                        }
                    }
                }
                val srcRules = if (conversationHuToEn) huRules else enRules
                var polished = Fixes.apply(txt, srcRules)
                polished = if (conversationHuToEn) TextPolish.polishHungarian(polished, vocabExtra)
                else TextPolish.polishEnglish(polished, vocabExtra)
                if (TextPolish.isNoise(polished) && isFinal) return@post
                if (isFinal) {
                    lastEn = polished
                    curEn = ""
                    curHu = ""
                    translate(polished, true)
                } else {
                    if (polished != curEn) {
                        curEn = polished
                        render()
                        translate(polished, false)
                    }
                }
            }
        }
        if (!isTest && prefs.saveTranscript) {
            val st = TranscriptStore(this, prefs.resumeLast)
            store = st
            // Hangmentés (opcionális): folytatott fájlnál nem illeszthető az időbélyegekhez
            if (prefs.saveAudio && !st.isResumed()) {
                val rec = AudioRecorder(st.audioFile())
                if (rec.start()) audioRec = rec
            }
        }

        overlay = Overlay(this, prefs, this).also { it.add() }
        showMessage(if (isTest) "Teszt: mondj angolul valamit (10 mp)…" else "Betöltés…", 10_000)
        initTranslator()
        registerThermal()
        batteryRunnable.run()
        startWorker(mode, modelPath)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    // ------------------------------------------------------- Overlay.Callbacks

    override fun onTogglePause() = togglePause()

    override fun onSizeDelta(delta: Int) {
        prefs.textSizeSp = prefs.textSizeSp + delta
        overlay?.applyStyle()
    }

    override fun onClose() = shutdown()

    override fun onPttDown() {
        pttActive = true
        overlay?.setPttActive(true)
    }

    override fun onPttUp() {
        pttActive = false
        overlay?.setPttActive(false)
    }

    override fun onSwitchDirection() {
        if (!prefs.isConversation() && !prefs.needsTranslation()) return
        conversationHuToEn = !conversationHuToEn
        reinitTranslatorForConversation()
        Haptics.tick(this)
        showMessage(
            if (conversationHuToEn) "Irány: Magyar → Angol" else "Irány: Angol → Magyar",
            2000
        )
    }

    override fun onDelayDelta(deltaMs: Int) {
        prefs.delayMs = (prefs.delayMs + deltaMs).coerceIn(0, 3000)
        Haptics.tick(this)
        showMessage("Késleltetés: ${prefs.delayMs} ms", 1500)
    }

    override fun onSaveFavorite() {
        val hu = AppState.lastCaptionHu
        val en = AppState.lastCaptionEn
        if (hu.isBlank() && en.isBlank()) return
        Favorites.add(this, en, hu)
        try {
            val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("caption", (en + "\n" + hu).trim()))
        } catch (_: Exception) {
        }
        Haptics.click(this)
        showMessage(getString(R.string.svc_favorite_saved), 1500)
    }

    /** Értesítés a munkamenet végén: az átirat mentve, koppintásra megnyílik. */
    private fun notifySaved(minutes: Int) {
        try {
            val pi = PendingIntent.getActivity(
                this, 7,
                Intent(this, TranscriptActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_translate)
                .setContentTitle(getString(R.string.svc_saved_title))
                .setContentText(getString(R.string.svc_saved_text, minutes))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(2, n)
        } catch (_: Exception) {
        }
    }

    override fun onToggleEnglish() {
        prefs.showEnglish = !prefs.showEnglish
        Haptics.tick(this)
        showMessage(if (prefs.showEnglish) "Angol sor: be" else "Angol sor: ki", 1500)
        render()
    }

    private fun togglePause() {
        if (!active) return
        paused = !paused
        AppState.paused = paused
        AppState.notifyListeners()
        overlay?.setPaused(paused)
        main.removeCallbacks(clearRunnable)
        if (paused) {
            history.clear()
            curEn = ""
            curHu = ""
            lastEn = ""
            overlay?.setContent("", listOf("⏸ Szünet"))
        } else {
            showMessage("Hallgatom…", 2000)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    // ------------------------------------------------------------------ fordító

    private fun initTranslator() {
        if (prefs.polyMode || !prefs.needsTranslation()) {
            translatorReady = true
            return
        }
        val src = mlKitLang(if (conversationHuToEn) Prefs.LANG_HU else prefs.sourceLang)
        val tgt = mlKitLang(
            if (conversationHuToEn) Prefs.LANG_EN
            else if (prefs.isHuToEn()) Prefs.LANG_EN
            else prefs.targetLang
        )
        val t = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(src)
                .setTargetLanguage(tgt)
                .build()
        )
        translator = t
        t.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener { translatorReady = true }
            .addOnFailureListener {
                AppState.lastError = AppError(
                    AppState.ERR_NO_TRANSLATOR,
                    "A fordítómodell nincs letöltve, ezért csak a forrásszöveg látszik."
                )
                setWarning("translator", "A fordítómodell hiányzik – csak forrásszöveg látszik.")
            }
    }

    /** en: a (javított) angol szöveg. */
    private fun translate(en: String, isFinal: Boolean) {
        val id = reqCounter.incrementAndGet()
        if (isFinal) lastFinalId = id

        val t = translator
        if (!translatorReady || t == null) {
            if (isFinal) pushFinal(en, en)
            return
        }
        t.translate(en)
            .addOnSuccessListener { raw ->
                if (!active) return@addOnSuccessListener
                val tgtRules = if (conversationHuToEn) enRules else huRules
                var hu = Fixes.apply(raw, tgtRules)
                hu = if (conversationHuToEn) TextPolish.polishEnglish(hu, vocabExtra)
                else TextPolish.polishHungarian(hu, vocabExtra)
                if (isFinal) {
                    // locked terms a forráson már lefutott; céloldalon is
                    pushFinal(en, hu)
                } else if (id > lastFinalId) {
                    curHu = hu
                    render()
                }
            }
            .addOnFailureListener { /* egy elmaradt rész nem kritikus */ }
    }

    // ------------------------------------------------------------- felirat-állapot

    /** Egy felismerő végleges szövege. isHu: melyik nyelvi oldal felismerőjétől jött. */
    private fun asrFinal(isHu: Boolean, text: String) {
        if (!autoDual) {
            onFinal(text)
            return
        }
        // Tolmács/auto: mindkét felismerő hallgat; a szöveg nyelve dönti el, melyik számít.
        try { asrExec.execute {
            val l = if (prefs.useMlLangId) LanguageIdHelper.identifyBlocking(text) else null
            val huText: Boolean? = when {
                l == "hu" -> true
                l != null -> false
                else -> LanguageHint.suggestConversationHuToEn(text, conversationHuToEn)
            }
            val accept = if (huText == null) isHu == conversationHuToEn else huText == isHu
            if (!accept) return@execute
            main.post {
                if (!active) return@post
                if (isHu != conversationHuToEn) {
                    conversationHuToEn = isHu
                    reinitTranslatorForConversation()
                    showMessage(if (isHu) "Auto: Magyar → Angol" else "Auto: Angol → Magyar", 1800)
                    try {
                        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                            .notify(NOTIFICATION_ID, buildNotification())
                    } catch (_: Exception) {
                    }
                }
                onFinal(text)
            }
        } } catch (_: Exception) {}
    }

    private fun asrPartial(isHu: Boolean, text: String) {
        if (autoDual && isHu != conversationHuToEn) return
        onPartial(text)
    }

    private fun onPartial(raw: String) {
        main.post {
            if (!active || paused) return@post
            val now = SystemClock.uptimeMillis()
            sentenceBuf?.onPartial(raw, now)
            sentenceBuf?.tick(now)
            // gyors vizuális partial (javítva)
            val srcRules = if (conversationHuToEn) huRules else enRules
            var text = Fixes.apply(raw, srcRules)
            text = if (conversationHuToEn) TextPolish.polishHungarian(text, vocabExtra)
            else TextPolish.polishEnglish(text, vocabExtra)
            if (text.isNotBlank() && text != curEn) {
                curEn = text
                store?.savePartial(text)
                render()
            }
        }
    }

    private fun onFinal(raw: String) {
        main.post {
            if (!active || paused) return@post
            if (TextPolish.isNoise(raw)) return@post
            val now = SystemClock.uptimeMillis()
            sentenceBuf?.onFinal(raw, now)
        }
    }

    private fun pushFinal(en: String, hu: String) {
        val delay = prefs.delayMs
        if (delay <= 0 || isTest) {
            commitFinalNow(en, hu)
        } else {
            val showAt = SystemClock.elapsedRealtime() + delay
            delayQueue.addLast(Triple(showAt, en, hu))
            main.removeCallbacks(delayRunnable)
            main.postDelayed(delayRunnable, delay.toLong())
        }
    }

    private fun commitFinalNow(en: String, hu: String) {
        history.addLast(hu)
        while (history.size > 3) history.removeFirst()
        curHu = ""
        lastEn = en
        if (isTest) {
            testEn.add(en)
            testHu.add(hu)
        } else {
            store?.append(en, hu)
        }
        render()
        if (prefs.ttsEnabled && hu.isNotBlank() && !isTest) {
            if (tts == null) {
                tts = TtsHelper(this).also { it.setLanguage(if (prefs.polyMode) prefs.polyTarget else prefs.targetLang) }
            }
            tts?.speak(hu)
        }
    }

    private fun reinitTranslatorForConversation() {
        try { translator?.close() } catch (_: Exception) {}
        translator = null
        translatorReady = false
        val (src, tgt) = if (conversationHuToEn) {
            com.google.mlkit.nl.translate.TranslateLanguage.HUNGARIAN to
                com.google.mlkit.nl.translate.TranslateLanguage.ENGLISH
        } else {
            com.google.mlkit.nl.translate.TranslateLanguage.ENGLISH to
                com.google.mlkit.nl.translate.TranslateLanguage.HUNGARIAN
        }
        val t = com.google.mlkit.nl.translate.Translation.getClient(
            com.google.mlkit.nl.translate.TranslatorOptions.Builder()
                .setSourceLanguage(src)
                .setTargetLanguage(tgt)
                .build()
        )
        translator = t
        t.downloadModelIfNeeded(com.google.mlkit.common.model.DownloadConditions.Builder().build())
            .addOnSuccessListener { translatorReady = true }
            .addOnFailureListener {
                setWarning("translator", "Fordítómodell hiba a váltáskor.")
            }
    }

    private fun render() {
        val lines = (history + curHu).filter { it.isNotBlank() }
        overlay?.setContent(curEn.ifBlank { lastEn }, lines)
        main.removeCallbacks(clearRunnable)
        main.postDelayed(clearRunnable, CLEAR_AFTER_MS)
        AppState.lastCaptionHu = lines.lastOrNull() ?: ""
        AppState.lastCaptionEn = curEn.ifBlank { lastEn }
        AppState.notifyListeners()
        WearBridge.pushStatus(this, AppState.lastCaptionHu, AppState.lastCaptionEn)
    }

    private fun showMessage(msg: String, ms: Long = 4000) {
        overlay?.setContent("", listOf(msg))
        main.removeCallbacks(clearRunnable)
        main.postDelayed(clearRunnable, ms)
    }

    private fun setWarning(key: String, msg: String?) {
        if (msg == null) {
            warnings.remove(key)
        } else if (warnings[key] != msg) {
            warnings[key] = msg
            AppState.addLog("WARN/$key: $msg")
        }
        val text = warnings.values.joinToString("\n")
        AppState.hint = text.ifBlank { null }
        overlay?.setWarning(text.ifBlank { null })
        WearBridge.pushStatus(this, AppState.lastCaptionHu, AppState.lastCaptionEn)
    }

    // ------------------------------------------------------- melegedés, akkumulátor

    private fun registerThermal() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            val l = PowerManager.OnThermalStatusChangedListener { status -> main.post { onThermal(status) } }
            thermalListener = l
            pm.addThermalStatusListener(l)
            onThermal(pm.currentThermalStatus)
        } catch (_: Exception) {
        }
    }

    private fun onThermal(status: Int) {
        val severe = status >= PowerManager.THERMAL_STATUS_SEVERE
        val moderate = status >= PowerManager.THERMAL_STATUS_MODERATE
        setWarning(
            "thermal",
            when {
                severe -> "🌡 A telefon túlmelegedett. Kapcsold be a takarékos módot, vagy tarts szünetet."
                moderate && prefs.adaptiveEco -> "🌡 Melegedés – automatikus takarékos mód."
                else -> null
            }
        )
        if (prefs.adaptiveEco) {
            val shouldForce = moderate
            if (shouldForce != prefs.forcedEco) {
                prefs.forcedEco = shouldForce
                partialIntervalMs = if (prefs.eco || prefs.forcedEco) 2500L else 900L
                if (shouldForce) {
                    toast("Takarékos mód: melegedés miatt")
                }
            }
        }
    }

    private fun checkBattery() {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val lvl = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        val pct = if (scale > 0 && lvl >= 0) lvl * 100 / scale else 100
        val low = pct <= 15 && !charging
        setWarning(
            "battery",
            if (low) "🔋 Alacsony akkumulátor ($pct%). Takarékos mód ajánlott." else null
        )
        if (prefs.adaptiveEco && low && !prefs.forcedEco) {
            prefs.forcedEco = true
            partialIntervalMs = 2500L
            toast("Takarékos mód: alacsony akkumulátor")
        }
        if (prefs.adaptiveEco && !low && pct > 25 && charging && prefs.forcedEco) {
            // thermal felülírhatja; csak battery miatt volt-e, nem tudjuk biztosan – ne kapcsoljuk vissza automatikusan
        }
    }

    // ------------------------------------------------------------ felismerő szál

    private fun startWorker(mode: String, modelPath: String?) {
        worker = Thread({
            var modelOther: Model? = null
            var recOther: Recognizer? = null
            var modelHu: Model? = null
            var recHu: Recognizer? = null
            var sys: LiveSystemAsr? = null
            var liveWhisper: LiveWhisperAsr? = null
            var poly: PolyEngine? = null
            var audio: AudioRecord? = null
            try {
                LibVosk.setLogLevel(LogLevel.WARNINGS)
                val sides0 = Engines.sides(prefs)
                // többnyelvű módban a PolyEngine (Whisper auto) dolgozik, a Vosk/rendszer ASR ágak ki vannak kapcsolva
                val polyOn = prefs.polyMode
                val sides = if (polyOn) Engines.Sides(hu = false, other = false, otherLang = sides0.otherLang) else sides0
                if (polyOn) {
                    val pe = PolyEngine(this@TranslateService, prefs, main, object : PolyEngine.Listener {
                        override fun onFinalLine(original: String, translated: String) {
                            if (active) commitFinalNow(original, translated)
                        }
                        override fun onFatal(message: String) {
                            main.post { if (active) fail(AppState.ERR_NO_MODEL, message) }
                        }
                        override fun onNote(message: String?) {
                            main.post { if (active) setWarning("poly", message) }
                        }
                    })
                    poly = pe
                    pe.start()
                }
                if (sides.other) {
                    if (modelPath == null || !File(modelPath).isDirectory) {
                        throw AppFailure(
                            AppState.ERR_NO_MODEL,
                            "Hiányzik a felismerő modell. Töltsd le a Modellek képernyőn."
                        )
                    }
                    val mdl = Model(modelPath)
                    modelOther = mdl
                    recOther = Recognizer(mdl, 16000f)
                }
                if (sides.hu) {
                    when (Engines.liveHuKind(this@TranslateService, prefs)) {
                        Engines.K_VOSK -> {
                            val hs = Engines.voskSpecFor(this@TranslateService, prefs, "hu")!!
                            val mdl = Model(ModelManager.dir(this@TranslateService, hs).absolutePath)
                            modelHu = mdl
                            recHu = Recognizer(mdl, 16000f)
                        }
                        Engines.K_WHISPER -> {
                            // Whisper élő mód – elkerüli a rendszer ASR „kliens hiba” hibáját
                            val lw = LiveWhisperAsr(
                                this@TranslateService, "hu",
                                object : LiveWhisperAsr.Callback {
                                    override fun onPartial(text: String) = asrPartial(true, text)
                                    override fun onFinal(text: String) = asrFinal(true, text)
                                    override fun onFatal(message: String) {
                                        main.post { if (active) fail(AppState.ERR_NO_MODEL, message) }
                                    }
                                }
                            )
                            liveWhisper = lw
                            lw.start()
                        }
                        else -> {
                            val s2 = LiveSystemAsr(
                                this@TranslateService, "hu-HU", prefs.allowOnlineAsr,
                                object : LiveSystemAsr.Callback {
                                    override fun onPartial(text: String) = asrPartial(true, text)
                                    override fun onFinal(text: String) = asrFinal(true, text)
                                    override fun onFatal(message: String) {
                                        main.post { if (active) fail(AppState.ERR_NO_MODEL, message) }
                                    }
                                }
                            )
                            sys = s2
                            s2.start()
                        }
                    }
                }
                val aud = createAudioRecord(mode)
                audio = aud
                aud.startRecording()
                store?.markStart()

                main.post {
                    if (!isTest) showMessage("Hallgatom…", 3000)
                    if (isTest) main.postDelayed({ finishTest() }, TEST_DURATION_MS)
                }

                val internal = mode == MODE_INTERNAL
                val buf = ShortArray(if (internal) 4800 else 1600)
                val out = ShortArray(1700)
                var sum = 0
                var cnt = 0
                var lastPartialO = ""
                var lastPartialH = ""
                var smooth = 0f
                var ema = 0.5f
                var overSince = 0L
                var lagOn = false
                var silentSince = 0L
                var silentWarned = false
                var wasPaused = false

                while (active) {
                    val n = aud.read(buf, 0, buf.size)
                    if (n < 0) throw AppFailure(AppState.ERR_AUDIO, "Hangolvasási hiba ($n)")
                    if (n == 0) continue

                    val data: ShortArray
                    val len: Int
                    if (internal) {
                        // 48 kHz -> 16 kHz: 3 minta átlagolása (az állapotot megtartjuk a hívások között)
                        var o = 0
                        for (k in 0 until n) {
                            sum += buf[k]
                            cnt++
                            if (cnt == 3) {
                                out[o++] = (sum / 3).toShort()
                                sum = 0
                                cnt = 0
                            }
                        }
                        data = out
                        len = o
                    } else {
                        data = buf
                        len = n
                    }
                    if (len == 0) continue
                    audioRec?.write(data, len)

                    // --- hangszint ---
                    var sq = 0.0
                    for (k in 0 until len) {
                        val s = data[k].toDouble()
                        sq += s * s
                    }
                    val rms = sqrt(sq / len)
                    val raw = (sqrt(rms / 32768.0) * 2.5).coerceAtMost(1.0).toFloat()
                    smooth = maxOf(raw, smooth * 0.85f)
                    AppState.updateLevel(smooth)
                    if (smooth > maxLevel) maxLevel = smooth
                    val lvl = smooth
                    main.post { overlay?.setLevel(lvl) }

                    val now = SystemClock.elapsedRealtime()

                    // --- nincs hang figyelés ---
                    if (rms < 20) {
                        if (silentSince == 0L) silentSince = now
                        if (!silentWarned && now - silentSince > SILENCE_WARN_MS) {
                            silentWarned = true
                            val msg = if (internal)
                                "🔇 Nem érkezik hang. Lehet, hogy az app tiltja a rögzítést – próbáld a mikrofon módot."
                            else
                                "🔇 Nem érzékelek hangot – ellenőrizd a mikrofont."
                            main.post { setWarning("silence", msg) }
                        }
                    } else {
                        silentSince = 0L
                        if (silentWarned) {
                            silentWarned = false
                            main.post { setWarning("silence", null) }
                        }
                    }

                    // --- VAD: nagyon halk chunk ne menjen a recognizerbe ---
                    val voiceLikely = rms >= 35

                    // --- szünet / push-to-talk ---
                    val listening = !paused && pttActive
                    if (!listening) {
                        wasPaused = true
                        continue
                    }
                    // a rendszer-felismerőnek a csendet is átadjuk (mondathatár-érzékelés)
                    poly?.feed(data, len)
                    val huNow = conversationHuToEn
                    val feedHu = sides.hu && (huNow || autoDual)
                    val feedOther = sides.other && (!huNow || autoDual)
                    if (feedHu) {
                        sys?.feed(data, len)
                        liveWhisper?.feed(data, len)
                    }
                    if (!voiceLikely) {
                        // hangszint frissült, de nincs beszéd – ne rontsa a partialt
                        // (a Whisper VAD saját maga is figyeli a csendet)
                        continue
                    }
                    if (wasPaused) {
                        recOther?.reset()
                        recHu?.reset()
                        lastPartialO = ""
                        lastPartialH = ""
                        wasPaused = false
                    }

                    // --- felismerés ---
                    val t0 = System.nanoTime()
                    val ro = recOther
                    if (feedOther && ro != null) {
                        if (ro.acceptWaveForm(data, len)) {
                            val parsed = RecognitionHelper.parseResult(ro.result)
                            lastPartialO = ""
                            if (parsed.text.isNotBlank() &&
                                RecognitionHelper.passes(parsed.confidence, prefs.confidenceMin)
                            ) {
                                asrFinal(false, parsed.text)
                            }
                        } else {
                            val p = RecognitionHelper.parsePartial(ro.partialResult)
                            if (p.isNotBlank() && p != lastPartialO) {
                                lastPartialO = p
                                asrPartial(false, p)
                            }
                        }
                    }
                    val rh = recHu
                    if (feedHu && rh != null) {
                        if (rh.acceptWaveForm(data, len)) {
                            val parsed = RecognitionHelper.parseResult(rh.result)
                            lastPartialH = ""
                            if (parsed.text.isNotBlank() &&
                                RecognitionHelper.passes(parsed.confidence, prefs.confidenceMin)
                            ) {
                                asrFinal(true, parsed.text)
                            }
                        } else {
                            val p = RecognitionHelper.parsePartial(rh.partialResult)
                            if (p.isNotBlank() && p != lastPartialH) {
                                lastPartialH = p
                                asrPartial(true, p)
                            }
                        }
                    }

                    // --- lemaradás figyelés ---
                    val procMs = (System.nanoTime() - t0) / 1_000_000f
                    val chunkMs = len * 1000f / 16000f
                    ema = ema * 0.9f + 0.1f * (procMs / chunkMs)
                    if (ema > 0.9f) {
                        if (overSince == 0L) overSince = now
                        if (!lagOn && now - overSince > 5000) {
                            lagOn = true
                            main.post {
                                setWarning(
                                    "lag",
                                    "⏱ A felismerés lemarad. Válts kisebb modellre vagy takarékos módra."
                                )
                            }
                        }
                    } else if (ema < 0.7f) {
                        overSince = 0L
                        if (lagOn) {
                            lagOn = false
                            main.post { setWarning("lag", null) }
                        }
                    }
                }
            } catch (t: Throwable) {
                if (active) {
                    val code = if (t is AppFailure) t.code else AppState.ERR_OTHER
                    val msg = t.message ?: t.javaClass.simpleName
                    main.post { fail(code, msg) }
                }
            } finally {
                try {
                    audio?.stop()
                } catch (_: Exception) {
                }
                audio?.release()
                sys?.stop()
                liveWhisper?.stop()
                poly?.stop()
                recOther?.close()
                modelOther?.close()
                recHu?.close()
                modelHu?.close()
            }
        }, "recognizer")
        worker?.start()
    }

    @SuppressLint("MissingPermission")
    private fun createAudioRecord(mode: String): AudioRecord {
        val record: AudioRecord
        if (mode == MODE_INTERNAL) {
            val proj = projection
                ?: throw AppFailure(AppState.ERR_PROJECTION, "Nincs hangrögzítési engedély.")
            val cfg = AudioPlaybackCaptureConfiguration.Builder(proj)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()
            val rate = 48000
            val fmt = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(rate)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()
            val min = AudioRecord.getMinBufferSize(
                rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            record = AudioRecord.Builder()
                .setAudioFormat(fmt)
                .setBufferSizeInBytes(maxOf(min, rate * 2))
                .setAudioPlaybackCaptureConfig(cfg)
                .build()
        } else {
            val rate = 16000
            val min = AudioRecord.getMinBufferSize(
                rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(min, rate * 2)
            )
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw AppFailure(AppState.ERR_AUDIO, "A hangrögzítés nem indult el.")
        }
        return record
    }

    // ----------------------------------------------------------------------- teszt

    private fun finishTest() {
        if (!active) return
        val sb = StringBuilder()
        if (testEn.isEmpty()) {
            sb.append("Nem ismertem fel beszédet a 10 másodperc alatt.\n")
        } else {
            for (i in testEn.indices) {
                sb.append("EN: ").append(testEn[i]).append('\n')
                sb.append("HU: ").append(testHu.getOrNull(i) ?: "…").append("\n\n")
            }
        }
        val pct = (maxLevel * 100).toInt()
        sb.append("Legnagyobb hangszint: $pct%.")
        if (maxLevel < 0.05f) sb.append(" Nagyon halk vagy nincs hang – ellenőrizd a mikrofont.")
        AppState.testResult = sb.toString().trim()
        shutdown()
    }

    // ------------------------------------------------------------------- leállítás

    private fun fail(code: String, msg: String) {
        AppState.lastError = AppError(code, msg)
        // A cleanup törli a függő főszálas üzeneteket, ezért a toastot közvetlenül jelenítjük meg.
        Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show()
        shutdown()
    }

    private fun cleanup() {
        if (cleaned) return
        cleaned = true
        active = false
        // Folyamatban lévő / késleltetett sorok mentése, hogy ne vesszenek el leálláskor
        val elapsedSec = if (AppState.sessionStartedAt > 0)
            (SystemClock.elapsedRealtime() - AppState.sessionStartedAt) / 1000 else 0L
        store?.let { st ->
            for ((_, en, hu) in delayQueue) st.append(en, hu)
            if (curEn.isNotBlank() && curEn != lastEn) st.append(curEn, curHu.ifBlank { "…" })
        }
        main.removeCallbacksAndMessages(null)
        delayQueue.clear()
        sentenceBuf?.reset()
        sentenceBuf = null
        try { asrExec.shutdownNow() } catch (_: Exception) {}
        overlay?.remove()
        overlay = null
        translator?.close()
        translator = null
        val hadStore = store != null
        store?.close()
        store = null
        audioRec?.stop()
        audioRec = null
        if (hadStore && !isTest && elapsedSec >= 20) notifySaved((elapsedSec / 60).toInt().coerceAtLeast(1))
        tts?.shutdown()
        tts = null
        LanguageIdHelper.close()
        WearBridge.pushStatus(this)
        WearBridge.stop(this)
        try {
            thermalListener?.let {
                (getSystemService(POWER_SERVICE) as PowerManager).removeThermalStatusListener(it)
            }
        } catch (_: Exception) {
        }
        thermalListener = null
        try {
            projection?.stop()
        } catch (_: Exception) {
        }
        projection = null
        AppState.running = false
        AppState.paused = false
        AppState.notifyListeners()
        AppState.mode = null
        AppState.updateLevel(0f)
        AppState.hint = null
        AppState.sessionStartedAt = 0L
        AppState.notifyTile(applicationContext)
    }

    private fun shutdown() {
        cleanup()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ------------------------------------------------------------------ értesítés


    private fun mlKitLang(code: String): String = when (code) {
        Prefs.LANG_HU -> TranslateLanguage.HUNGARIAN
        Prefs.LANG_DE -> TranslateLanguage.GERMAN
        Prefs.LANG_FR -> TranslateLanguage.FRENCH
        Prefs.LANG_ES -> TranslateLanguage.SPANISH
        Prefs.LANG_IT -> TranslateLanguage.ITALIAN
        Prefs.LANG_UK -> "uk" // ML Kit BCP-47
        else -> TranslateLanguage.ENGLISH
    }

    @Suppress("DEPRECATION")
    private fun acquireWakeLock() {
        try {
            releaseWakeLock()
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            // PARTIAL: CPU él; keepScreenOn esetén képernyő is
            val flags = if (prefs.keepScreenOn) {
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP
            } else {
                PowerManager.PARTIAL_WAKE_LOCK
            }
            val wl = pm.newWakeLock(flags, "angolmagyar:translate")
            wl.setReferenceCounted(false)
            wl.acquire(6 * 60 * 60 * 1000L) // max 6 óra biztonsági limit
            wakeLock = wl
        } catch (_: Exception) {
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    private fun createChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Élő fordítás", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(): Notification {
        val stopPi = PendingIntent.getService(
            this, 0,
            Intent(this, TranslateService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val pausePi = PendingIntent.getService(
            this, 2,
            Intent(this, TranslateService::class.java).setAction(ACTION_TOGGLE_PAUSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val switchPi = PendingIntent.getService(
            this, 3,
            Intent(this, TranslateService::class.java).setAction(ACTION_SWITCH_DIR),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openPi = PendingIntent.getActivity(
            this, 0,
            Intent(this, LiveTranslateActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val dirText = when {
            !prefs.needsTranslation() -> "Csak átirat"
            prefs.isConversation() ->
                if (conversationHuToEn) "Beszélgetés: HU → EN" else "Beszélgetés: EN → HU"
            prefs.isHuToEn() -> "HU → EN"
            else -> "${prefs.sourceLang.uppercase()} → ${prefs.targetLang.uppercase()}"
        }
        val modeText = when (AppState.mode) {
            MODE_INTERNAL -> "Belső hang"
            MODE_PTT -> "PTT"
            else -> "Mikrofon"
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_translate)
            .setContentTitle(if (paused) "Szünetel · $modeText" else "Fut · $modeText")
            .setContentText(dirText)
            .setSubText(if (prefs.ttsEnabled) "TTS be" else null)
            .setContentIntent(openPi)
            .addAction(0, if (paused) "Folytatás" else "Szünet", pausePi)
            .addAction(0, "Leállítás", stopPi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (prefs.isConversation()) {
            builder.addAction(0, "⇄ Irány", switchPi)
        }
        return builder.build()
    }

    private fun toast(msg: String) {
        main.post { Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show() }
    }
}
