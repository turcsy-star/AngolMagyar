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

    private fun buildTranscribe() {
        column.addView(Ui.title(this, "Átirat"))
        column.addView(Ui.small(this, "Beszédből szöveg: hang- vagy videófájlból, vagy élő felvételből."))

        val c0 = Ui.card(this)
        c0.addView(Ui.title(this, "Honnan?", 16f))
        val gSrc = radios(listOf("Fájlból (hang vagy videó)", "Élő felvétel (mikrofon)"), 0)
        c0.addView(gSrc)
        column.addView(c0)

        val c1 = Ui.card(this)
        c1.addView(Ui.title(this, "A beszéd nyelve", 16f))
        val gLang = radios(listOf("Magyar", "Angol"), 0)
        c1.addView(gLang)
        val tr = switchRow("Fordítás is a másik nyelvre", false)
        c1.addView(tr)
        column.addView(c1)

        val c2 = Ui.card(this)
        c2.addView(Ui.title(this, "Beszélők (ha tudod)", 16f))
        val diar = switchRow("Beszélők megkülönböztetése (S1, S2…)", false)
        c2.addView(diar)
        c2.addView(Ui.small(this, "Nevek soronként, pl. S1=Anna. Üresen hagyhatod."))
        val names = AppCompatEditText(this).apply {
            setText(prefs.speakerNames)
            hint = "S1=Anna\nS2=Péter"
            minLines = 2
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        c2.addView(names)
        column.addView(c2)

        column.addView(Ui.button(this, "Indítás", true) {
            val hu = gLang.index() == 0
            prefs.speakerNames = names.text?.toString() ?: ""
            if (gSrc.index() == 0) {
                startActivity(
                    Intent(this, FileSubtitleActivity::class.java)
                        .putExtra(FileSubtitleActivity.EXTRA_TITLE, "Átirat fájlból")
                        .putExtra(FileSubtitleActivity.EXTRA_SRC, if (hu) "hu" else "en")
                        .putExtra(FileSubtitleActivity.EXTRA_TRANSLATE, tr.isChecked)
                        .putExtra(FileSubtitleActivity.EXTRA_OUTPUT, if (tr.isChecked) 2 else 0)
                        .putExtra(FileSubtitleActivity.EXTRA_DIARIZE, diar.isChecked)
                        .putExtra(FileSubtitleActivity.EXTRA_AUTOSTART, true)
                )
                finish()
            } else {
                // élő felvétel: csak átirat (vagy fordítással), mentéssel
                prefs.saveTranscript = true
                if (tr.isChecked) {
                    prefs.translateDir = if (hu) Prefs.DIR_HU_EN else Prefs.DIR_EN_HU
                    if (!hu) prefs.sourceLang = Prefs.LANG_EN
                    prefs.targetLang = if (hu) Prefs.LANG_EN else Prefs.LANG_HU
                } else {
                    prefs.translateDir = Prefs.DIR_NONE
                    prefs.sourceLang = if (hu) Prefs.LANG_HU else Prefs.LANG_EN
                }
                startLive(TranslateService.MODE_MIC)
            }
        })
    }

    // ---------------------------------------------------------- film felirat

    private fun buildSubtitle() {
        column.addView(Ui.title(this, "Felirat videóhoz"))
        val how = Ui.card(this)
        how.addView(Ui.title(this, "Így megy", 16f))
        how.addView(Ui.body(this,
            "1. Válaszd ki, mi legyen a feliratban (lent).\n" +
                "2. Nyomd meg az indítást, és válaszd ki a videót vagy hangfájlt.\n" +
                "3. Az app felismeri a beszédet és lefordítja – ez hosszú filmnél sokáig tart, töltőn, Wi-Fin érdemes.\n" +
                "4. Mentsd el a .srt fájlt a videó mellé, a videóval azonos néven: a lejátszó (VLC, MX Player) magától betölti."))
        column.addView(how)

        val c1 = Ui.card(this)
        c1.addView(Ui.title(this, "Mi kerüljön a feliratba?", 16f))
        val g = radios(
            listOf(
                "Angol videó → magyar felirat",
                "Angol videó → angol + magyar (két sor)",
                "Magyar videó → angol felirat",
                "Magyar videó → magyar felirat (fordítás nélkül)"
            ), 0
        )
        c1.addView(g)
        column.addView(c1)

        column.addView(Ui.button(this, "Videó kiválasztása és indítás", true) {
            // src, translate, output (0 = eredeti, 1 = fordítás, 2 = mindkettő)
            val (src, tr, out) = when (g.index()) {
                0 -> Triple("en", true, 1)
                1 -> Triple("en", true, 2)
                2 -> Triple("hu", true, 1)
                else -> Triple("hu", false, 0)
            }
            startActivity(
                Intent(this, FileSubtitleActivity::class.java)
                    .putExtra(FileSubtitleActivity.EXTRA_TITLE, "Felirat videóhoz")
                    .putExtra(FileSubtitleActivity.EXTRA_SRC, src)
                    .putExtra(FileSubtitleActivity.EXTRA_TRANSLATE, tr)
                    .putExtra(FileSubtitleActivity.EXTRA_OUTPUT, out)
                    .putExtra(FileSubtitleActivity.EXTRA_DIARIZE, false)
                    .putExtra(FileSubtitleActivity.EXTRA_AUTOSTART, true)
            )
            finish()
        })
    }
}
