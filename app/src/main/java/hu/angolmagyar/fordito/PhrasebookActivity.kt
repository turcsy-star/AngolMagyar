package hu.angolmagyar.fordito
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hu.angolmagyar.fordito.ui.design.PremiumCard
import hu.angolmagyar.fordito.ui.theme.AngolMagyarTheme
import java.text.DateFormat
import java.util.Date
class PhrasebookActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Prefs(this)
        setContent {
            AngolMagyarTheme(isTrueBlack = prefs.oledBlack) {
                PhrasebookScreen(onBack = { finish() })
            }
        }
    }
}
@Composable
private fun PhrasebookScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { PhrasebookStore(context) }
    var entries by remember { mutableStateOf(store.all()) }
    var query by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }
    var reviewMode by remember { mutableStateOf(false) }
    var dueQueue by remember { mutableStateOf(store.due()) }
    var reviewIndex by remember { mutableIntStateOf(0) }
    var revealed by remember { mutableStateOf(false) }
    val filtered = remember(entries, query) {
        val q = query.trim()
        if (q.isEmpty()) entries
        else entries.filter {
            it.source.contains(q, true) || it.translation.contains(q, true)
        }
    }
    fun reload() {
        entries = store.all()
        dueQueue = store.due()
    }
    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Saját szótár", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            TextButton(onClick = onBack) { Text("Vissza") }
        }
        if (reviewMode) {
            val current = dueQueue.getOrNull(reviewIndex)
            Text(
                "Ismétlés (${reviewIndex + 1} / ${dueQueue.size.coerceAtLeast(1)})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            if (current == null) {
                PremiumCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Nincs esedékes kártya.", fontWeight = FontWeight.SemiBold)
                        Text("Minden mentett kifejezés a következő ismétlésig vár.", style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { reviewMode = false; reload() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Vissza a listához")
                        }
                    }
                }
            } else {
                PremiumCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(current.source, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        if (revealed) {
                            Text(current.translation, style = MaterialTheme.typography.titleLarge)
                            Text("Mennyire ment könnyen?", fontWeight = FontWeight.SemiBold)
                            listOf(
                                listOf(0 to "Újra", 3 to "Nehéz"),
                                listOf(4 to "Jó", 5 to "Könnyű")
                            ).forEach { rowItems ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    rowItems.forEach { (grade, label) ->
                                        Button(
                                            onClick = {
                                                store.review(current.id, grade)
                                                revealed = false
                                                if (reviewIndex + 1 < dueQueue.size) {
                                                    reviewIndex++
                                                } else {
                                                    reload()
                                                    dueQueue = store.due()
                                                    reviewIndex = 0
                                                    if (dueQueue.isEmpty()) reviewMode = false
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) { Text(label) }
                                    }
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = { revealed = true },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Jelentés megmutatása") }
                        }
                    }
                }
                OutlinedButton(
                    onClick = { reviewMode = false; reload() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Ismétlés befejezése") }
            }
            return
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Keresés") },
            singleLine = true
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val dueCount = store.due().size
            Button(
                onClick = {
                    dueQueue = store.due()
                    reviewIndex = 0
                    revealed = false
                    reviewMode = true
                },
                enabled = dueCount > 0,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (dueCount > 0) "Ismétlés ($dueCount)" else "Nincs esedékes")
            }
            OutlinedButton(
                onClick = { showClearConfirm = true },
                enabled = entries.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) { Text("Összes törlése") }
        }
        Text(
            "${filtered.size} kifejezés · ismétlés: SpacedRepetition (helyi)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filtered, key = { it.id }) { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(entry.source, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(entry.translation, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(entry.savedAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (entry.nextReviewAt > 0L) {
                            Text(
                                "Következő ismétlés: ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(entry.nextReviewAt))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(
                                    android.content.ClipData.newPlainText(
                                        "Fordítás",
                                        "${entry.source}\n${entry.translation}"
                                    )
                                )
                            }) { Text("Másolás") }
                            TextButton(onClick = {
                                store.delete(entry.id)
                                reload()
                            }) { Text("Törlés") }
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
            confirmButton = {
                Button(onClick = {
                    store.clear()
                    entries = emptyList()
                    showClearConfirm = false
                }) { Text("Összes törlése") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearConfirm = false }) { Text("Mégse") }
            }
        )
    }
}
