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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hu.angolmagyar.fordito.core.db.AppDatabase
import hu.angolmagyar.fordito.core.db.ReviewScheduleEntity
import hu.angolmagyar.fordito.intelligence.LearningAnalyticsStore
import hu.angolmagyar.fordito.intelligence.ReviewState
import hu.angolmagyar.fordito.intelligence.SpacedRepetition
import hu.angolmagyar.fordito.Prefs
import hu.angolmagyar.fordito.ui.design.PremiumCard
import hu.angolmagyar.fordito.ui.theme.AngolMagyarTheme
import kotlinx.coroutines.launch

class ReviewCoachActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Prefs(this)
        setContent { AngolMagyarTheme(isTrueBlack = prefs.oledBlack) { ReviewCoachScreen(onBack = { finish() }) } }
    }
}

@Composable
private fun ReviewCoachScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val analytics = remember { LearningAnalyticsStore(context) }
    val scope = rememberCoroutineScope()
    var cards by remember { mutableStateOf(emptyList<Triple<String, String, ReviewScheduleEntity>>()) }
    var index by remember { mutableIntStateOf(0) }
    var revealed by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("A tanulókártyák betöltése…") }
    var dueCount by remember { mutableIntStateOf(0) }

    fun reload() {
        scope.launch {
            runCatching {
                val now = System.currentTimeMillis()
                val dictionary = db.dictionaryDao().all()
                dictionary.forEach { entry ->
                    if (db.reviewScheduleDao().find(entry.englishKey) == null) {
                        db.reviewScheduleDao().upsert(ReviewScheduleEntity(englishKey = entry.englishKey, dueAt = now))
                    }
                }
                val due = db.reviewScheduleDao().due(now)
                val all = dictionary.associateBy { it.englishKey }
                cards = due.mapNotNull { state -> all[state.englishKey]?.let { Triple(it.english, it.hungarian, state) } }
                dueCount = cards.size
                index = 0
                revealed = false
                status = if (cards.isEmpty()) "Nincs esedékes kártya. Importálj vagy ments szavakat a saját szótárba." else "${cards.size} esedékes kártya"
            }.onFailure { status = "Betöltési hiba: ${it.message ?: "ismeretlen hiba"}" }
        }
    }
    LaunchedEffect(Unit) { reload() }
    val current = cards.getOrNull(index)
    Column(Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedButton(onClick = onBack) { Text("← Vissza") }
        Text("PLATINUM • MEMORY COACH", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text("Intelligens ismétlő", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (current != null) {
            PremiumCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${index + 1} / ${cards.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(current.first, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    if (revealed) Text(current.second, style = MaterialTheme.typography.titleLarge)
                    else OutlinedButton(onClick = { revealed = true }, modifier = Modifier.fillMaxWidth()) { Text("Jelentés megmutatása") }
                    if (revealed) {
                        Text("Mennyire ment könnyen?", fontWeight = FontWeight.SemiBold)
                        listOf(listOf(0 to "Újra", 3 to "Nehéz"), listOf(4 to "Jó", 5 to "Könnyű")).forEach { rowItems ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowItems.forEach { (grade, label) ->
                                    Button(onClick = {
                                        scope.launch {
                                            val old = current.third
                                            val plan = SpacedRepetition.schedule(ReviewState(old.repetitions, old.intervalDays, old.easeFactor), grade)
                                            db.reviewScheduleDao().upsert(old.copy(repetitions = plan.repetitions, intervalDays = plan.intervalDays, easeFactor = plan.easeFactor, dueAt = plan.dueAt, lastReviewedAt = System.currentTimeMillis()))
                                            analytics.recordReview()
                                            analytics.recordPractice(grade >= 3)
                                            if (index + 1 < cards.size) { index++; revealed = false } else reload()
                                        }
                                    }, modifier = Modifier.weight(1f)) { Text(label) }
                                }
                            }
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = { reload() }, modifier = Modifier.fillMaxWidth()) { Text("Frissítés") }
        Text("A rendszer a saját szótár szavait ütemezi. A kártyák és az ismétlési ütemezés helyben, a készüléken tárolódnak.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
