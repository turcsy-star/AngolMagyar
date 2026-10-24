package hu.angolmagyar.fordito

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Feladat-varázsló: a főképernyő csempéi ide visznek. Az adatok felvételenként változnak,
 * ezért itt kérjük be őket (nem a Beállításokban); minden mező előre kitöltött, így
 * a nagy gombbal azonnal indítható ("gyors indítás").
 */
class TaskSetupActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TASK = "task"
        const val TASK_LIVE = "live"
        const val TASK_INTERPRETER = "interpreter"
        const val TASK_TRANSCRIBE = "transcribe"
        const val TASK_SUBTITLE = "subtitle"
    }

    private lateinit var prefs: Prefs
    private lateinit var column: LinearLayout
    private fun dp(v: Int) = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        column = Ui.column(this).apply { setPadding(dp(16), dp(16), dp(16), dp(32)) }
        val scroll = Ui.scrollOf(this, column)
        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        column.addView(Ui.button(this, "← Vissza", false) { finish() })
        when (intent.getStringExtra(EXTRA_TASK)) {
            TASK_INTERPRETER -> buildInterpreter()
            TASK_TRANSCRIBE -> buildTranscribe()
            TASK_SUBTITLE -> buildSubtitle()
            else -> buildLive()
        }
    }

    // ----------------------------------------------------------------- segédek

    private fun radios(options: List<String>, checked: Int): RadioGroup {
        val g = RadioGroup(this)
        options.forEachIndexed { i, label ->
            g.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = label
                isChecked = i == checked
                minHeight = dp(48)
            })
        }
        return g
    }

    private fun RadioGroup.index(): Int {
        val c = checkedRadioButtonId
        return (0 until childCount).indexOfFirst { getChildAt(it).id == c }.coerceAtLeast(0)
    }

    private fun switchRow(label: String, checked: Boolean) = SwitchCompat(this).apply {
        text = label
        isChecked = checked
        minHeight = dp(52)
    }

    private fun sourceModeOptions() = listOf(
        "Belső hang (film, videó, YouTube)",
        "Mikrofon (beszélgetés, előadás)",
        "Nyomva beszélés (gomb)"
    )

    private fun modeFor(idx: Int) = when (idx) {
        0 -> TranslateService.MODE_INTERNAL
        2 -> TranslateService.MODE_PTT
        else -> TranslateService.MODE_MIC
    }

    private fun startLive(mode: String) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_AUTO_START, mode)
        )
        finish()
    }

    // ------------------------------------------------------------ élő fordítás

    private data class Pair2(val label: String, val src: String, val tgt: String, val dir: String)

    private fun buildLive() {
        column.addView(Ui.title(this, "Élő fordítás"))
        column.addView(Ui.small(this, "A hang élő felirata és fordítása a képernyőn."))

        val pairs = listOf(
            Pair2("Angol → Magyar", "en", "hu", Prefs.DIR_EN_HU),
            Pair2("Magyar → Angol", "hu", "en", Prefs.DIR_HU_EN),
            Pair2("Német → Magyar", "de", "hu", Prefs.DIR_EN_HU),
            Pair2("Francia → Magyar", "fr", "hu", Prefs.DIR_EN_HU),
            Pair2("Spanyol → Magyar", "es", "hu", Prefs.DIR_EN_HU),
            Pair2("Olasz → Magyar", "it", "hu", Prefs.DIR_EN_HU),
            Pair2("Ukrán → Magyar", "uk", "hu", Prefs.DIR_EN_HU)
        )
        val cur = when {
            prefs.translateDir == Prefs.DIR_HU_EN -> 1
            else -> pairs.indexOfFirst { it.src == prefs.sourceLang && it.dir == Prefs.DIR_EN_HU }
                .let { if (it < 0) 0 else it }
        }
        val c1 = Ui.card(this)
        c1.addView(Ui.title(this, "Milyen nyelvről fordítsak?", 16f))
        val g1 = radios(pairs.map { it.label }, cur)
        c1.addView(g1)
        column.addView(c1)

        val c2 = Ui.card(this)
        c2.addView(Ui.title(this, "Honnan jön a hang?", 16f))
        val lastIdx = when (prefs.lastMode) {
            TranslateService.MODE_MIC -> 1
            TranslateService.MODE_PTT -> 2
            else -> 0
        }
        val g2 = radios(sourceModeOptions(), lastIdx)
        c2.addView(g2)
        column.addView(c2)

        val save = switchRow("Átirat mentése", prefs.saveTranscript)
        val c3 = Ui.card(this)
        c3.addView(save)
        column.addView(c3)

        column.addView(Ui.button(this, "Indítás", true) {
            val p = pairs[g1.index()]
            prefs.translateDir = p.dir
            if (p.dir == Prefs.DIR_EN_HU) prefs.sourceLang = p.src
            prefs.targetLang = p.tgt
            prefs.saveTranscript = save.isChecked
            startLive(modeFor(g2.index()))
        })
    }

    // ------------------------------------------------------------------ tolmács

    private fun buildInterpreter() {
        column.addView(Ui.title(this, "Tolmács"))
        column.addView(Ui.small(this, "Kétirányú beszélgetés: angol ⇄ magyar, felolvasással."))

        val c1 = Ui.card(this)
        val auto = switchRow("Automatikus irányváltás", prefs.autoLangSwitch)
        c1.addView(auto)
        c1.addView(Ui.small(this,
            "Be: az app mindkét nyelvet figyeli, és a hallott nyelv dönt az irányról. " +
                "Ki: rögzített irány, a feliraton lévő ⇄ gombbal váltasz."))
        column.addView(c1)

        val c2 = Ui.card(this)
        c2.addView(Ui.title(this, "Kezdő irány", 16f))
        val g1 = radios(listOf("Angol → Magyar", "Magyar → Angol"), if (prefs.convStartHu) 1 else 0)
        c2.addView(g1)
        column.addView(c2)

        val c3 = Ui.card(this)
        c3.addView(Ui.title(this, "Honnan jön a hang?", 16f))
        val g2 = radios(sourceModeOptions(), if (prefs.lastMode == TranslateService.MODE_PTT) 2 else 1)
        c3.addView(g2)
        val tts = switchRow("Fordítás felolvasása", true)
        c3.addView(tts)
        column.addView(c3)

        column.addView(Ui.button(this, "Indítás", true) {
            prefs.translateDir = Prefs.DIR_CONVERSATION
            prefs.sourceLang = Prefs.LANG_EN
            prefs.targetLang = Prefs.LANG_HU
            prefs.autoLangSwitch = auto.isChecked
            prefs.convStartHu = g1.index() == 1
            prefs.ttsEnabled = tts.isChecked
            startLive(modeFor(g2.index()))
        })
    }

    // ------------------------------------------------------------------- átirat

    private data class LangOpt(val code: String, val label: String)

    private val subtitleLangs = listOf(
        LangOpt("en", "Angol"),
        LangOpt("de", "Német"),
        LangOpt("fr", "Francia"),
        LangOpt("es", "Spanyol"),
        LangOpt("it", "Olasz"),
        LangOpt("uk", "Ukrán"),
        LangOpt("hu", "Magyar")
    )

    private fun buildTranscribe() {
        column.addView(Ui.title(this, "Átirat"))
        column.addView(Ui.small(this,
            "1. Honnan jön a hang.\n" +
                "2. Milyen nyelvű a beszéd.\n" +
                "3. Kell-e fordítás / beszélők.\n" +
                "4. Fájl vagy élő felvétel → részletes folyamat (chunk, ETA)."
        ))

        val c0 = Ui.card(this)
        c0.addView(Ui.title(this, "Honnan?", 16f))
        val gSrc = radios(listOf("Fájlból (hang vagy videó)", "Élő felvétel (mikrofon)"), 0)
        c0.addView(gSrc)
        column.addView(c0)

        // Nyelvek – ugyanaz a lista, mint a film-feliratnál
        val cLang = Ui.card(this)
        cLang.addView(Ui.title(this, "A beszéd nyelve", 16f))
        // egyetlen nyelv választható → rádiógomb (átiratnál magyar az alap)
        val gLang = radios(subtitleLangs.map { it.label }, subtitleLangs.indexOfFirst { it.code == "hu" })
        cLang.addView(gLang)
        column.addView(cLang)

        fun selectedLang(): LangOpt = subtitleLangs[gLang.index().coerceIn(0, subtitleLangs.lastIndex)]

        val cOut = Ui.card(this)
        cOut.addView(Ui.title(this, "Kimenet", 16f))
        val tr = switchRow("Fordítás is (célnyelv)", false)
        cOut.addView(tr)
        val gTgt = radios(
            listOf("Magyar", "Angol", "Német", "Francia", "Spanyol"), 1 // angol cél ha magyar forrás
        )
        cOut.addView(Ui.small(this, "Célnyelv (ha fordítasz):"))
        cOut.addView(gTgt)
        val tgtCodes = listOf("hu", "en", "de", "fr", "es")
        column.addView(cOut)

        val c2 = Ui.card(this)
        c2.addView(Ui.title(this, "Beszélők (opcionális)", 16f))
        val diar = switchRow("Beszélők megkülönböztetése (S1, S2…)", false)
        c2.addView(diar)
        c2.addView(Ui.small(this, "Nevek soronként, pl. S1=Anna. Üresen hagyható."))
        val names = AppCompatEditText(this).apply {
            setText(prefs.speakerNames)
            hint = "S1=Anna\nS2=Péter"
            minLines = 2
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        c2.addView(names)
        column.addView(c2)

        // Modell státusz
        val modelCard = Ui.card(this)
        modelCard.addView(Ui.title(this, "Szükséges modell", 16f))
        val modelStatus = Ui.body(this, "Ellenőrzés…")
        modelCard.addView(modelStatus)
        val modelBtn = Ui.button(this, "Hiányzó modell letöltése…", true) {
            startActivity(Intent(this, ModelsActivity::class.java))
        }
        modelBtn.visibility = android.view.View.GONE
        modelCard.addView(modelBtn)
        column.addView(modelCard)

        fun refreshModelStatus() {
            val lang = selectedLang()
            val lines = ArrayList<String>()
            var missing = false
            if (lang.code == "hu") {
                val ok = try {
                    SystemAsrSupport.onDeviceAvailable(this) ||
                        (Prefs(this).allowOnlineAsr && SystemAsrSupport.anyAvailable(this)) ||
                        ModelManager.isInstalled(this, ModelManager.HU_LARGE) ||
                        Engines.whisperSpec(this) != null
                } catch (_: Exception) { false }
                if (ok) lines.add(if (Engines.whisperSpec(this) != null) "✓ Magyar felismerő: Whisper (offline)" else "✓ Magyar felismerő elérhető")
                else {
                    lines.add("✗ Magyar offline felismerő nem elérhető")
                    lines.add("  Tipp: a Modellek képernyőn töltsd le a Whisper modellt – offline és pontos magyarul is.")
                    missing = true
                }
            } else {
                val spec = ModelManager.forSourceLang(lang.code, lang.code == "en", this)
                val has = Engines.whisperSpec(this) != null || ModelManager.isInstalled(this, spec) ||
                    (lang.code == "en" && (
                        ModelManager.isInstalled(this, ModelManager.LARGE) ||
                            ModelManager.isInstalled(this, ModelManager.SMALL)))
                if (has) lines.add("✓ ${lang.label} felismerő telepítve")
                else {
                    lines.add("✗ Nincs ${lang.label} felismerő")
                    lines.add("  Ajánlott: ${spec.label}")
                    missing = true
                }
            }
            if (tr.isChecked) lines.add("Fordítás: ML Kit → ${tgtCodes[gTgt.index().coerceIn(0, tgtCodes.lastIndex)]}")
            modelStatus.text = lines.joinToString("\n")
            modelBtn.visibility = if (missing) android.view.View.VISIBLE else android.view.View.GONE
        }

        gLang.setOnCheckedChangeListener { _, _ -> refreshModelStatus() }
        tr.setOnCheckedChangeListener { _, _ -> refreshModelStatus() }
        gTgt.setOnCheckedChangeListener { _, _ -> refreshModelStatus() }
        refreshModelStatus()

        // Folyamat-előnézet (Whisper-szerű lépéslista)
        val planCard = Ui.card(this)
        planCard.addView(Ui.title(this, "Fájl esetén a folyamat", 16f))
        planCard.addView(Ui.body(this,
            "1. Hang kinyerése a fájlból\n" +
                "2. PCM betöltés (hossz megjelenik)\n" +
                "3. Felismerés chunkokban (X/Y, %)\n" +
                "4. Opcionális fordítás\n" +
                "5. Átirat / SRT mentés\n\n" +
                "Közben: teljes % · lépés % · eltelt / hátralévő · kész kb. óó:pp\n" +
                "Háttérben is fut (értesítés + wake lock)."
        ))
        column.addView(planCard)

        column.addView(Ui.button(this, "Indítás", true) {
            val lang = selectedLang()
            prefs.speakerNames = names.text?.toString() ?: ""
            val tgt = tgtCodes[gTgt.index().coerceIn(0, tgtCodes.lastIndex)]
            if (gSrc.index() == 0) {
                // Fájl → FileSubtitle ugyanazzal a részletes UI-val
                fun go() {
                    openFileSubtitle(
                        src = lang.code,
                        translate = tr.isChecked,
                        output = if (tr.isChecked) 2 else 0,
                        tgt = tgt,
                        title = "Átirat fájlból",
                        diarize = diar.isChecked
                    )
                }
                if (lang.code != "hu") {
                    val spec = ModelManager.forSourceLang(lang.code, true, this)
                    val has = Engines.whisperSpec(this) != null || ModelManager.isInstalled(this, spec) ||
                        (lang.code == "en" && (
                            ModelManager.isInstalled(this, ModelManager.LARGE) ||
                                ModelManager.isInstalled(this, ModelManager.SMALL)))
                    if (!has) {
                        android.app.AlertDialog.Builder(this)
                            .setTitle("Hiányzik a ${lang.label} modell")
                            .setMessage("Töltsd le: ${spec.label}. Wi-Fin, töltőn ajánlott.")
                            .setPositiveButton("Letöltés") { _, _ ->
                                startActivity(Intent(this, ModelsActivity::class.java))
                            }
                            .setNegativeButton("Mégis tovább") { _, _ -> go() }
                            .show()
                    } else go()
                } else go()
            } else {
                prefs.saveTranscript = true
                if (tr.isChecked) {
                    prefs.translateDir = "${lang.code}_${tgt}"
                    prefs.sourceLang = lang.code
                    prefs.targetLang = tgt
                } else {
                    prefs.translateDir = Prefs.DIR_NONE
                    prefs.sourceLang = lang.code
                }
                startLive(TranslateService.MODE_MIC)
            }
        })
    }

    // ---------------------------------------------------------- film felirat

    private fun buildSubtitle() {
        column.addView(Ui.title(this, "Felirat videóhoz"))
        column.addView(Ui.small(this,
            "1. Válaszd ki a videó nyelvét.\n" +
                "2. Jelöld be, mi kerüljön a feliratba.\n" +
                "3. Ha hiányzik a modell, töltsd le.\n" +
                "4. Fájl kiválasztása → SRT."
        ))

        // A videó nyelve: pontosan egy → rádiógomb
        val cLang = Ui.card(this)
        cLang.addView(Ui.title(this, "A videó nyelve", 16f))
        val gLang = radios(subtitleLangs.map { it.label }, 0)
        cLang.addView(gLang)
        column.addView(cLang)

        // Kimenet: egymástól független igen/nem → jelölőnégyzet (legalább az egyik kell)
        val cOut = Ui.card(this)
        cOut.addView(Ui.title(this, "Mit írjon a felirat?", 16f))
        val cbOrig = androidx.appcompat.widget.AppCompatCheckBox(this).apply {
            text = "Eredeti szöveg (a videó nyelvén)"
            isChecked = false
            minHeight = dp(48)
        }
        val cbTr = androidx.appcompat.widget.AppCompatCheckBox(this).apply {
            text = "Fordítás (célnyelven) – ajánlott"
            isChecked = true
            minHeight = dp(48)
        }
        cOut.addView(cbTr)
        cOut.addView(cbOrig)
        cOut.addView(Ui.small(this, "Mindkettő bejelölve: két soros felirat (eredeti + fordítás)."))
        column.addView(cOut)

        val cTgt = Ui.card(this)
        cTgt.addView(Ui.title(this, "Célnyelv (fordítás)", 16f))
        val gTgt = radios(
            listOf("Magyar", "Angol", "Német", "Francia", "Spanyol"), 0
        )
        cTgt.addView(gTgt)
        column.addView(cTgt)
        val tgtCodes = listOf("hu", "en", "de", "fr", "es")

        val modelCard = Ui.card(this)
        modelCard.addView(Ui.title(this, "Szükséges modell", 16f))
        val modelStatus = Ui.body(this, "Ellenőrzés…")
        modelCard.addView(modelStatus)
        val modelBtn = Ui.button(this, "Modell letöltése…", true) {
            startActivity(Intent(this, ModelsActivity::class.java))
        }
        modelBtn.visibility = android.view.View.GONE
        modelCard.addView(modelBtn)
        column.addView(modelCard)

        fun selectedLang(): LangOpt = subtitleLangs[gLang.index().coerceIn(0, subtitleLangs.lastIndex)]
        fun targetCode(): String = tgtCodes[gTgt.index().coerceIn(0, tgtCodes.lastIndex)]

        fun refreshModelStatus() {
            val lang = selectedLang()
            val needTr = cbTr.isChecked
            cTgt.visibility = if (needTr) android.view.View.VISIBLE else android.view.View.GONE
            val lines = ArrayList<String>()
            var missing = false
            if (lang.code == "hu") {
                val ok = try {
                    SystemAsrSupport.onDeviceAvailable(this) ||
                        (Prefs(this).allowOnlineAsr && SystemAsrSupport.anyAvailable(this)) ||
                        ModelManager.isInstalled(this, ModelManager.HU_LARGE) ||
                        Engines.whisperSpec(this) != null
                } catch (_: Exception) { false }
                if (ok) lines.add(if (Engines.whisperSpec(this) != null) "✓ Magyar felismerő: Whisper (offline)" else "✓ Magyar felismerő elérhető")
                else {
                    lines.add("✗ Magyar offline felismerő nem elérhető")
                    lines.add("  Tipp: a Modellek képernyőn töltsd le a Whisper modellt – offline és pontos magyarul is.")
                    missing = true
                }
            } else {
                val spec = ModelManager.forSourceLang(lang.code, lang.code == "en", this)
                val has = Engines.whisperSpec(this) != null || ModelManager.isInstalled(this, spec) ||
                    (lang.code == "en" && (
                        ModelManager.isInstalled(this, ModelManager.LARGE) ||
                            ModelManager.isInstalled(this, ModelManager.SMALL)))
                if (has) {
                    lines.add("✓ ${lang.label} felismerő telepítve")
                } else {
                    lines.add("✗ Nincs ${lang.label} felismerő modell")
                    lines.add("  Ajánlott: ${spec.label}")
                    missing = true
                }
            }
            if (needTr) {
                if (targetCode() == lang.code) {
                    lines.add("⚠ A videó és a célnyelv azonos – válassz másik célnyelvet.")
                } else {
                    val tgtLabel = listOf("magyar", "angol", "német", "francia", "spanyol")[gTgt.index().coerceIn(0, 4)]
                    lines.add("Fordítás: ML Kit (${lang.label} → $tgtLabel)")
                }
            }
            modelStatus.text = lines.joinToString("\n")
            modelBtn.visibility = if (missing) android.view.View.VISIBLE else android.view.View.GONE
            modelBtn.text = "Hiányzó modell letöltése…"
        }

        // Ha a videó nyelve = célnyelv, automatikusan átállítjuk a célnyelvet (magyar ↔ angol)
        fun fixTargetIfSame() {
            if (targetCode() == selectedLang().code) {
                gTgt.check(gTgt.getChildAt(if (selectedLang().code == "hu") 1 else 0).id)
            }
        }

        // Legalább az egyik kimenet legyen bejelölve
        cbOrig.setOnCheckedChangeListener { _, checked ->
            if (!checked && !cbTr.isChecked) cbTr.isChecked = true
            refreshModelStatus()
        }
        cbTr.setOnCheckedChangeListener { _, checked ->
            if (!checked && !cbOrig.isChecked) cbOrig.isChecked = true
            refreshModelStatus()
        }
        // Listeners only AFTER local functions exist
        gLang.setOnCheckedChangeListener { _, _ ->
            fixTargetIfSame()
            refreshModelStatus()
        }
        gTgt.setOnCheckedChangeListener { _, _ -> refreshModelStatus() }
        fixTargetIfSame()
        refreshModelStatus()

        column.addView(Ui.button(this, "Tovább: fájl kiválasztása", true) {
            val lang = selectedLang()
            val (tr, out) = when {
                cbTr.isChecked && cbOrig.isChecked -> true to 2
                cbTr.isChecked -> true to 1
                else -> false to 0
            }
            val tgt = targetCode()
            fun go() = openFileSubtitle(lang.code, tr, out, tgt)
            if (lang.code != "hu") {
                val spec = ModelManager.forSourceLang(lang.code, true, this)
                val has = Engines.whisperSpec(this) != null || ModelManager.isInstalled(this, spec) ||
                    (lang.code == "en" && (
                        ModelManager.isInstalled(this, ModelManager.LARGE) ||
                            ModelManager.isInstalled(this, ModelManager.SMALL)))
                if (!has) {
                    android.app.AlertDialog.Builder(this)
                        .setTitle("Hiányzik a ${lang.label} modell")
                        .setMessage("A felismeréshez töltsd le: ${spec.label}. Wi-Fin, töltőn ajánlott.")
                        .setPositiveButton("Letöltés") { _, _ ->
                            startActivity(Intent(this, ModelsActivity::class.java))
                        }
                        .setNegativeButton("Mégis tovább") { _, _ -> go() }
                        .show()
                } else go()
            } else go()
        })
    }

    private fun openFileSubtitle(
        src: String,
        translate: Boolean,
        output: Int,
        tgt: String = "hu",
        title: String = "Felirat videóhoz",
        diarize: Boolean = false
    ) {
        startActivity(
            Intent(this, FileSubtitleActivity::class.java)
                .putExtra(FileSubtitleActivity.EXTRA_TITLE, title)
                .putExtra(FileSubtitleActivity.EXTRA_SRC, src)
                .putExtra(FileSubtitleActivity.EXTRA_TRANSLATE, translate)
                .putExtra(FileSubtitleActivity.EXTRA_OUTPUT, output)
                .putExtra(FileSubtitleActivity.EXTRA_TGT, tgt)
                .putExtra(FileSubtitleActivity.EXTRA_DIARIZE, diarize)
                .putExtra(FileSubtitleActivity.EXTRA_AUTOSTART, true)
        )
        finish()
    }
}
