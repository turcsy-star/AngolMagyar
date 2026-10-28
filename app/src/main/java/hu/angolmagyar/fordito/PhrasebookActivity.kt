package hu.angolmagyar.fordito

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hu.angolmagyar.fordito.ui.design.PremiumCard
import hu.angolmagyar.fordito.ui.design.PremiumSectionTitle
import hu.angolmagyar.fordito.ui.theme.AngolMagyarTheme
import java.text.DateFormat
import java.util.Date

/** Local-only phrasebook. Entries are saved only after an explicit user action on the translation card. */
class PhrasebookActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AngolMagyarTheme { PhrasebookScreen(onBack = { finish() }) } }
    }
}

@Composable
private fun PhrasebookScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { PhrasebookStore(context) }
    var entries by remember { mutableStateOf(store.all()) }
    var query by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }
    var practiceMode by remember { mutableStateOf(false) }
    var revealedIds by remember { mutableStateOf(setOf<String>()) }
    var practiceIndex by remember { mutableStateOf(0) }
    val filtered = entries.filter {
        query.isBlank() || it.source.contains(query, true) || it.translation.contains(query, true)
    }
    val dueEntries = filtered.filter { it.nextReviewAt == 0L || it.nextReviewAt <= System.currentTimeMillis() }
    val currentPractice = dueEntries.getOrNull(practiceIndex.coerceAtMost((dueEntries.size - 1).coerceAtLeast(0)))

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Saját szókincstár", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row {
                TextButton(onClick = { practiceMode = !practiceMode; practiceIndex = 0; revealedIds = emptySet() }) { Text(if (practiceMode) "Szókincstár" else "Gyakorlás") }
                TextButton(onClick = onBack) { Text("Vissza") }
            }
        }
        Text("A mentett fordítások kizárólag ezen az eszközön tárolódnak.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!practiceMode) OutlinedTextField(
            value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Keresés a mentett kifejezések között") }, singleLine = true
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (practiceMode) "${dueEntries.size} ismétlésre vár" else "${filtered.size} mentett kifejezés", style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = { if (entries.isNotEmpty()) showClearConfirm = true }, enabled = entries.isNotEmpty()) {
                Text("Összes törlése")
            }
        }
        if (practiceMode) {
            if (currentPractice == null) {
                PremiumCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PremiumSectionTitle("Minden ismétlés kész!", "Most nincs esedékes kifejezés.")
                        Text("A sikeres válaszok egyre hosszabb időköz után térnek vissza. A gyakorlás teljesen helyi.",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("KIFEJEZÉS ${practiceIndex + 1} / ${dueEntries.size}", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(currentPractice.source, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        if (currentPractice.id in revealedIds) {
                            Divider()
                            Text(currentPractice.translation, style = MaterialTheme.typography.titleLarge)
                        }
                        Button(onClick = { revealedIds = revealedIds + currentPractice.id }, enabled = currentPractice.id !in revealedIds,
                            modifier = Modifier.fillMaxWidth()) { Text(if (currentPractice.id in revealedIds) "Fordítás megmutatva" else "Fordítás megmutatása") }
                        if (currentPractice.id in revealedIds) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                                    store.review(currentPractice.id, false); entries = store.all(); revealedIds = emptySet()
                                    practiceIndex = 0
                                }) { Text("Nem tudtam") }
                                Button(modifier = Modifier.weight(1f), onClick = {
                                    store.review(currentPractice.id, true); entries = store.all(); revealedIds = emptySet()
                                    practiceIndex = 0
                                }) { Text("Tudtam") }
                            }
                        }
                        Text("Ismétlések: ${currentPractice.reviewCount} · előző intervallum: ${currentPractice.intervalDays} nap",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else if (filtered.isEmpty()) {
            PremiumCard(Modifier.fillMaxWidth()) {
                Column {
                    PremiumSectionTitle("Még nincs mentett kifejezés", "Élő fordításnál a Mentés gombbal adhatsz hozzá mondatokat.")
                    Spacer(Modifier.height(6.dp))
                    Text("A szókincstár kézzel mentett elemeket tárol; a beszélgetéseket nem menti automatikusan.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filtered, key = { it.id }) { entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(entry.source, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(entry.translation, style = MaterialTheme.typography.bodyLarge)
                            Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(entry.savedAt)),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (entry.nextReviewAt > 0L) Text("Következő ismétlés: ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(entry.nextReviewAt))}",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Fordítás", "${entry.source}\n${entry.translation}"))
                                }) { Text("Másolás") }
                                TextButton(onClick = { store.delete(entry.id); entries = store.all() }) { Text("Törlés") }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Törlöd az összes mentett kifejezést?") },
            text = { Text("Ez a művelet nem vonható vissza.") },
            confirmButton = { Button(onClick = { store.clear(); entries = emptyList(); showClearConfirm = false }) { Text("Összes törlése") } },
            dismissButton = { OutlinedButton(onClick = { showClearConfirm = false }) { Text("Mégse") } }
        )
    }
}
