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
import hu.angolmagyar.fordito.DiamondIntegration
import hu.angolmagyar.fordito.PerformanceRegressionSuite
import hu.angolmagyar.fordito.SmartContextTranslation
import hu.angolmagyar.fordito.TranslationQuality
import hu.angolmagyar.fordito.TranslationQualityAudit
import hu.angolmagyar.fordito.DualTranslation
import hu.angolmagyar.fordito.DictionaryPro
import hu.angolmagyar.fordito.PronunciationCoach
import hu.angolmagyar.fordito.PhrasebookStore
import hu.angolmagyar.fordito.ui.design.DesignAudit
import hu.angolmagyar.fordito.ui.design.InfinityGlobalUi
import hu.angolmagyar.fordito.ui.design.PremiumCard
import hu.angolmagyar.fordito.ui.design.PremiumMotion
import hu.angolmagyar.fordito.ui.design.rememberAccessibilitySnapshot
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
    var report by remember { mutableStateOf<TranslationQuality.QualityResult?>(null) }
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
    var saveMessage by remember { mutableStateOf("") }
    var attempts by remember { mutableStateOf(emptyList<Int>()) }
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
    val speakNow: (String, Float) -> Unit = { text, rate ->
        val languageResult = tts.setLanguage(if (accent == "UK") Locale.UK else Locale.US)
        speechMessage = when {
            languageResult == TextToSpeech.LANG_MISSING_DATA || languageResult == TextToSpeech.LANG_NOT_SUPPORTED ->
                "A kiválasztott angol hang nem érhető el. Telepíts angol beszédhangot az Android szövegfelolvasó beállításaiban."
            text.isBlank() -> "Írj be egy angol szöveget a felolvasáshoz."
            !ttsReady -> "A beszédmotor még nem áll készen. Próbáld újra néhány másodperc múlva."
            else -> {
                tts.setSpeechRate(rate)
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "platinum_pronunciation")
                "Felolvasás indítva: $accent hangbeállítás."
            }
        }
    }
    val proHits = remember(dictionaryQuery) { DictionaryPro.search(dictionaryQuery) }
    val qualityMetrics = remember(source, translation) {
        val srcWords = source.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        val dstWords = translation.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        val ratio = if (srcWords > 0) dstWords.toDouble() / srcWords else 0.0
        Triple(srcWords, dstWords, ratio)
    }
    val preferredTone = when (tone) {
        "Hivatalos" -> SmartContextTranslation.Tone.FORMAL
        "Közvetlen" -> SmartContextTranslation.Tone.INFORMAL
        else -> null
    }
    val smartSuggestion = remember(source, translation, preferredTone) {
        SmartContextTranslation.suggest(source, translation.ifBlank { null }, preferredTone)
    }
    val contextHints = remember(smartSuggestion, context, tone) {
        buildList {
            smartSuggestion.notes.forEach { add(it) }
            smartSuggestion.alternatives.take(4).forEach { alt ->
                add("Alternatíva (${alt.tone.name.lowercase()}): „${alt.text}” – ${alt.reason}")
            }
            if (smartSuggestion.ambiguity != SmartContextTranslation.AmbiguityLevel.NONE) {
                add("Kétértelműség szintje: ${smartSuggestion.ambiguity.name.lowercase()}.")
            }
            if (context.isBlank()) add("Adj meg helyzetet (pl. baráti beszélgetés, ügyfélszolgálat), hogy pontosabb legyen az elemzés.")
            add("Választott stílus: $tone. A javaslat iránymutatás, nem automatikus fordítás.")
            if (isEmpty()) add("Írj be forrásszöveget a Smart Context elemzéshez.")
        }
    }
    Column(
        Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(onClick = onBack) { Text("← Vissza") }
        Text("DIAMOND • NEXT WAVE", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
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
                Text("Nyelvtani és terminológiai figyelmeztetések (szám, név, tagadás, hossz, angol maradvány, struktúra). Heurisztikus pontszám – nem AI-értékelés.")
                OutlinedTextField(source, { source = it; report = null }, Modifier.fillMaxWidth(), label = { Text("Angol forrás") }, minLines = 2)
                OutlinedTextField(translation, { translation = it; report = null }, Modifier.fillMaxWidth(), label = { Text("Magyar fordítás") }, minLines = 2)
                Button(onClick = { report = TranslationQuality.analyze(source, translation) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Fordítás ellenőrzése (v2.64)")
                }
                report?.let { result ->
                    Text(TranslationQuality.summaryLine(result), fontWeight = FontWeight.Bold)
                    if (result.warnings.isEmpty()) {
                        Text("✓ Nem találtam ismert ellenőrzési jelzést.")
                        Text("Ez nem teljes nyelvtani vagy szemantikai bizonyítvány; a természetes megfogalmazást továbbra is érdemes átolvasni.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        result.warnings.forEach { w ->
                            Text("• [${w.severity}] ${w.messageHu}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (result.needsHumanReview) {
                            Text("Emberi átnézés javasolt.", color = MaterialTheme.colorScheme.secondary)
                        }
                    }
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
                    val cmp = DualTranslation.compare(source, alternativeA, alternativeB)
                    Text("Hasonlóság: ${cmp.similarityPercent}% · A: ${cmp.wordsA} szó · B: ${cmp.wordsB} szó", fontWeight = FontWeight.SemiBold)
                    if (cmp.common.isNotEmpty()) Text("Közös szavak: ${cmp.common.joinToString(", ")}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (cmp.onlyA.isNotEmpty()) Text("Csak az A-ban: ${cmp.onlyA.joinToString(", ")}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (cmp.onlyB.isNotEmpty()) Text("Csak a B-ben: ${cmp.onlyB.joinToString(", ")}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Jelzések – A: ${cmp.findingsA.size} · B: ${cmp.findingsB.size}", fontWeight = FontWeight.SemiBold)
                    cmp.findingsA.forEach { Text("• A: ${it.message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    cmp.findingsB.forEach { Text("• B: ${it.message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    cmp.notes.forEach { Text("• $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            saveMessage = if (PhrasebookStore(appContext).save(source, alternativeA)) "Az A változat mentve a szókincstárba." else "Az A változat nem menthető (üres vagy már szerepel)."
                        }) { Text("A mentése") }
                        OutlinedButton(onClick = {
                            saveMessage = if (PhrasebookStore(appContext).save(source, alternativeB)) "A B változat mentve a szókincstárba." else "A B változat nem menthető (üres vagy már szerepel)."
                        }) { Text("B mentése") }
                    }
                    if (saveMessage.isNotBlank()) Text(saveMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("A kisebb jelzésszám önmagában nem jelenti azt, hogy a fordítás jobb. Ellenőrizd a jelentést, a hangnemet és a természetességet is.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        PremiumCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("📖 Dictionary Pro • Offline", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Beépített szótár szófajokkal, többjelentésű szavakkal, hamis barátokkal és példamondatokkal. Angolul és magyarul is kereshetsz, helyben, ékezet nélkül is.")
                OutlinedTextField(dictionaryQuery, { dictionaryQuery = it }, Modifier.fillMaxWidth(),
                    label = { Text("Angol szó vagy magyar jelentés") }, singleLine = true)
                if (dictionaryQuery.isBlank()) {
                    Text("Kezdj el gépelni a szótár kereséséhez.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (proHits.isEmpty() && customDictionaryMatches.isEmpty()) {
                    Text("Nincs találat. Próbálj másik kifejezést.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    proHits.forEach { hit ->
                        val e = hit.entry
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(e.word, fontWeight = FontWeight.Bold)
                            e.senses.forEach { Text(it.line()) }
                            Text("${e.example} – ${e.exampleHu}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (e.tip.isNotBlank()) Text("Tipp: ${e.tip}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                            OutlinedButton(onClick = {
                                dictionaryStatus = if (PhrasebookStore(appContext).save(e.word, e.senses.first().meanings.first())) "„${e.word}” mentve a szókincstárba." else "„${e.word}” már szerepel a szókincstárban."
                            }) { Text("Mentés szókincstárba") }
                        }
                    }
                    customDictionaryMatches.take(10).forEach { item ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("${item.first} (saját)", fontWeight = FontWeight.Bold)
                            Text(item.second)
                            if (item.third.isNotBlank()) Text(item.third, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Button(onClick = { speakNow(source, 0.88f) }, modifier = Modifier.fillMaxWidth()) { Text("▶ Kiejtés meghallgatása") }
                OutlinedButton(onClick = { speakNow(source, 0.6f) }, modifier = Modifier.fillMaxWidth()) { Text("🐢 Lassú felolvasás") }
                PronunciationCoach.slowChunks(source, 3).forEach { chunk ->
                    OutlinedButton(onClick = { speakNow(chunk, 0.65f) }, modifier = Modifier.fillMaxWidth()) { Text("▶ $chunk") }
                }
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
                    val coach = PronunciationCoach.analyze(source, recognizedSpeech)
                    coach.words.forEach { w ->
                        val mark = when (w.status) {
                            PronunciationCoach.WordStatus.CORRECT -> "✓"
                            PronunciationCoach.WordStatus.WRONG -> "✗"
                            PronunciationCoach.WordStatus.MISSING -> "–"
                        }
                        val extra = when (w.status) {
                            PronunciationCoach.WordStatus.WRONG -> " (hallott: ${w.heard})"
                            PronunciationCoach.WordStatus.MISSING -> " (kimaradt)"
                            else -> ""
                        }
                        Text("$mark ${w.word}$extra", style = MaterialTheme.typography.bodySmall,
                            color = if (w.status == PronunciationCoach.WordStatus.CORRECT) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                    }
                    if (coach.extras.isNotEmpty()) Text("Többlet szavak: ${coach.extras.joinToString(", ")}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    coach.tips.forEach { Text("💡 $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (coach.practiceWords.isNotEmpty()) {
                        Text("Gyakorolj még:", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            coach.practiceWords.take(3).forEach { pw ->
                                OutlinedButton(onClick = { speakNow(pw, 0.6f) }) { Text("▶ $pw") }
                            }
                        }
                    }
                    OutlinedButton(onClick = { attempts = (attempts + coach.scorePercent).takeLast(10) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Próbálkozás rögzítése")
                    }
                    if (attempts.isNotEmpty()) {
                        Text("Próbálkozások: ${attempts.joinToString(" → ") { "$it%" }} · ${PronunciationCoach.trend(attempts)}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
                val dashboardResult = if (source.isNotBlank() || translation.isNotBlank())
                    TranslationQuality.analyze(source, translation) else null
                Text(dashboardResult?.let { TranslationQuality.summaryLine(it) } ?: "Ellenőrzési jelzések: 0", fontWeight = FontWeight.SemiBold)
                dashboardResult?.warnings?.forEach { w ->
                    Text("• [${w.severity}] ${w.messageHu}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Ezek leíró mutatók és szabályalapú jelzések, nem objektív fordítási pontosság vagy AI-minőségi pontszám.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        PremiumCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🔀 Hybrid Translation Engine · v2.65", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Egységes offline/online motorválasztás, fallback és adatvédelmi kapcsolók a core modulban (HybridTranslationEngine).")
                Text("Módok: OFFLINE_ONLY · PREFER_OFFLINE · PREFER_ONLINE · ONLINE_ONLY. A hálózati hívás csak explicit allowNetwork=true esetén indul.")
                Text("A meglévő OnlineTranslator adapterként csatlakoztatható; az offline oldalt a helyi motor / TM / szótár adja.")
            }
        }
        PremiumCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🧠 Infinity AI Translation Engine", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("A jelenlegi kiadásban a Smart Context modul szabályalapú fordítási briefet készít. Valódi generatív AI-fordítás csak külön konfigurált és engedélyezett fordítószolgáltatás csatlakoztatásával aktiválható; ezt a verzió nem színleli.")
                Text("A Hybrid Engine már biztosítja az offline tartalékútvonalat és az adatvédelmi kapcsolókat.")
            }
        }
        val auditRows = remember { DesignAudit.paletteRows() }
        val a11y = rememberAccessibilitySnapshot()
        PremiumCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("✨ Motion Engine · Global UI · Akadálymentesség", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Mozgásprofil: ${PremiumMotion.statusLabel(appContext)} (a Beállításokban módosítható). A gombok nem animálnak: csak hullámeffekt és haptika jelez.")
                Text("Betűméret-skála: ${"%.2f".format(Locale.ROOT, a11y.fontScale)} · TalkBack: ${if (a11y.touchExplorationEnabled) "aktív" else "inaktív"} · Min. érintési cél: 48 dp",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Kontraszt-audit: ${DesignAudit.summaryLine(auditRows)}", fontWeight = FontWeight.SemiBold)
                auditRows.forEach { r ->
                    Text("${if (r.passes) "✓" else "○"} ${r.title}: szöveg ${"%.1f".format(Locale.ROOT, r.textRatio)}:1 · kiemelés ${"%.1f".format(Locale.ROOT, r.accentRatio)}:1",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (r.passes) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                }
                Text("Global UI migráció: ${InfinityGlobalUi.progressLabel()}", fontWeight = FontWeight.SemiBold)
                InfinityGlobalUi.migrationChecklist.forEach { (title, done) ->
                    Text("${if (done) "✓" else "○"} $title", style = MaterialTheme.typography.bodySmall,
                        color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.secondary)
                }
            }
        }
        var suiteReport by remember { mutableStateOf<PerformanceRegressionSuite.Report?>(null) }
        var gateReport by remember { mutableStateOf<DiamondIntegration.GateReport?>(null) }
        PremiumCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("⚡ Performance & Regression Suite · v2.67", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Beépített, hálózat nélküli core regresszió: Smart Context, Translation Quality, Hybrid Engine, Snapshot + latency/heap.")
                Button(onClick = { suiteReport = PerformanceRegressionSuite.runBuiltin() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Regressziós készlet futtatása")
                }
                suiteReport?.let { r ->
                    Text(r.summaryLine(), fontWeight = FontWeight.SemiBold)
                    Text(if (r.allPassed) "✓ Minden ellenőrzés sikeres." else "✗ Hibás ellenőrzések: ${r.failedChecks.size}",
                        color = if (r.allPassed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    r.timings.forEach { t ->
                        Text("• ${t.name}: ${t.ms} ms${if (!t.ok) " ⚠ ${t.detail}" else ""}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    r.failedChecks.forEach { c ->
                        Text("• FAIL ${c.name}: ${c.detail}", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        PremiumCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("💎 Diamond Integration Gate · v3.00", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Kiadási checklist, modul-térkép és opcionális regresszió egy helyen.")
                Button(onClick = { gateReport = DiamondIntegration.evaluate(runRegression = true) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Diamond kapu értékelése")
                }
                gateReport?.let { g ->
                    Text(g.summaryLine(), fontWeight = FontWeight.SemiBold)
                    g.items.forEach { item ->
                        val mark = if (item.done) "✓" else "○"
                        Text("$mark ${item.title}${if (item.note.isNotBlank()) " — ${item.note}" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
