package hu.angolmagyar.fordito.ui

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import hu.angolmagyar.fordito.core.db.AppDatabase
import hu.angolmagyar.fordito.core.db.DictionaryEntity
import hu.angolmagyar.fordito.intelligence.DictionaryCsv
import hu.angolmagyar.fordito.intelligence.DictionaryEntry
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hu.angolmagyar.fordito.Prefs
import hu.angolmagyar.fordito.TranslationQualityAudit
import hu.angolmagyar.fordito.ui.design.PremiumCard
import hu.angolmagyar.fordito.ui.theme.AngolMagyarTheme
import hu.angolmagyar.fordito.intelligence.SynonymEngine
import hu.angolmagyar.fordito.intelligence.PronunciationAssessment

class NextWaveToolsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Prefs(this)
        setContent {
            AngolMagyarTheme(isTrueBlack = prefs.oledBlack) {
                NextWaveTools(onBack = { finish() })
            }
        }
    }
}

@Composable
private fun NextWaveTools(onBack: () -> Unit) {
    var source by remember { mutableStateOf("") }
    var translation by remember { mutableStateOf("") }
    var context by remember { mutableStateOf("") }
    var tone by remember { mutableStateOf("Természetes") }
    var report by remember { mutableStateOf<TranslationQualityAudit.Report?>(null) }
    var alternativeA by remember { mutableStateOf("") }
    var alternativeB by remember { mutableStateOf("") }
    var dictionaryQuery by remember { mutableStateOf("") }
    var synonymQuery by remember { mutableStateOf("") }
    var recognizedSpeech by remember { mutableStateOf("") }
    var accent by remember { mutableStateOf("US") }
    var ttsReady by remember { mutableStateOf(false) }
    var dictionaryCount by remember { mutableIntStateOf(0) }
    var customDictionaryMatches by remember { mutableStateOf(emptyList<Triple<String, String, String>>()) }
    var dictionaryStatus by remember { mutableStateOf("A saját szótár még nincs betöltve.") }
    var speechMessage by remember { mutableStateOf("A beszédfelismerés eredménye itt jelenik meg.") }
    val appContext = LocalContext.current
    val database = remember { AppDatabase.get(appContext) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { dictionaryCount = database.dictionaryDao().count() }
    LaunchedEffect(dictionaryQuery) {
        customDictionaryMatches = if (dictionaryQuery.isBlank()) emptyList() else
            database.dictionaryDao().search(dictionaryQuery.trim()).map { Triple(it.english, it.hungarian, it.example) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            runCatching {
                val csv = appContext.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: error("A kiválasztott fájl nem olvasható.")
                val rows = DictionaryCsv.parse(csv)
                val entities = rows.map { row ->
                    DictionaryEntity(englishKey = row.english.trim().lowercase(Locale.ROOT), english = row.english.trim(),
                        hungarian = row.hungarian.trim(), example = row.example.trim())
                }
                database.dictionaryDao().upsertAll(entities)
                dictionaryCount = database.dictionaryDao().count()
                "${entities.size} bejegyzés importálva. Összesen: $dictionaryCount."
            }.onSuccess { dictionaryStatus = it }.onFailure { dictionaryStatus = "Importhiba: ${it.message ?: "ismeretlen hiba"}" }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri != null) scope.launch {
            runCatching {
                val entries = database.dictionaryDao().all().map { DictionaryEntry(it.english, it.hungarian, it.example) }
                val csv = DictionaryCsv.export(entries)
                appContext.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write("\uFEFF"); it.write(csv) }
                    ?: error("A célfájl nem írható.")
                "${entries.size} bejegyzés exportálva."
            }.onSuccess { dictionaryStatus = it }.onFailure { dictionaryStatus = "Exporthiba: ${it.message ?: "ismeretlen hiba"}" }
        }
    }
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) recognizedSpeech = spoken
        speechMessage = if (spoken.isNullOrBlank()) "Nem érkezett felismerhető beszéd." else "A beszédfelismerés eredménye betöltve. Ellenőrizd a szöveget a pontozás előtt."
    }
    val tts = remember {
        TextToSpeech(appContext) { status -> ttsReady = status == TextToSpeech.SUCCESS }
    }
    DisposableEffect(tts) {
        onDispose { tts.stop(); tts.shutdown() }
    }
    val dictionaryEntries = remember {
        listOf(
            Triple("actually", "valójában; tulajdonképpen", "Actually, I agree. – Tulajdonképpen egyetértek."),
            Triple("address", "cím; megszólít; kezel", "What is your address? – Mi a címed?"),
            Triple("afford", "megengedhet magának; futja rá", "I can't afford it. – Nem engedhetem meg magamnak."),
            Triple("appreciate", "értékel; nagyra becsül; hálás", "I appreciate your help. – Hálás vagyok a segítségedért."),
            Triple("available", "elérhető; rendelkezésre álló; szabad", "Is this seat available? – Szabad ez a hely?"),
            Triple("break", "szünet; eltör; megszeg", "Let's take a break. – Tartsunk szünetet."),
            Triple("charge", "díj; tölt; megvádol", "Charge your phone. – Töltsd fel a telefonod."),
            Triple("fair", "igazságos; vásár; szőke", "That's fair. – Ez igazságos."),
            Triple("get", "kap; szerez; megért; válik", "I get it. – Értem."),
            Triple("issue", "probléma; ügy; kiadás; kibocsát", "We have an issue. – Van egy problémánk."),
            Triple("kind", "kedves; fajta; típus", "What kind? – Milyen fajta?"),
            Triple("leave", "elmegy; otthagy; szabadság", "Leave it here. – Hagyd itt."),
            Triple("matter", "számít; ügy; dolog; anyag", "It doesn't matter. – Nem számít."),
            Triple("mean", "jelent; szándékozik; fukar", "What do you mean? – Mire gondolsz?"),
            Triple("miss", "hiányol; lekésik; kisasszony", "I miss you. – Hiányzol."),
            Triple("notice", "észrevesz; értesítés; közlemény", "Did you notice? – Észrevetted?"),
            Triple("point", "pont; lényeg; rámutat", "What's the point? – Mi értelme?"),
            Triple("right", "helyes; jobb; jog; azonnal", "You're right. – Igazad van."),
            Triple("run", "fut; működtet; vezet; futás", "The app runs well. – Az alkalmazás jól működik."),
            Triple("save", "ment; megspórol; megment", "Save the file. – Mentsd el a fájlt."),
            Triple("since", "óta; mivel; azóta", "Since Monday. – Hétfő óta."),
            Triple("still", "még mindig; csendes; állókép", "I still remember. – Még mindig emlékszem."),
            Triple("support", "támogat; alátámaszt; ügyfélszolgálat", "We support the project. – Támogatjuk a projektet."),
            Triple("turn", "fordul; fordulat; sor; bekapcsol", "Turn left. – Fordulj balra."),
            Triple("value", "érték; értékel; fontosság", "Good value. – Jó ár-érték arány."),
            Triple("wonder", "tűnődik; csoda; kíváncsi", "I wonder why. – Kíváncsi vagyok, miért.")
        )
    }
    val dictionaryMatches = (customDictionaryMatches + dictionaryEntries.filter {
        dictionaryQuery.isNotBlank() && (it.first.contains(dictionaryQuery.trim(), true) ||
            it.second.contains(dictionaryQuery.trim(), true))
    }).distinctBy { it.first.lowercase(Locale.ROOT) }.take(25)
    val qualityMetrics = remember(source, translation) {
        val srcWords = source.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        val dstWords = translation.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        val ratio = if (srcWords > 0) dstWords.toDouble() / srcWords else 0.0
        Triple(srcWords, dstWords, ratio)
    }
    val contextHints = remember(context, source, tone) {
        buildList {
            val text = (context + " " + source).lowercase()
            if (text.contains("you") || text.contains("you’re") || text.contains("you're"))
                add("A „you” lehet tegező vagy magázó: döntsd el a beszélő és a címzett viszonya alapján.")
            if (text.contains("you") && (text.contains("love") || text.contains("dear")))
                add("Kapcsolati helyzet: az érzelmi töltet és a megszólítás természetessége fontos.")
            if (text.contains("can") || text.contains("could") || text.contains("would"))
                add("A modalitás udvariasságot vagy lehetőséget is kifejezhet; ne csak szó szerint fordítsd.")
            if (text.contains("right") || text.contains("fine") || text.contains("fair"))
                add("Lehetséges többjelentésű szó: ellenőrizd a mondatkörnyezetet.")
            if (text.contains("!") || text.contains("?"))
                add("Őrizd meg a mondat kommunikációs szándékát és az írásjelek hangulatát.")
            if (context.isBlank()) add("Adj meg helyzetet (pl. baráti beszélgetés, ügyfélszolgálat, munkahely), hogy pontosabb legyen az elemzés.")
            add("Választott stílus: $tone. A javaslat iránymutatás, nem automatikus fordítás.")
        }
    }
    Column(
        Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(onClick = onBack) { Text("← Vissza") }
        Text("PLATINUM • NEXT WAVE", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text("Intelligens fordítási eszközök", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Két használható alapmodul: kontextus-brief és determinisztikus minőségellenőrzés.")
        PremiumCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("🧠 Smart Context Translation", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("A modul felismeri a gyakori fordítási döntési pontokat, és előkészíti a fordítási briefet. Nem állít elő bizonyítatlan AI-pontszámot.")
                OutlinedTextField(source, { source = it }, Modifier.fillMaxWidth(), label = { Text("Angol forrásszöveg") }, minLines = 2)
                OutlinedTextField(context, { context = it }, Modifier.fillMaxWidth(), label = { Text("Helyzet / kontextus (opcionális)") }, minLines = 2)
                Text("Hangnem")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Természetes", "Hivatalos", "Közvetlen").forEach { value ->
                        if (tone == value) Button(onClick = { tone = value }) { Text(value) }
                        else OutlinedButton(onClick = { tone = value }) { Text(value) }
                    }
                }
                Text("Elemzési javaslatok", fontWeight = FontWeight.SemiBold)
                contextHints.forEach { Text("• $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        PremiumCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("📊 Translation Quality", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Számok, nevek, tagadás és szokatlan hosszeltérés ellenőrzése. A figyelmeztetés nem bizonyít hibát, a szöveget nem módosítja.")
                OutlinedTextField(source, { source = it; report = null }, Modifier.fillMaxWidth(), label = { Text("Angol forrás") }, minLines = 2)
                OutlinedTextField(translation, { translation = it; report = null }, Modifier.fillMaxWidth(), label = { Text("Magyar fordítás") }, minLines = 2)
                Button(onClick = { report = TranslationQualityAudit.inspect(source, translation) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Fordítás ellenőrzése")
                }
                report?.let { result ->
                    Text(if (result.isEmpty) "✓ Nem találtam ismert ellenőrzési jelzést." else "Ellenőrzési jelzések: ${result.findings.size}", fontWeight = FontWeight.Bold)
                    result.findings.forEach { finding ->
                        Text("• ${finding.severity}: ${finding.message}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (result.isEmpty) Text("Ez nem teljes nyelvtani vagy szemantikai bizonyítvány; a természetes megfogalmazást továbbra is érdemes átolvasni.")
                }
            }
        }
        PremiumCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("🔁 Dual Translation", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Két fordítási változat összevetése ugyanahhoz a forráshoz. A jelzések ellenőrzési támpontok, nem automatikus minőségi rangsorok.")
                OutlinedTextField(source, { source = it }, Modifier.fillMaxWidth(), label = { Text("Közös angol forrás") }, minLines = 2)
                OutlinedTextField(alternativeA, { alternativeA = it }, Modifier.fillMaxWidth(), label = { Text("A fordítás") }, minLines = 2)
                OutlinedTextField(alternativeB, { alternativeB = it }, Modifier.fillMaxWidth(), label = { Text("B fordítás") }, minLines = 2)
                if (alternativeA.isNotBlank() && alternativeB.isNotBlank()) {
                    val aAudit = TranslationQualityAudit.inspect(source, alternativeA)
                    val bAudit = TranslationQualityAudit.inspect(source, alternativeB)
                    Text("A változat: ${aAudit.findings.size} jelzés · B változat: ${bAudit.findings.size} jelzés",
                        fontWeight = FontWeight.SemiBold)
                    Text("A: ${alternativeA.trim().split(Regex("\\s+")).size} szó · B: ${alternativeB.trim().split(Regex("\\s+")).size} szó",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("A kisebb jelzésszám önmagában nem jelenti azt, hogy a fordítás jobb. Ellenőrizd a jelentést, a hangnemet és a természetességet is.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        PremiumCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("📖 Dictionary Pro • Offline", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Beépített mini-szótár gyakori többjelentésű szavakkal és példamondatokkal. A keresés helyben működik.")
                OutlinedTextField(dictionaryQuery, { dictionaryQuery = it }, Modifier.fillMaxWidth(),
                    label = { Text("Angol szó vagy magyar jelentés") }, singleLine = true)
                if (dictionaryQuery.isBlank()) {
                    Text("Kezdj el gépelni a szótár kereséséhez.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (dictionaryMatches.isEmpty()) {
                    Text("Nincs találat a beépített mini-szótárban. Próbálj másik kifejezést.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    dictionaryMatches.forEach { item ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(item.first, fontWeight = FontWeight.Bold)
                            Text(item.second)
                            Text(item.third, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text("A szótár jelenleg beépített alapkészlet, nem teljes értékű lexikai adatbázis.", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Saját, tartós szótár: $dictionaryCount bejegyzés", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("text/*", "text/csv", "application/vnd.ms-excel")) }) { Text("CSV import") }
                    OutlinedButton(onClick = { exportLauncher.launch("platinum_dictionary.csv") }) { Text("CSV export") }
                }
                Text(dictionaryStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        PremiumCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("🗣 Pronunciation Master", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("A készülék Android szövegfelolvasó motorjával hallgathatod meg az angol szöveget.")
                OutlinedTextField(source, { source = it }, Modifier.fillMaxWidth(), label = { Text("Angol szöveg a kiejtéshez") }, minLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (accent == "US") Button(onClick = { accent = "US" }) { Text("🇺🇸 Amerikai") }
                    else OutlinedButton(onClick = { accent = "US" }) { Text("🇺🇸 Amerikai") }
                    if (accent == "UK") Button(onClick = { accent = "UK" }) { Text("🇬🇧 Brit") }
                    else OutlinedButton(onClick = { accent = "UK" }) { Text("🇬🇧 Brit") }
                }
                Button(onClick = {
                    val locale = if (accent == "UK") Locale.UK else Locale.US
                    val languageResult = tts.setLanguage(locale)
                    speechMessage = when {
                        languageResult == TextToSpeech.LANG_MISSING_DATA || languageResult == TextToSpeech.LANG_NOT_SUPPORTED ->
                            "A kiválasztott angol hang nem érhető el. Telepíts angol beszédhangot az Android szövegfelolvasó beállításaiban."
                        source.isBlank() -> "Írj be egy angol szöveget a felolvasáshoz."
                        !ttsReady -> "A beszédmotor még nem áll készen. Próbáld újra néhány másodperc múlva."
                        else -> {
                            tts.setSpeechRate(0.88f)
                            tts.speak(source, TextToSpeech.QUEUE_FLUSH, null, "platinum_pronunciation")
                            "Felolvasás indítva: $accent hangbeállítás."
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("▶ Kiejtés meghallgatása") }
                Text(speechMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("A tényleges akcentus a telefonon telepített beszédhangtól függ; ez nem fonetikai pontozó és nem beszédfelismeréses értékelés.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        PremiumCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("✨ Synonym Intelligence", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Offline, szerkesztett szinonimajavaslatok. A megfelelő szó kiválasztása mindig a mondatkörnyezettől függ.")
                OutlinedTextField(synonymQuery, { synonymQuery = it }, Modifier.fillMaxWidth(),
                    label = { Text("Angol szó (pl. important, good, help)") }, singleLine = true)
                val suggestions = SynonymEngine.suggest(synonymQuery)
                if (synonymQuery.isNotBlank() && suggestions.isEmpty()) Text("Ehhez a szóhoz még nincs offline javaslat.")
                suggestions.forEach { (word, meaning) -> Text("• $word — $meaning") }
                Text("A szókészlet kurált alapkészlet, nem teljes tezaurusz.", style = MaterialTheme.typography.labelSmall)
            }
        }
        PremiumCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("🎙 Pronunciation Assessment", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Illeszd be a beszédfelismerő által felismert szöveget. A modul szóhibaarány-alapú összevetést készít a célmondattal; ez nem fonetikai vagy akcentusértékelés.")
                OutlinedTextField(source, { source = it }, Modifier.fillMaxWidth(), label = { Text("Célmondat (angol)") }, minLines = 2)
                OutlinedButton(onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Olvasd fel az angol célmondatot")
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    }
                    runCatching { speechLauncher.launch(intent) }.onFailure {
                        speechMessage = "A készüléken nem érhető el kompatibilis beszédfelismerő szolgáltatás."
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("🎙 Beszéd felismerése (angol)") }
                Text(speechMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(recognizedSpeech, { recognizedSpeech = it }, Modifier.fillMaxWidth(), label = { Text("Felismert beszéd szövege – szerkeszthető") }, minLines = 2)
                if (source.isNotBlank() && recognizedSpeech.isNotBlank()) {
                    val assessment = PronunciationAssessment.assess(source, recognizedSpeech)
                    Text("Szövegegyezés: ${assessment.scorePercent}% ", fontWeight = FontWeight.Bold)
                    Text("Helyettesítés: ${assessment.substitutions} · Kihagyás: ${assessment.deletions} · Betoldás: ${assessment.insertions}")
                }
            }
        }
        PremiumCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("📊 Infinity Quality Dashboard", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Átlátható alapmutatók az aktuálisan megadott forrásról és fordításról.")
                Text("Forrás: ${qualityMetrics.first} szó · ${source.length} karakter")
                Text("Fordítás: ${qualityMetrics.second} szó · ${translation.length} karakter")
                Text("Szóarány: ${if (qualityMetrics.first > 0) String.format(Locale.ROOT, "%.2f", qualityMetrics.third) else "—"}")
                val dashboardAudit = if (source.isNotBlank() || translation.isNotBlank())
                    TranslationQualityAudit.inspect(source, translation) else null
                Text("Ellenőrzési jelzések: ${dashboardAudit?.findings?.size ?: 0}", fontWeight = FontWeight.SemiBold)
                dashboardAudit?.findings?.forEach { finding ->
                    Text("• ${finding.severity}: ${finding.message}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Ezek leíró mutatók és szabályalapú jelzések, nem objektív fordítási pontosság vagy AI-minőségi pontszám.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        PremiumCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🧠 Infinity AI Translation Engine", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("A jelenlegi kiadásban a Smart Context modul szabályalapú fordítási briefet készít. Valódi generatív AI-fordítás csak külön konfigurált és engedélyezett fordítószolgáltatás csatlakoztatásával aktiválható; ezt a verzió nem színleli.")
                Text("A következő integrációs lépés: szolgáltatófüggetlen adapter, felhasználói API-kulcskezelés, adatvédelmi tájékoztatás és offline tartalékútvonal.")
            }
        }
        PremiumCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("✨ Platinum Motion / 💎 Infinity Global UI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("A projektben már létezik központi Infinity paletta és csökkentett animációs segéd. A teljes régi View-felület egységesítése és a mozgásprofilok minden képernyőre kiterjesztése még migrációs feladat.")
                Text("Javasolt következő lépés: képernyőnkénti paletta-audit, rendszeres animációskála-ellenőrzés, majd golden screenshot és fizikai eszközteszt.")
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
