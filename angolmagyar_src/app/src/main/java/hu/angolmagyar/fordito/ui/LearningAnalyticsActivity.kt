package hu.angolmagyar.fordito.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hu.angolmagyar.fordito.Prefs
import hu.angolmagyar.fordito.intelligence.LearningAnalyticsStore
import hu.angolmagyar.fordito.intelligence.LearningDay
import hu.angolmagyar.fordito.ui.design.PremiumCard
import hu.angolmagyar.fordito.ui.theme.AngolMagyarTheme
import java.text.SimpleDateFormat
import java.util.Locale

class LearningAnalyticsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        val prefs = Prefs(this)
        setContent { AngolMagyarTheme(isTrueBlack = prefs.oledBlack) { LearningAnalyticsScreen(onBack = { finish() }) } }
    }
}

@Composable private fun LearningAnalyticsScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { LearningAnalyticsStore(context) }
    var days by remember { mutableStateOf(store.snapshot(7)) }
    var goal by remember { mutableIntStateOf(store.dailyGoal()) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh) { days = store.snapshot(7) }
    val totals = remember(days) { store.totals() }
    val today = days.lastOrNull() ?: LearningDay("", 0, 0, 0, 0)
    val progress = (today.activity.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedButton(onClick = onBack) { Text("← Vissza") }
        Text("PLATINUM • DIAMOND ANALYTICS", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text("Tanulási áttekintés", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Helyben tárolt aktivitási adatok. A rendszer nem ment el begépelt szavakat vagy mondatokat.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("30 napos gyakorlás", totals.practices.toString(), Modifier.weight(1f))
            MetricCard("Pontosság", "${totals.accuracyPercent}%", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("Aktív napok", "${totals.activeDays}/30", Modifier.weight(1f))
            MetricCard("Heti aktivitás", totals.weeklyActivity.toString(), Modifier.weight(1f))
        }
        PremiumCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("MAI CÉLKÖVETÉS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text("${today.activity} / $goal aktivitás", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(9.dp), strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { goal = (goal - 1).coerceAtLeast(1); store.setDailyGoal(goal) }) { Text("−") }
                    Text("Napi cél: $goal", modifier = Modifier.weight(1f).padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { goal = (goal + 1).coerceAtMost(500); store.setDailyGoal(goal) }) { Text("+") }
                }
                Text("Az aktivitás a keresések, gyakorlások és ismétlések összesített száma.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        PremiumCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("7 NAPOS TANULÁSI TREND", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                val maxValue = (days.maxOfOrNull { it.activity } ?: 0).coerceAtLeast(1)
                days.forEach { day ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(day.date.takeLast(5), style = MaterialTheme.typography.labelMedium)
                            Text("${day.activity}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(progress = { day.activity.toFloat() / maxValue }, modifier = Modifier.fillMaxWidth().height(7.dp), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
                Text("Keresés: ${days.sumOf { it.lookups }}  •  Gyakorlás: ${days.sumOf { it.practices }}  •  Ismétlés: ${days.sumOf { it.reviews }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedButton(onClick = { refresh++ }, modifier = Modifier.fillMaxWidth()) { Text("Adatok frissítése") }
        Text("Adatvédelem: az elemzés kizárólag összesített számlálókat tárol az alkalmazás helyi beállításaiban. Az adatok nem kerülnek hálózatra.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    PremiumCard(modifier) { Column(verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) } }
}
