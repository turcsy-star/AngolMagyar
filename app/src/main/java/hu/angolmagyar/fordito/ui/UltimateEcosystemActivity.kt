package hu.angolmagyar.fordito.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hu.angolmagyar.fordito.Prefs
import hu.angolmagyar.fordito.ultimate.UltimateLearningEngine
import hu.angolmagyar.fordito.core.db.AppDatabase
import hu.angolmagyar.fordito.core.db.DictionaryEntity
import hu.angolmagyar.fordito.core.db.ReviewScheduleEntity
import hu.angolmagyar.fordito.intelligence.PronunciationAssessment
import hu.angolmagyar.fordito.ui.design.PremiumCard
import hu.angolmagyar.fordito.ui.theme.AngolMagyarTheme

private const val MAX_DOCUMENT_CHARS = 200_000

class UltimateEcosystemActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Prefs(this)
        setContent { AngolMagyarTheme(isTrueBlack = prefs.oledBlack) { UltimateEcosystemScreen(onBack = { finish() }) } }
    }
}

@Composable
private fun UltimateEcosystemScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { context.getSharedPreferences("ultimate_ecosystem_v4", 0) }
    val database = remember { AppDatabase.get(context) }
    val scope = rememberCoroutineScope()
    var savedPhrase by remember { mutableStateOf(store.getString("saved_phrase", "") ?: "") }
    var phraseInput by remember { mutableStateOf("") }
    var phraseMeaning by remember { mutableStateOf("") }
    var phraseExample by remember { mutableStateOf("") }
    var dictionaryCount by remember { mutableIntStateOf(0) }
    var dueReviewCount by remember { mutableIntStateOf(0) }
    var pronunciationTarget by remember { mutableStateOf("Could we have a table for two, please?") }
    var scenarioIndex by remember { mutableIntStateOf(0) }
    var response by remember { mutableStateOf("") }
    var documentText by remember { mutableStateOf("") }
    var speechText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Offline-first helyi eszközök készen állnak.") }
    var cefrScore by remember { mutableIntStateOf(store.getInt("cefr_score", 35)) }
    var completedSessions by remember { mutableIntStateOf(store.getInt("sessions", 0)) }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                val buffer = CharArray(MAX_DOCUMENT_CHARS + 1)
                var filled = 0
                while (filled < buffer.size) {
                    val n = reader.read(buffer, filled, buffer.size - filled)
                    if (n < 0) break
                    filled += n
                }
                String(buffer, 0, filled) to (filled > MAX_DOCUMENT_CHARS)
            } ?: error("A dokumentum nem olvasható")
        }.onSuccess { (text, truncated) ->
            documentText = text.take(MAX_DOCUMENT_CHARS)
            status = if (truncated) "Dokumentum beolvasva, de csak az első ${MAX_DOCUMENT_CHARS} karaktert elemeztük (a fájl nagyobb)."
            else "Dokumentum beolvasva."
        }
            .onFailure { status = "Dokumentumbeolvasási hiba: ${it.message}" }
    }
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        speechText = if (result.resultCode == Activity.RESULT_OK)
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty() else ""
        status = if (speechText.isBlank()) "Nem érkezett beszédfelismerési eredmény." else "A rendszer beszédfelismerője szöveget adott vissza; a pontosságot ellenőrizd." 
    }
    LaunchedEffect(Unit) {
        runCatching {
            dictionaryCount = database.dictionaryDao().count()
            dueReviewCount = database.reviewScheduleDao().dueCount()
        }.onFailure { status = "A tanulói szótár nem tölthető be: ${it.message}" }
    }
    val pronunciation = remember(pronunciationTarget, speechText) {
        if (pronunciationTarget.isNotBlank() && speechText.isNotBlank()) PronunciationAssessment.assess(pronunciationTarget, speechText) else null
    }
    val scenario = UltimateLearningEngine.scenarios[scenarioIndex]
    val wordCount = UltimateLearningEngine.wordCount(documentText)
    Column(Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onBack) { Text("← Vissza") }
        PremiumCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("DIAMOND • ULTIMATE ECOSYSTEM", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text("Ultimate Intelligence Ecosystem 4.1", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("10 integrált fejlesztési terület • helyi, adatvédelmi szempontú eszközök • átlátható képességjelölés")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric("CEFR becslés", UltimateLearningEngine.classifyLevel(cefrScore))
                    Metric("Gyakorlások", completedSessions.toString())
                    Metric("Dokumentum", "$wordCount szó")
                }
            }
        }
        SectionTitle("1. DIAMOND INTELLIGENCE CORE", "Tanulási profil és fejlesztési irány")
        PremiumCard {
            Text("Önértékelési szint: ${UltimateLearningEngine.classifyLevel(cefrScore)}", fontWeight = FontWeight.SemiBold)
            Slider(value = cefrScore.toFloat(), onValueChange = { cefrScore = it.toInt() }, valueRange = 0f..100f)
            Text("$cefrScore / 100 • A szintbecslés önértékelési segédlet, nem hivatalos nyelvvizsga.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = { store.edit().putInt("cefr_score", cefrScore).apply(); status = "Tanulási profil mentve ezen az eszközön." }) { Text("Profil mentése") }
        }
        SectionTitle("2. PLATINUM LANGUAGE MASTERY", "Kompetenciakövetés és CEFR")
        PremiumCard {
            Text("A napi gyakorlás után növeld a saját fejlődési mutatódat. A jelenlegi becslés: ${UltimateLearningEngine.classifyLevel(cefrScore)}.")
            Text("Javasolt fókusz: rendszeres szókincsismétlés, nyelvtani példamondatok és aktív beszédgyakorlás.")
        }
        SectionTitle("3. INFINITY SPEECH INTELLIGENCE", "Beszédfelismerés és kiejtésgyakorlás")
        PremiumCard {
            Button(onClick = { runCatching { speechLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US").putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak in English")) }.onFailure { status = "A beszédfelismerő nem érhető el: ${it.message}" } }) { Text("🎙 Angol beszéd felismerése") }
            OutlinedTextField(value = pronunciationTarget, onValueChange = { pronunciationTarget = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Gyakorlómondat / referencia") })
            if (speechText.isNotBlank()) {
                Text("Felismerés: $speechText")
                pronunciation?.let { result ->
                    Text("Szövegegyezési mutató: ${result.scorePercent}%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Eltérések: ${result.substitutions} csere • ${result.deletions} hiány • ${result.insertions} többlet")
                    Text("Ez a beszédfelismerő átiratának szöveges összehasonlítása, nem fonetikai kiejtésmérés.", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("A felismeréshez a készülék beszédszolgáltatása szükséges; internetkapcsolatot igényelhet.", style = MaterialTheme.typography.bodySmall)
        }
        SectionTitle("4. DIAMOND DOCUMENT INTELLIGENCE", "Helyi dokumentumbeolvasás és szövegelemzés")
        PremiumCard {
            Button(onClick = { documentPicker.launch(arrayOf("text/plain", "text/csv", "application/json", "text/markdown")) }) { Text("Szöveges dokumentum megnyitása") }
            if (documentText.isNotBlank()) {
                Text("${wordCount} szó • kb. ${UltimateLearningEngine.estimateReadingMinutes(documentText)} perc olvasás")
                Text(documentText.take(1800))
            }
        }
        SectionTitle("5. PLATINUM MEMORY UNIVERSE", "Saját szótár és ismétlési rendszer összekapcsolása")
        PremiumCard {
            Text("A saját kifejezések a közös Room-szótárba kerülnek, így megjelennek a tanulókártyák és az ismétlő modul számára is.")
            OutlinedTextField(value = phraseInput, onValueChange = { phraseInput = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Angol kifejezés") }, singleLine = true)
            OutlinedTextField(value = phraseMeaning, onValueChange = { phraseMeaning = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Magyar jelentés") }, singleLine = true)
            OutlinedTextField(value = phraseExample, onValueChange = { phraseExample = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Példamondat (nem kötelező)") }, minLines = 2)
            Button(onClick = {
                val english = phraseInput.trim().replace(Regex("\\s+"), " "); val meaning = phraseMeaning.trim()
                if (english.isBlank() || meaning.isBlank()) status = "Az angol kifejezés és a magyar jelentés is kötelező."
                else scope.launch {
                    runCatching {
                        database.dictionaryDao().upsertAll(listOf(DictionaryEntity(englishKey = english.lowercase(java.util.Locale.ROOT), english = english, hungarian = meaning, example = phraseExample.trim())))
                        val existing = database.reviewScheduleDao().find(english.lowercase(java.util.Locale.ROOT))
                        if (existing == null) database.reviewScheduleDao().upsert(ReviewScheduleEntity(englishKey = english.lowercase(java.util.Locale.ROOT)))
                        dictionaryCount = database.dictionaryDao().count()
                        dueReviewCount = database.reviewScheduleDao().dueCount()
                        phraseInput = ""; phraseMeaning = ""; phraseExample = ""
                        status = "Kifejezés mentve a közös szótárba; bekerült az ismétlési rendszerbe."
                    }.onFailure { status = "Mentési hiba: ${it.message ?: "ismeretlen hiba"}" }
                }
            }) { Text("Mentés a tanulói szótárba") }
            Text("Saját szótár: $dictionaryCount kifejezés", fontWeight = FontWeight.SemiBold)
            Text("Esedékes ismétlések: $dueReviewCount", style = MaterialTheme.typography.bodySmall)
            if (savedPhrase.isNotBlank()) Text("Korábbi gyorsmentés: $savedPhrase", style = MaterialTheme.typography.bodySmall)
            Text("Az esedékes ismétléseket a Platinum Memory Coach / Intelligens ismétlő képernyőn végezheted el.", style = MaterialTheme.typography.bodySmall)
        }
        SectionTitle("6. INFINITY SCENARIO SIMULATOR", "Életszerű angol kommunikációs helyzetek")
        PremiumCard {
            Text(scenario.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(scenario.prompt)
            OutlinedTextField(value = response, onValueChange = { response = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Angol válaszod") }, minLines = 3)
            Text("Hasznos fordulat: ${scenario.hint}", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { pronunciationTarget = scenario.hint; speechText = ""; status = "A fordulat bekerült a beszédgyakorló referenciamondatába." }) { Text("Használat beszédgyakorló mondatként") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { scenarioIndex = (scenarioIndex + UltimateLearningEngine.scenarios.size - 1) % UltimateLearningEngine.scenarios.size; response = "" }) { Text("Előző") }
                Button(onClick = { if (response.isNotBlank()) { scenarioIndex = (scenarioIndex + 1) % UltimateLearningEngine.scenarios.size; response = ""; completedSessions++; store.edit().putInt("sessions", completedSessions).apply(); status = "Gyakorlási kör rögzítve." } else { status = "A gyakorlás rögzítéséhez előbb írj egy angol választ." } }) { Text("Következő helyzet") }
            }
        }
        SectionTitle("7. PLATINUM PRIVACY & SECURITY", "Helyi adattárolás és adatvédelmi átláthatóság")
        PremiumCard {
            Text("A tanulási profil SharedPreferences-ben, a szótár és az ismétlési ütemezés a helyi Room-adatbázisban tárolódik.")
            Text("A beszédfelismerés az Android rendszer beszédszolgáltatását használja; annak adatkezelését és esetleges hálózati továbbítását a szolgáltató szabályzata határozza meg.", style = MaterialTheme.typography.bodySmall)
            Text("Biometrikus zárolás és teljes alkalmazásszintű titkosítás nincs bekapcsolva ezen a képernyőn; a meglévő alkalmazás biztonsági/backup funkciói külön használhatók.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { store.edit().clear().apply(); savedPhrase = ""; cefrScore = 35; completedSessions = 0; status = "Az Ultimate modul helyi adatai törölve." }) { Text("Modul saját adatainak törlése") }
        }
        SectionTitle("8. DIAMOND PERFORMANCE ENGINE", "Könnyű helyi diagnosztika")
        PremiumCard {
            val runtime = Runtime.getRuntime(); val used = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
            Text("JVM memóriahasználat: kb. $used MB / ${runtime.maxMemory() / (1024 * 1024)} MB")
            Text("Ez pillanatfelvétel, nem teljesítménybenchmark.")
        }
        SectionTitle("9. PLATINUM GLOBAL EXPERIENCE", "Egységes alkalmazásélmény")
        PremiumCard { Text("A képernyő a meglévő AngolMagyar Material 3 témát, prémium kártyákat és rendszer-navigációt használja. A teljes alkalmazásra kiterjedő témamigráció külön feladat.") }
        SectionTitle("10. INFINITY ACHIEVEMENT UNIVERSE", "Helyi gyakorlási mérföldkövek")
        PremiumCard {
            Text("Teljesített szituációs gyakorlások: $completedSessions")
            Text(if (completedSessions >= 20) "Diamond gyakorló" else if (completedSessions >= 5) "Platinum gyakorló" else "Kezdő felfedező")
            LinearProgressIndicator(progress = { (completedSessions / 20f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        }
        Text(status, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        Text("Fontos: a külső AI, valós idejű fonémaelemzés, OCR, biometria és automatikus felhőszinkron csak külön integrációval és engedélyekkel valósítható meg; ez a kiadás nem állítja ezek meglétét.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun SectionTitle(title: String, subtitle: String) { Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
@Composable private fun RowScope.Metric(label: String, value: String) { Column(Modifier.weight(1f)) { Text(value, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.labelSmall) } }
