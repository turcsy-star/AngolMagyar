package hu.angolmagyar.fordito
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hu.angolmagyar.fordito.ui.design.PremiumCard
import hu.angolmagyar.fordito.ui.theme.AngolMagyarTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
class PrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Prefs(this)
        setContent {
            AngolMagyarTheme(isTrueBlack = prefs.oledBlack) {
                PrivacyScreen(prefs = prefs, onBack = { finish() })
            }
        }
    }
}
@Composable
private fun PrivacyScreen(prefs: Prefs, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var allowOnline by remember { mutableStateOf(prefs.allowOnlineAsr) }
    var confirmDelete by remember { mutableStateOf(false) }
    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val msg = withContext(Dispatchers.IO) {
                try {
                    val n = ctx.contentResolver.openOutputStream(uri)?.use { BackupHelper.export(ctx, it) } ?: 0
                    "Mentve ($n átirat + beállítások)."
                } catch (e: Exception) {
                    "Mentési hiba: ${e.message}"
                }
            }
            toast(msg)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val msg = withContext(Dispatchers.IO) {
                try {
                    val n = ctx.contentResolver.openInputStream(uri)?.use { BackupHelper.import(ctx, it) } ?: 0
                    "Visszaállítva ($n átirat). Indítsd újra az appot a beállításokhoz."
                } catch (e: Exception) {
                    "Visszaállítási hiba: ${e.message}"
                }
            }
            toast(msg)
        }
    }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("← Vissza") }
        Text("Adatvédelem és mentés", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() })
        PremiumCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Mi marad a telefonon?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() })
                Text("• A hang feldolgozása a telefonon történik (angol: Vosk; magyar: az Android beépített felismerője, offline csomaggal).\n" +
                    "• Az átiratok és a hangfelvételek csak a telefon belső tárában vannak.\n" +
                    "• A fordítás offline (ML Kit).\n" +
                    "• A kamerás szövegfelismerés (OCR) is a telefonon fut.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        PremiumCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Mi kerül ki a hálózatra?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() })
                Text("• Modellek letöltése (Vosk, ML Kit), ez csak letöltés, hang nem megy ki.\n" +
                    "• Csak ha te engedélyezed: online magyar felismerés, ilyenkor a hang a Google szervereire kerül.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Online magyar felismerés engedélyezése (tartalék)", Modifier.weight(1f))
                    Switch(checked = allowOnline, onCheckedChange = { allowOnline = it; prefs.allowOnlineAsr = it })
                }
            }
        }
        PremiumCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Mentés és visszaállítás", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() })
                Text("Egyetlen zip: beállítások + minden átirat. Telefoncseréhez vagy biztonsági másolatnak.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = {
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                    exportLauncher.launch("angolmagyar_mentes_$stamp.zip")
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Mentés fájlba…") }
                OutlinedButton(onClick = {
                    importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*"))
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Visszaállítás fájlból…") }
            }
        }
        PremiumCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Törlés", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() })
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Minden átirat törlése")
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Minden átirat törlése?") },
            text = { Text("Az átiratok és a kuka tartalma végleg törlődik. A beállítások megmaradnak.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    val n = BackupHelper.deleteAllTranscripts(ctx)
                    toast("$n fájl törölve.")
                }) { Text("Törlés") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Mégse") } }
        )
    }
}
