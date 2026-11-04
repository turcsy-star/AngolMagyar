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
            "Ajánlott: DeepL – csak egy kulcs kell hozzá. Ingyenes kulcs (500 000 karakter/hó, kb. 25–40 film): " +
                "deepl.com/pro-api → „DeepL API Free” – a kulcs végén :fx áll. Másold be ide, kapcsold be a tudomásulvételt, kész."))
        col.addView(label("Szolgáltató"))
        val provSpin = Spinner(ctx)
        val provNames = listOf("Gemini (elsődleges)", "Mistral AI (tartalék)", "Anthropic (Claude)", "OpenAI-kompatibilis", "DeepL (kontextussal)")
        provSpin.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, provNames)
        fun providerAt(pos: Int) = when (pos) {
            1 -> OnlineTranslator.Provider.MISTRAL
            2 -> OnlineTranslator.Provider.ANTHROPIC
            3 -> OnlineTranslator.Provider.OPENAI
            4 -> OnlineTranslator.Provider.DEEPL
            else -> OnlineTranslator.Provider.GEMINI
        }
        fun positionOf(provider: OnlineTranslator.Provider) = when (provider) {
            OnlineTranslator.Provider.GEMINI -> 0
            OnlineTranslator.Provider.MISTRAL -> 1
            OnlineTranslator.Provider.ANTHROPIC -> 2
            OnlineTranslator.Provider.OPENAI -> 3
            OnlineTranslator.Provider.DEEPL -> 4
        }
        var loadedProvider = OnlinePrefs.provider(ctx)
        provSpin.setSelection(positionOf(loadedProvider))
        col.addView(provSpin)
        col.addView(label("API-kulcs"))
        val key = field("API-kulcs", OnlinePrefs.apiKeyFor(ctx, loadedProvider), true)
        col.addView(key)
        val advBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        advBox.addView(label("Modell"))
        val model = field("üresen: ${OnlineTranslator.DEFAULT_ANTHROPIC_MODEL}", OnlinePrefs.model(ctx))
        advBox.addView(model)
        advBox.addView(label("Végpont (OpenAI-kompatibilisnél; üresen: ${OnlineTranslator.DEFAULT_OPENAI_BASE})"))
        val base = field("http://192.168.1.10:11434/v1", OnlinePrefs.baseUrl(ctx))
        advBox.addView(base)
        col.addView(advBox)
        fun syncAdv() { advBox.visibility = if (provSpin.selectedItemPosition == 4) View.GONE else View.VISIBLE }
        syncAdv()
        provSpin.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val next = providerAt(pos)
                if (next != loadedProvider) {
                    OnlinePrefs.setApiKeyFor(ctx, loadedProvider, key.text.toString())
                    loadedProvider = next
                    OnlinePrefs.setProvider(ctx, loadedProvider)
                    key.setText(OnlinePrefs.apiKeyFor(ctx, loadedProvider))
                    model.setText(OnlinePrefs.model(ctx))
                }
                syncAdv()
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }
        col.addView(label("Háttér a fordítónak (téma, nevek – nem kötelező)"))
        val hint = field("pl. családi dráma, a szereplők: Nina, Jake", OnlinePrefs.hint(ctx))
        col.addView(hint)
        val consent = androidx.appcompat.widget.SwitchCompat(ctx).apply {
            text = "Tudomásul veszem: a felismert szöveg elhagyja a telefont (nem a hang, csak a szöveg)"
            minHeight = Ui.dp(ctx, 52)
            isChecked = OnlinePrefs.consent(ctx)
        }
        col.addView(consent)
        val dict = androidx.appcompat.widget.SwitchCompat(ctx).apply {
            text = "Online szótár a szöveghibák javításához (csak az ellenőrzendő különálló szavak mennek ki, mondat nem)"
            minHeight = Ui.dp(ctx, 52)
            isChecked = OnlinePrefs.dictEnabled(ctx)
        }
        col.addView(dict)
        val result = TextView(ctx).apply { textSize = 13f; gravity = Gravity.START }
        col.addView(result)
        fun saveAll(): Boolean {
            val selectedProvider = providerAt(provSpin.selectedItemPosition)
            OnlinePrefs.setProvider(ctx, selectedProvider)
            val keySaved = OnlinePrefs.setApiKeyFor(ctx, selectedProvider, key.text.toString())
            OnlinePrefs.setModel(ctx, model.text.toString())
            OnlinePrefs.setBaseUrl(ctx, base.text.toString())
            OnlinePrefs.setHint(ctx, hint.text.toString())
            OnlinePrefs.setConsent(ctx, consent.isChecked)
            OnlinePrefs.setDictEnabled(ctx, dict.isChecked)
            if (OnlinePrefs.usable(ctx) && OnlinePrefs.mode(ctx) == OnlinePrefs.OFF) OnlinePrefs.setMode(ctx, OnlinePrefs.AUTO)
            return keySaved
        }
        col.addView(Ui.smallButton(ctx, "Kapcsolat kipróbálása") {
            if (!saveAll()) {
                result.text = "A kulcs biztonságos mentése nem sikerült. Ellenőrizd az Android Keystore állapotát; a kulcs nem lett titkosítatlanul eltárolva."
                return@smallButton
            }
            if (!Engines.online(ctx)) {
                result.text = "Nincs internetkapcsolat a telefonon. Kapcsolódj Wi-Fi-hez vagy mobilnethez, majd próbáld újra."
                return@smallButton
            }
            val cfg = OnlinePrefs.config(ctx, "angol", "magyar")
            if (cfg == null) {
                val miss = OnlinePrefs.missing(ctx)
                val keyHint = if (OnlinePrefs.provider(ctx) == OnlineTranslator.Provider.ANTHROPIC && "API-kulcs" in miss)
                    " (A Claude-előfizetés nem API-kulcs: az console.anthropic.com oldalon kell létrehozni.)" else ""
                result.text = if (miss.isEmpty()) "Az online fordítás jelenleg nem használható."
                else "Hiányzik: ${miss.joinToString(", ")}.$keyHint"
            } else {
                result.text = "Próba…"
                Thread {
                    val err = try { OnlineTranslator(cfg).test() } catch (e: Throwable) { OnlineTranslator.netErrorText(e) }
                    val dictErr = if (dict.isChecked) OnlineDictionary.selfTest() else "kikapcsolva"
                    act.runOnUiThread {
                        result.text = (if (err == null) "✓ Online fordítás: működik." else "✗ Online fordítás: $err") +
                            "\n" + (if (dictErr == null) "✓ Online szótár: működik." else "Online szótár: $dictErr")
                    }
                }.start()
            }
        })
        val sv = ScrollView(ctx).apply { addView(col) }
        AlertDialog.Builder(ctx)
            .setTitle("Online fordítás")
            .setView(sv)
            .setPositiveButton("Mentés") { _, _ ->
                val saved = saveAll()
                if (!saved) {
                    Toast.makeText(ctx, "A kulcs biztonságos mentése nem sikerült; nem tároltuk titkosítatlanul.", Toast.LENGTH_LONG).show()
                } else if (!OnlinePrefs.usable(ctx)) {
                    Toast.makeText(ctx, "Az online fordítás még nincs teljesen beállítva.", Toast.LENGTH_LONG).show()
                }
                onDone()
            }
            .setNegativeButton("Mégse", null)
            .show()
    }
}
