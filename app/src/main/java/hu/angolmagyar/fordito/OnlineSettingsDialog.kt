package hu.angolmagyar.fordito

import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/** Az online (LLM-es) fordítás beállítása: szolgáltató, kulcs, modell, tudomásulvétel, kapcsolat-teszt. */
object OnlineSettingsDialog {

    fun show(act: AppCompatActivity, onDone: () -> Unit) {
        val ctx = act
        val pad = Ui.dp(ctx, 16)
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, 0) }

        fun label(t: String) = Ui.body(ctx, t)
        fun field(hint: String, value: String, password: Boolean = false) = EditText(ctx).apply {
            setText(value)
            this.hint = hint
            textSize = 14f
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or
                (if (password) InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_TEXT_VARIATION_URI)
            setTextColor(Ui.color(ctx, R.color.textPrimary))
        }

        col.addView(Ui.small(ctx,
            "A felismert angol szöveg ezen a szolgáltatáson keresztül megy a fordításhoz. Az Anthropic vagy " +
                "OpenAI-kompatibilis végpont (akár a saját hálózatodon futó helyi szerver) is megadható."))

        col.addView(label("Szolgáltató"))
        val provSpin = Spinner(ctx)
        val provNames = listOf("Anthropic (Claude)", "OpenAI-kompatibilis")
        provSpin.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, provNames)
        provSpin.setSelection(if (OnlinePrefs.provider(ctx) == OnlineTranslator.Provider.OPENAI) 1 else 0)
        col.addView(provSpin)

        col.addView(label("API-kulcs"))
        val key = field("sk-…", OnlinePrefs.apiKey(ctx), true)
        col.addView(key)

        col.addView(label("Modell"))
        val model = field("üresen: ${OnlineTranslator.DEFAULT_ANTHROPIC_MODEL}", OnlinePrefs.model(ctx))
        col.addView(model)

        col.addView(label("Végpont (csak OpenAI-kompatibilisnél; üresen: ${OnlineTranslator.DEFAULT_OPENAI_BASE})"))
        val base = field("http://192.168.1.10:11434/v1", OnlinePrefs.baseUrl(ctx))
        col.addView(base)

        col.addView(label("Háttér a fordítónak (téma, nevek – nem kötelező)"))
        val hint = field("pl. családi dráma, a szereplők: Nina, Jake", OnlinePrefs.hint(ctx))
        col.addView(hint)

        val consent = androidx.appcompat.widget.SwitchCompat(ctx).apply {
            text = "Tudomásul veszem: a felismert szöveg elhagyja a telefont (nem a hang, csak a szöveg)"
            minHeight = Ui.dp(ctx, 52)
            isChecked = OnlinePrefs.consent(ctx)
        }
        col.addView(consent)
        val result = TextView(ctx).apply { textSize = 13f; gravity = Gravity.START }
        col.addView(result)

        fun saveAll() {
            OnlinePrefs.setProvider(ctx, if (provSpin.selectedItemPosition == 1) OnlineTranslator.Provider.OPENAI
            else OnlineTranslator.Provider.ANTHROPIC)
            OnlinePrefs.setApiKey(ctx, key.text.toString())
            OnlinePrefs.setModel(ctx, model.text.toString())
            OnlinePrefs.setBaseUrl(ctx, base.text.toString())
            OnlinePrefs.setHint(ctx, hint.text.toString())
            OnlinePrefs.setConsent(ctx, consent.isChecked)
        }

        col.addView(Ui.smallButton(ctx, "Kapcsolat kipróbálása") {
            saveAll()
            val cfg = OnlinePrefs.config(ctx, "angol", "magyar")
            if (cfg == null) {
                val miss = ArrayList<String>()
                if (OnlinePrefs.apiKey(ctx).isBlank() && !(OnlinePrefs.provider(ctx) == OnlineTranslator.Provider.OPENAI && OnlinePrefs.baseUrl(ctx).isNotBlank()))
                    miss.add("API-kulcs (a Claude-előfizetés nem API-kulcs: az console.anthropic.com oldalon kell létrehozni)")
                if (OnlinePrefs.provider(ctx) == OnlineTranslator.Provider.OPENAI && OnlinePrefs.model(ctx).isBlank()) miss.add("modellnév")
                if (!OnlinePrefs.consent(ctx)) miss.add("tudomásulvétel (kapcsoló)")
                result.text = "Hiányzik: " + miss.joinToString(", ") + "."
            } else {
                result.text = "Próba…"
                Thread {
                    val err = try { OnlineTranslator(cfg).test() } catch (e: Throwable) { e.message ?: "hiba" }
                    act.runOnUiThread { result.text = if (err == null) "✓ Működik." else "✗ $err" }
                }.start()
            }
        })

        val sv = ScrollView(ctx).apply { addView(col) }
        AlertDialog.Builder(ctx)
            .setTitle("Online fordítás")
            .setView(sv)
            .setPositiveButton("Mentés") { _, _ ->
                saveAll()
                if (!OnlinePrefs.usable(ctx)) Toast.makeText(ctx, "Az online fordítás még nincs teljesen beállítva.", Toast.LENGTH_LONG).show()
                onDone()
            }
            .setNegativeButton("Mégse", null)
            .show()
    }
}
