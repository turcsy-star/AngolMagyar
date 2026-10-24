package hu.angolmagyar.fordito

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast
import java.io.File
import java.util.concurrent.Executors
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Beállítások: modell, felirat megjelenése, átirat, javítólisták, rendszerbeállítások. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var preview: TextView
    private lateinit var enFixes: AppCompatEditText
    private lateinit var huFixes: AppCompatEditText
    private lateinit var vocabEdit: AppCompatEditText
    private lateinit var speakerNamesEdit: AppCompatEditText

    private val zipLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            Toast.makeText(this, "Magyar modell telepítése…", Toast.LENGTH_SHORT).show()
            Executors.newSingleThreadExecutor().execute {
                try {
                    val tmp = File(cacheDir, "hu_upload.zip")
                    contentResolver.openInputStream(uri)?.use { input ->
                        tmp.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw IllegalStateException("Nem olvasható")
                    ModelManager.installFromZip(this, ModelManager.HU_LARGE, tmp) { _, _ -> }
                    tmp.delete()
                    runOnUiThread {
                        Toast.makeText(this, "Magyar modell telepítve.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this, "ZIP hiba: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

    private val onnxLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            Toast.makeText(this, "ONNX modell másolása…", Toast.LENGTH_SHORT).show()
            Executors.newSingleThreadExecutor().execute {
                try {
                    val dest = DiarizerFactory.modelFile(this)
                    dest.parentFile?.mkdirs()
                    contentResolver.openInputStream(uri)?.use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw IllegalStateException("Nem olvasható")
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            "ONNX kész (${dest.length() / 1024} KB)",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this, "ONNX hiba: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

    private fun dp(v: Int) = Ui.dp(this, v)

    private fun downloadHu(spec: ModelManager.Spec) {
        Toast.makeText(this, "Letöltés: ${spec.label}…", Toast.LENGTH_SHORT).show()
        Executors.newSingleThreadExecutor().execute {
            try {
                ModelManager.download(this, spec) { pct, msg ->
                    // progress opcionális
                }
                runOnUiThread {
                    Toast.makeText(this, "Kész: ${spec.label}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Letöltési hiba: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val column = Ui.column(this).apply {
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }
        val scroll = Ui.scrollOf(this, column)
        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }

        column.addView(Ui.button(this, "← Vissza", false) { finish() })
        column.addView(Ui.title(this, "Beállítások"))
        val tabGroups = listOf(ArrayList<View>(), ArrayList<View>(), ArrayList<View>())
        val tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }
        column.addView(tabBar)

        // ---------------------------------------------------------------- modell
        val modelCard = Ui.card(this)
        modelCard.addView(Ui.title(this, "Felismerő modellek", 17f))
        modelCard.addView(Ui.small(this, "Letöltés, választás és törlés egy helyen; készülék szerinti javaslattal."))
        modelCard.addView(Ui.button(this, "Modellek kezelése…", true) {
            startActivity(Intent(this, ModelsActivity::class.java))
        })
        modelCard.addView(switchRow("Takarékos mód (kis modell, ritkább részfordítás)", prefs.eco) { prefs.eco = it })
        modelCard.addView(Ui.small(this, "Kevesebb melegedés és akkumulátor-fogyasztás, kicsit lassabban frissülő felirat."))
        modelCard.addView(switchRow("Automatikus takarékos mód (melegedés / alacsony akku)", prefs.adaptiveEco) { prefs.adaptiveEco = it })
        modelCard.addView(Ui.small(this, "Thermal vagy alacsony akkumulátor esetén automatikusan kis modell és ritkább frissítés."))
        tabGroups[1].add(modelCard)

        // ---------------------------------------------------------------- magyar / tárgyalás modellek
        val huCard = Ui.card(this)
        huCard.addView(Ui.title(this, "Magyar felismerés és tárgyalás", 17f))
        huCard.addView(Ui.small(this, MeetingMode.statusText(this)))
        huCard.addView(Ui.button(this, "Magyar nagy/egyedi ZIP telepítése…", false) {
            zipLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*"))
        })
        huCard.addView(switchRow("Biztonságos hosszú felvétel (chunk + hűtés, 7–8 óra)", prefs.meetingSafeMode) {
            prefs.meetingSafeMode = it
        })
        huCard.addView(Ui.small(this, "Chunk: ${prefs.meetingChunkMinutes} perc. Melegedésnél automatikus szünet – kíméli az akkumulátort."))
        val meetingBtn = Ui.button(
            this,
            getString(if (prefs.meetingMode) R.string.s_meeting_off else R.string.s_meeting_on), false
        ) {}
        meetingBtn.setOnClickListener {
            if (prefs.meetingMode) {
                MeetingMode.clear(prefs)
                meetingBtn.text = getString(R.string.s_meeting_on)
                Toast.makeText(this, R.string.s_meeting_cleared, Toast.LENGTH_SHORT).show()
            } else {
                MeetingMode.apply(prefs)
                meetingBtn.text = getString(R.string.s_meeting_off)
                Toast.makeText(this, R.string.s_meeting_applied, Toast.LENGTH_SHORT).show()
            }
        }
        huCard.addView(meetingBtn)
        huCard.addView(Ui.body(this, "Beszélőnevek (fájl átirat / diarizáció)"))
        huCard.addView(Ui.small(this, "Soronként: S1=Anna vagy 1=Anna – az SRT-ben és az összefoglalóban megjelenik."))
        speakerNamesEdit = fixEdit(prefs.speakerNames, "pl.\nS1=Anna\nS2=Péter\nS3=Moderátor")
        huCard.addView(speakerNamesEdit)
        huCard.addView(Ui.small(this, DiarizerFactory.onnxStatus(this)))
        huCard.addView(Ui.button(this, "ONNX speaker_encoder.onnx telepítése…", false) {
            onnxLauncher.launch(arrayOf("application/octet-stream", "*/*"))
        })
        huCard.addView(Ui.small(this, "ONNX embedding diarization (ha nincs modell → energia S1/S2). ML Kit Language ID beépítve. Wear: :wear modul."))
        huCard.addView(Ui.small(this, "A nagy magyar modellhez Vosk-kompatibilis ZIP kell (am mappa a csomagban)."))
        tabGroups[1].add(huCard)


        // ---------------------------------------------------------------- megjelenés (téma)
        val themeCard = Ui.card(this)
        themeCard.addView(Ui.title(this, getString(R.string.s_theme_title), 17f))
        val themeGroup = RadioGroup(this)
        listOf(
            0 to R.string.s_theme_system,
            1 to R.string.s_theme_light,
            2 to R.string.s_theme_dark
        ).forEach { (id, label) ->
            themeGroup.addView(RadioButton(this).apply {
                this.id = View.generateViewId()
                text = getString(label)
                isChecked = prefs.themeMode == id
                setOnClickListener {
                    if (prefs.themeMode != id) {
                        prefs.themeMode = id
                        App.applyTheme(id)
                    }
                }
            })
        }
        themeCard.addView(themeGroup)
        tabGroups[0].add(themeCard)

        // ---------------------------------------------------------------- fordítás iránya
        val dirCard = Ui.card(this)
        dirCard.addView(Ui.title(this, "Fordítás iránya", 17f))
        val dirRadios = RadioGroup(this)
        val dirs = listOf(
            Prefs.DIR_EN_HU to "Angol → Magyar",
            Prefs.DIR_HU_EN to "Magyar → Angol",
            Prefs.DIR_CONVERSATION to "Kétirányú beszélgetés (⇄ a feliraton)",
            Prefs.DIR_NONE to "Csak átirat (nincs fordítás)"
        )
        dirs.forEach { (id, label) ->
            dirRadios.addView(RadioButton(this).apply {
                this.id = View.generateViewId()
                text = label
                isChecked = prefs.translateDir == id
                setOnClickListener { prefs.translateDir = id }
            })
        }
        dirCard.addView(dirRadios)
        dirCard.addView(Ui.small(this, "Magyar → angol irányhoz töltsd le a magyar Vosk modellt is a főképernyőn."))
        dirCard.addView(switchRow("Beszélgetés: automatikus irányváltás", prefs.autoLangSwitch) {
            prefs.autoLangSwitch = it
        })
        dirCard.addView(switchRow("ML Kit Language ID (valódi nyelvfelismerés)", prefs.useMlLangId) {
            prefs.useMlLangId = it
        })
        dirCard.addView(Ui.small(this, "ML Kit on-device; ki kapcsolva csak heurisztika. Manuális ⇄ továbbra is működik."))
        tabGroups[1].add(dirCard)

        val langCard = Ui.card(this)
        langCard.addView(Ui.title(this, "Élő fordítás nyelvei", 17f))
        langCard.addView(Ui.small(this, "Forrás: a kiválasztott nyelv Vosk-modellje ismeri fel a beszédet (angol, magyar, német, francia, spanyol, olasz, ukrán). Cél: ML Kit offline fordítás."))
        langCard.addView(Ui.small(this, "Forrásnyelv:"))
        val srcGroup = RadioGroup(this)
        listOf(
            Prefs.LANG_EN to "Angol",
            Prefs.LANG_HU to "Magyar",
            Prefs.LANG_DE to "Német",
            Prefs.LANG_FR to "Francia",
            Prefs.LANG_ES to "Spanyol",
            Prefs.LANG_IT to "Olasz",
            Prefs.LANG_UK to "Ukrán"
        ).forEach { (id, label) ->
            srcGroup.addView(RadioButton(this).apply {
                this.id = View.generateViewId()
                text = label
                isChecked = prefs.sourceLang == id
                setOnClickListener { prefs.sourceLang = id }
            })
        }
        langCard.addView(srcGroup)
        langCard.addView(Ui.small(this, "Célnyelv (fordítás):"))
        val tgtGroup = RadioGroup(this)
        listOf(
            Prefs.LANG_HU to "Magyar",
            Prefs.LANG_EN to "Angol",
            Prefs.LANG_DE to "Német",
            Prefs.LANG_FR to "Francia",
            Prefs.LANG_ES to "Spanyol",
            Prefs.LANG_IT to "Olasz",
            Prefs.LANG_UK to "Ukrán"
        ).forEach { (id, label) ->
            tgtGroup.addView(RadioButton(this).apply {
                this.id = View.generateViewId()
                text = label
                isChecked = prefs.targetLang == id
                setOnClickListener { prefs.targetLang = id }
            })
        }
        langCard.addView(tgtGroup)
        langCard.addView(Ui.small(this, "Natív felismeréshez töltsd le a forrásnyelv Vosk modelljét. Fordítás: ML Kit (első használatkor). WakeLock: lezárt képernyő OK."))
        tabGroups[1].add(langCard)

        // ---------------------------------------------------------------- telepített modellek törlése
        val installedCard = Ui.card(this)
        installedCard.addView(Ui.title(this, "Telepített modellek (törlés)", 17f))
        installedCard.addView(Ui.small(this, "A nem használt nyelvek törlésével helyet szabadíthatsz. Később újra letölthetők."))
        val installed = ModelManager.installedSpecs(this)
        if (installed.isEmpty()) {
            installedCard.addView(Ui.small(this, "Nincs telepített modell."))
        } else {
            for (spec in installed) {
                val mb = ModelManager.sizeMb(this, spec)
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                val label = Ui.small(this, "${spec.label}  ·  ${mb} MB")
                label.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                row.addView(label)
                row.addView(Ui.smallButton(this, "Törlés") {
                    android.app.AlertDialog.Builder(this@SettingsActivity)
                        .setTitle("Modell törlése")
                        .setMessage("Törlöd: ${spec.label} (${mb} MB)?\nKésőbb újra letölthető.")
                        .setPositiveButton("Törlés") { _, _ ->
                            if (ModelManager.delete(this@SettingsActivity, spec)) {
                                Toast.makeText(this@SettingsActivity, "Törölve: ${spec.label}", Toast.LENGTH_SHORT).show()
                                // frissítés: activity újratöltés
                                recreate()
                            } else {
                                Toast.makeText(this@SettingsActivity, "Törlés sikertelen", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton("Mégse", null)
                        .show()
                })
                installedCard.addView(row)
            }
            installedCard.addView(Ui.button(this, "Összes modell törlése", false) {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Összes modell")
                    .setMessage("Minden letöltött Vosk modell törlése? Több 100 MB is felszabadulhat.")
                    .setPositiveButton("Törlés") { _, _ ->
                        var n = 0
                        for (s in ModelManager.installedSpecs(this)) {
                            if (ModelManager.delete(this, s)) n++
                        }
                        Toast.makeText(this, "$n modell törölve.", Toast.LENGTH_SHORT).show()
                        recreate()
                    }
                    .setNegativeButton("Mégse", null)
                    .show()
            })
        }
        tabGroups[1].add(installedCard)



        // ---------------------------------------------------------------- késleltetés, confidence, PTT
        val advCard = Ui.card(this)
        advCard.addView(Ui.title(this, "Felismerés és felirat", 17f))

        val delayLabel = Ui.body(this, "Felirat késleltetés: ${prefs.delayMs} ms")
        advCard.addView(delayLabel)
        advCard.addView(SeekBar(this).apply {
            max = 30  // 0..3000 ms, 100-as lépés
            progress = prefs.delayMs / 100
            setOnSeekBarChangeListener(simpleSeek { v ->
                prefs.delayMs = v * 100
                delayLabel.text = "Felirat késleltetés: ${prefs.delayMs} ms"
            })
        })
        advCard.addView(Ui.small(this, "Filmnézésnél hasznos, ha a felirat kicsit késik vagy siet."))

        val confLabel = Ui.body(this, "Confidence küszöb: ${if (prefs.confidenceMin == 0) "ki" else "${prefs.confidenceMin}%"}")
        advCard.addView(confLabel)
        advCard.addView(SeekBar(this).apply {
            max = 80
            progress = prefs.confidenceMin
            setOnSeekBarChangeListener(simpleSeek { v ->
                prefs.confidenceMin = v
                confLabel.text = "Confidence küszöb: ${if (v == 0) "ki" else "$v%"}"
            })
        })
        advCard.addView(Ui.small(this, "Alacsonyabb confidence-ű szavak elrejtése (kevesebb hibás felirat)."))

        advCard.addView(switchRow("Push-to-talk (mikrofon: csak gombnyomásra hallgat)", prefs.pushToTalk) {
            prefs.pushToTalk = it
        })
        advCard.addView(Ui.small(this, "A feliraton a 🎙 gombot tartsd nyomva beszélés közben."))
        tabGroups[2].add(advCard)

        // ---------------------------------------------------------------- felirat
        val styleCard = Ui.card(this)
        // ---------------------------------------------------------------- stílus presetek
        val presetCard = Ui.card(this)
        presetCard.addView(Ui.title(this, "Felirat stílus", 17f))
        val presets = listOf(
            Prefs.STYLE_DEFAULT to "Alap",
            Prefs.STYLE_NETFLIX to "Netflix",
            Prefs.STYLE_YOUTUBE to "YouTube",
            Prefs.STYLE_CONCERT to "Koncert",
            Prefs.STYLE_HC to "Magas kontraszt"
        )
        val presetGroup = RadioGroup(this)
        presets.forEach { (id, label) ->
            presetGroup.addView(RadioButton(this).apply {
                this.id = View.generateViewId()
                text = label
                isChecked = prefs.stylePreset == id
                setOnClickListener {
                    prefs.applyStylePreset(id)
                    applyPreview()
                }
            })
        }
        presetCard.addView(presetGroup)
        tabGroups[0].add(presetCard)

        styleCard.addView(Ui.title(this, "Felirat megjelenése", 17f))

        preview = TextView(this).apply {
            text = "Helló, hogyan vagy ma?"
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(18), dp(16), dp(18))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8); bottomMargin = dp(8) }
        }
        styleCard.addView(preview)

        val sizeLabel = Ui.body(this, "Betűméret: ${prefs.textSizeSp} sp")
        styleCard.addView(sizeLabel)
        styleCard.addView(SeekBar(this).apply {
            max = 28
            progress = prefs.textSizeSp - 12
            setOnSeekBarChangeListener(simpleSeek { p ->
                prefs.textSizeSp = p + 12
                sizeLabel.text = "Betűméret: ${prefs.textSizeSp} sp"
                applyPreview()
            })
        })

        val alphaLabel = Ui.body(this, "Háttér átlátszatlansága: ${prefs.bgAlphaPct}%")
        styleCard.addView(alphaLabel)
        styleCard.addView(SeekBar(this).apply {
            max = 80
            progress = prefs.bgAlphaPct - 20
            setOnSeekBarChangeListener(simpleSeek { p ->
                prefs.bgAlphaPct = p + 20
                alphaLabel.text = "Háttér átlátszatlansága: ${prefs.bgAlphaPct}%"
                applyPreview()
            })
        })

        styleCard.addView(Ui.body(this, "Szövegszín"))
        val colors = RadioGroup(this)
        Prefs.COLOR_NAMES.forEachIndexed { i, name ->
            colors.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = name
                isChecked = i == prefs.textColorIdx
                setOnClickListener {
                    prefs.textColorIdx = i
                    applyPreview()
                }
            })
        }
        styleCard.addView(colors)

        styleCard.addView(switchRow("Angol eredeti sor mutatása", prefs.showEnglish) {
            prefs.showEnglish = it
            applyPreview()
        })
        styleCard.addView(switchRow(getString(R.string.s_touch_through), prefs.touchThrough) {
            prefs.touchThrough = it
        })
        styleCard.addView(Ui.small(this, getString(R.string.s_touch_through_hint)))
        styleCard.addView(switchRow("Magas kontraszt mód (nagyobb, fehér, tömör háttér)", prefs.highContrast) {
            prefs.highContrast = it
            applyPreview()
        })
        styleCard.addView(switchRow("Felirat a képernyő tetején", prefs.atTop) {
            prefs.atTop = it
            prefs.overlayY = -1
        })
        styleCard.addView(Ui.small(this, "Vízszintes igazítás:"))
        val alignGroup = RadioGroup(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(Prefs.ALIGN_LEFT to "Bal", Prefs.ALIGN_CENTER to "Közép", Prefs.ALIGN_RIGHT to "Jobb").forEach { (id, label) ->
            alignGroup.addView(RadioButton(this).apply {
                this.id = View.generateViewId()
                text = label
                isChecked = prefs.hAlign == id
                setOnClickListener { prefs.hAlign = id }
            })
        }
        styleCard.addView(alignGroup)

        val maxLinesLabel = Ui.body(this, "Max. megjelenő sorok: ${prefs.maxOverlayLines}")
        styleCard.addView(maxLinesLabel)
        styleCard.addView(SeekBar(this).apply {
            max = 4 // 2..6
            progress = prefs.maxOverlayLines - 2
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    prefs.maxOverlayLines = p + 2
                    maxLinesLabel.text = "Max. megjelenő sorok: ${prefs.maxOverlayLines}"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })
        styleCard.addView(Ui.small(this, "Dupla koppintás = szünet / folytatás. Egy koppintás = vezérlősáv. Húzás = pozíció. Két ujjal csippentve = betűméret. Hosszú nyomás = mondat mentése a kedvencek közé."))
        tabGroups[0].add(styleCard)

        // ---------------------------------------------------------------- átirat
        val trCard = Ui.card(this)
        trCard.addView(Ui.title(this, "Átirat", 17f))
        trCard.addView(switchRow("Átirat automatikus mentése", prefs.saveTranscript) { prefs.saveTranscript = it })
        trCard.addView(switchRow(getString(R.string.s_resume_last), prefs.resumeLast) { prefs.resumeLast = it })
        trCard.addView(Ui.small(this, getString(R.string.s_resume_last_hint)))
        trCard.addView(switchRow(getString(R.string.s_save_audio), prefs.saveAudio) { prefs.saveAudio = it })
        trCard.addView(Ui.small(this, getString(R.string.s_save_audio_hint)))
        trCard.addView(switchRow("Fordított szöveg felolvasása (TTS)", prefs.ttsEnabled) { prefs.ttsEnabled = it })
        trCard.addView(Ui.small(this, "A kész fordítást a rendszer TextToSpeech hangjával felolvassa (offline)."))
        tabGroups[0].add(trCard)

        // ---------------------------------------------------------------- javítások + személyes szótár
        val fixCard = Ui.card(this)
        fixCard.addView(Ui.title(this, "Javítólisták és szótár", 17f))
        fixCard.addView(Ui.small(this, "Soronként egy szabály: régi=új. Kis/nagybetű nem számít, egész szóra illeszt."))
        val suggestions = VocabLearner.pending(prefs)
        if (suggestions.isNotEmpty()) {
            fixCard.addView(Ui.body(this, getString(R.string.s_learn_title)))
            fixCard.addView(Ui.small(this, getString(R.string.s_learn_hint)))
            for (sg in suggestions.take(6)) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                row.addView(
                    Ui.body(this, "${sg.from} → ${sg.to}  (${sg.count}×)"),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                )
                row.addView(Ui.smallButton(this, getString(R.string.td_learn_add)) {
                    // az EditText-ekbe is beírjuk, hogy a kilépéskori mentés ne írja felül
                    val line = sg.from.lowercase() + "=" + sg.to
                    val box = if (sg.target) huFixes else enFixes
                    val cur = box.text?.toString() ?: ""
                    box.setText(if (cur.isBlank()) line else cur.trimEnd() + "\n" + line)
                    VocabLearner.dismiss(prefs, sg)
                    row.visibility = View.GONE
                })
                row.addView(Ui.smallButton(this, getString(R.string.td_learn_skip)) {
                    VocabLearner.dismiss(prefs, sg)
                    row.visibility = View.GONE
                })
                fixCard.addView(row)
            }
        }
        fixCard.addView(Ui.body(this, "Angol javítások (felismerés után, fordítás előtt)"))
        enFixes = fixEdit(prefs.enFixes, "pl.\ncold play=Coldplay")
        fixCard.addView(enFixes)
        fixCard.addView(Ui.body(this, "Magyar javítások (fordítás után)"))
        huFixes = fixEdit(prefs.huFixes, "pl.\nhelló világ=Hello World")
        fixCard.addView(huFixes)
        fixCard.addView(Ui.body(this, "Személyes szótár (nevek, cégnevek, kifejezések)"))
        fixCard.addView(Ui.small(this, "Soronként: szó vagy forrás=helyes forma. Pl.\nOpenAI=OpenAI\nchatgpt=ChatGPT"))
        vocabEdit = fixEdit(prefs.customVocab, "pl.\nOpenAI\nchatgpt=ChatGPT\nbudapest=Budapest")
        fixCard.addView(vocabEdit)
        tabGroups[2].add(fixCard)

        // ---------------------------------------------------------------- rendszer
        val sysCard = Ui.card(this)
        sysCard.addView(Ui.title(this, "Rendszer", 17f))
        sysCard.addView(switchRow("Rezgés (haptika)", prefs.haptics) { prefs.haptics = it })
        sysCard.addView(switchRow("Képernyő ébren tartása futás közben", prefs.keepScreenOn) {
            prefs.keepScreenOn = it
        })
        sysCard.addView(switchRow("ONNX speaker embedding (ha telepítve)", prefs.preferOnnx) {
            prefs.preferOnnx = it
        })
        sysCard.addView(Ui.small(this, DiarizerFactory.onnxStatus(this)))

        sysCard.addView(Ui.button(this, "Megjelenítés más appok fölött", false) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        })
        sysCard.addView(Ui.button(this, "Akkumulátor-optimalizálás beállításai", false) {
            BatterySettings.open(this)
        })
        sysCard.addView(Ui.button(this, getString(R.string.log_title), false) { LogDialog.show(this) })
        tabGroups[2].add(sysCard)

        // --- fülek: Megjelenés / Felismerés / Haladó
        val tabNames = listOf("Megjelenés", "Felismerés", "Haladó")
        val tabButtons = ArrayList<android.widget.Button>()
        fun showTab(i: Int) {
            for (g in tabGroups.indices) for (v in tabGroups[g]) {
                v.visibility = if (g == i) View.VISIBLE else View.GONE
            }
            tabButtons.forEachIndexed { k, b ->
                b.alpha = if (k == i) 1f else 0.55f
                b.setTypeface(null, if (k == i) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            }
            try { prefs.settingsTab = i } catch (_: Exception) {}
        }
        tabNames.forEachIndexed { i, n ->
            val b = Ui.smallButton(this, n) { showTab(i) }
            b.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (i > 0) marginStart = dp(6)
            }
            tabButtons.add(b)
            tabBar.addView(b)
        }
        // a kártyák a fülsor után kerülnek az oszlopba, fülenként egymás után
        for (g in tabGroups) for (v in g) column.addView(v)
        showTab(prefs.settingsTab.coerceIn(0, 2))

        applyPreview()
    }

    override fun onPause() {
        super.onPause()
        prefs.enFixes = enFixes.text?.toString() ?: ""
        prefs.huFixes = huFixes.text?.toString() ?: ""
        prefs.customVocab = vocabEdit.text?.toString() ?: ""
        prefs.speakerNames = speakerNamesEdit.text?.toString() ?: ""
    }

    private fun applyPreview() {
        val hc = prefs.highContrast
        preview.text = if (prefs.showEnglish) "Hello, how are you today?\nHelló, hogyan vagy ma?"
        else "Helló, hogyan vagy ma?"
        preview.textSize = (prefs.textSizeSp + if (hc) 2 else 0).toFloat()
        preview.setTextColor(if (hc) Color.WHITE else prefs.textColor)
        preview.setTypeface(null, android.graphics.Typeface.BOLD)
        val alpha = if (hc) 245 else prefs.bgAlphaPct * 255 / 100
        // ugyanaz a kékes-fekete háttér, mint az élő feliraton
        preview.background = GradientDrawable().apply {
            setColor(Color.argb(alpha, 8, 10, 18))
            cornerRadius = dp(18).toFloat()
        }
    }

    private fun switchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) =
        SwitchCompat(this).apply {
            text = label
            isChecked = checked
            minHeight = dp(52)
            setOnCheckedChangeListener { _, c -> onChange(c) }
        }

    private fun fixEdit(initial: String, hintText: String) = AppCompatEditText(this).apply {
        setText(initial)
        hint = hintText
        minLines = 3
        gravity = Gravity.TOP or Gravity.START
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
    }

    private fun simpleSeek(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser) onChange(progress)
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }
}
