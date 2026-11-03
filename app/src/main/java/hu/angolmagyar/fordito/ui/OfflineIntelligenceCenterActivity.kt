package hu.angolmagyar.fordito.ui
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import hu.angolmagyar.fordito.Prefs
import hu.angolmagyar.fordito.core.OfflineIntelligenceRepository
import hu.angolmagyar.fordito.core.db.ReviewScheduleEntity
import hu.angolmagyar.fordito.ui.design.PremiumCard
import hu.angolmagyar.fordito.ui.theme.AngolMagyarTheme
import kotlinx.coroutines.launch
class OfflineIntelligenceCenterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Prefs(this)
        setContent {
            AngolMagyarTheme(isTrueBlack = prefs.oledBlack) {
                OfflineIntelligenceCenterScreen(onBack = { finish() })
            }
        }
    }
}
@Composable
private fun OfflineIntelligenceCenterScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { OfflineIntelligenceRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var memoryQuery by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Helyi adatok betöltése…") }
    var glossaryCount by remember { mutableStateOf<Int?>(null) }
    var memoryCount by remember { mutableStateOf<Int?>(null) }
    var dueCount by remember { mutableStateOf<Int?>(null) }
    var auditReport by remember { mutableStateOf<hu.angolmagyar.fordito.OfflineIntelligencePipeline.AuditReport?>(null) }
    var memoryMatches by remember { mutableStateOf(emptyList<hu.angolmagyar.fordito.OfflineMemoryRanker.Match>()) }
    var dueItems by remember { mutableStateOf(emptyList<ReviewScheduleEntity>()) }
    var busy by remember { mutableStateOf(false) }
    fun refreshOverview() {
        scope.launch {
            busy = true
            runCatching {
                glossaryCount = repository.glossaryCount()
                memoryCount = repository.translationMemoryCount()
                dueCount = repository.dueReviewCount()
                dueItems = repository.dueReviews(limit = 8)
                status = "Az adatok helyben, offline módban frissültek."
            }.onFailure {
                status = "Nem sikerült betölteni a helyi adatokat: ${it.message ?: "ismeretlen hiba"}"
            }
            busy = false
        }
    }
    LaunchedEffect(Unit) { refreshOverview() }
    Column(
        Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OutlinedButton(onClick = onBack) { Text("← Vissza") }
        Text("DIAMOND • OFFLINE INTELLIGENCE", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text("Minőség és tanulási központ", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Helyi szótár, fordítási memória és ismétlési állapot – internetkapcsolat nélkül.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        PremiumCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("HELYI ÁLLAPOT", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text("Szótári bejegyzések: ${glossaryCount ?: "…"}")
                Text("Fordítási memória: ${memoryCount ?: "…"}")
                Text("Esedékes ismétlések: ${dueCount ?: "…"}")
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { refreshOverview() }, enabled = !busy) { Text("Állapot frissítése") }
            }
        }
        PremiumCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("FORDÍTÁSMINŐSÉG-ELLENŐRZÉS", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                OutlinedTextField(value = source, onValueChange = { source = it; auditReport = null }, modifier = Modifier.fillMaxWidth(), label = { Text("Angol forrásszöveg") }, minLines = 2)
                OutlinedTextField(value = target, onValueChange = { target = it; auditReport = null }, modifier = Modifier.fillMaxWidth(), label = { Text("Magyar fordítás") }, minLines = 2)
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            runCatching { repository.auditTranslation(source, target) }
                                .onSuccess { auditReport = it; status = "Az ellenőrzés befejeződött." }
                                .onFailure { status = "Ellenőrzési hiba: ${it.message ?: "ismeretlen hiba"}" }
                            busy = false
                        }
                    },
                    enabled = !busy && source.isNotBlank() && target.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Offline ellenőrzés futtatása") }
                auditReport?.let { report ->
                    Text(if (report.hasWarnings) "${report.warningCount} figyelmeztetés" else "Nem találtunk eltérést a vizsgált szabályok szerint.", fontWeight = FontWeight.SemiBold, color = if (report.hasWarnings) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    report.invariantFindings.forEach { Text("• ${it.message}", style = MaterialTheme.typography.bodyMedium) }
                    report.terminologyFindings.forEach { Text("• ${it.sourceTerm}: ${it.message} Elvárt: ${it.expectedTargets.joinToString()}", style = MaterialTheme.typography.bodyMedium) }
                    Text("Ez figyelmeztető ellenőrzés, nem bizonyítja a fordítás teljes helyességét, és nem módosítja a szöveget.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        PremiumCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("FORDÍTÁSI MEMÓRIA", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                OutlinedTextField(value = memoryQuery, onValueChange = { memoryQuery = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Keresendő angol kifejezés") }, singleLine = true)
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            runCatching { repository.rankSimilarTranslations(memoryQuery, 8) }
                                .onSuccess { memoryMatches = it; status = "${it.size} helyi memória-találat." }
                                .onFailure { status = "Memóriakeresési hiba: ${it.message ?: "ismeretlen hiba"}" }
                            busy = false
                        }
                    },
                    enabled = !busy && memoryQuery.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Hasonló fordítások keresése") }
                if (memoryMatches.isEmpty() && memoryQuery.isNotBlank()) {
                    Text("Még nincs megjelenített találat. Indíts keresést.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                memoryMatches.forEach { match ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(match.item.source, fontWeight = FontWeight.SemiBold)
                        Text(match.item.target)
                        Text("Hasonlósági pontszám: ${"%.2f".format(java.util.Locale.ROOT, match.score)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("A keresés offline, lexikai hasonlóság alapján működik; nem szemantikus AI-értékelés.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        PremiumCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("KÖVETKEZŐ ISMÉTLÉSEK", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                if (dueItems.isEmpty()) {
                    Text("Nincs megjeleníthető esedékes ismétlés.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    dueItems.forEach { item ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(item.englishKey, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            Text("${item.intervalDays} nap", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                OutlinedButton(onClick = { refreshOverview() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Ismétlések frissítése") }
            }
        }
    }
}
