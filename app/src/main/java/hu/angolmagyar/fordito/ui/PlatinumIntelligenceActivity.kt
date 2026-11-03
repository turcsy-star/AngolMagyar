package hu.angolmagyar.fordito.ui
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hu.angolmagyar.fordito.DiagnosticsActivity
import hu.angolmagyar.fordito.Engines
import hu.angolmagyar.fordito.FileSubtitleActivity
import hu.angolmagyar.fordito.OnlineDictionary
import hu.angolmagyar.fordito.OnlinePrefs
import hu.angolmagyar.fordito.OnlineSettingsDialog
import hu.angolmagyar.fordito.OnlineTranslator
import hu.angolmagyar.fordito.Prefs
import hu.angolmagyar.fordito.TranscriptActivity
import hu.angolmagyar.fordito.VocabLearner
import hu.angolmagyar.fordito.ui.design.PremiumCard
import hu.angolmagyar.fordito.ui.theme.AngolMagyarTheme
/**
 * „Minőség" képernyő: csak az átirat és a fordítás pontosságát javító eszközök.
 * (A korábbi Platinum/Diamond/Infinity csempék, ütemtervek és mutatók kikerültek.)
 */
class PlatinumIntelligenceActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Prefs(this)
        setContent {
            AngolMagyarTheme(isTrueBlack = prefs.oledBlack) {
                QualityHub(
                    oled = prefs.oledBlack,
                    onBack = { finish() },
                    onOpen = { clazz -> startActivity(Intent(this, clazz)) },
                    onOnlineSettings = { after -> OnlineSettingsDialog.show(this) { after() } }
                )
            }
        }
    }
}
private fun connectionReport(ctx: Context): String {
    val sb = StringBuilder()
    val net = Engines.online(ctx)
    if (!net) {
        return "✗ Internet: nincs hálózat.\nKapcsolódj Wi-Fi-hez vagy mobilnethez, majd próbáld újra."
    }
    sb.append("✓ Internet: van hálózat.")
    val cfg = OnlinePrefs.config(ctx, "angol", "magyar")
    if (cfg == null) {
        val miss = OnlinePrefs.missing(ctx)
        sb.append("\n✗ Online fordítás: nincs beállítva")
        if (miss.isNotEmpty()) sb.append(" (hiányzik: ").append(miss.joinToString(", ")).append(")")
        sb.append(".")
    } else {
        val err = try { OnlineTranslator(cfg).test() } catch (e: Throwable) { OnlineTranslator.netErrorText(e) }
        sb.append(if (err == null) "\n✓ Online fordítás: működik." else "\n✗ Online fordítás: $err")
    }
    if (OnlinePrefs.dictEnabled(ctx)) {
        val d = OnlineDictionary.selfTest()
        sb.append(if (d == null) "\n✓ Online szótár: működik." else "\n✗ Online szótár: $d")
    } else {
        sb.append("\nOnline szótár: ki van kapcsolva.")
    }
    return sb.toString()
}
@Composable
private fun QualityHub(
    oled: Boolean,
    onBack: () -> Unit,
    onOpen: (Class<*>) -> Unit,
    onOnlineSettings: (() -> Unit) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var autoOn by remember { mutableStateOf(VocabLearner.auto(prefs)) }
    var rules by remember { mutableStateOf(VocabLearner.autoRules(prefs).lines().count { it.isNotBlank() }) }
    var recent by remember { mutableStateOf(VocabLearner.autoLog(prefs)) }
    var dictOn by remember { mutableStateOf(OnlinePrefs.dictEnabled(context)) }
    var status by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize()
            .background(if (oled) Color.Black else MaterialTheme.colorScheme.background)
            .statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("← Vissza") }
        Text("Minőség", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Az átirat és a fordítás pontosságát javító eszközök.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PremiumCard {
            Text("1. Szövegjavítás és újraírás", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "A hibásan felismert szavakat az online szótár javítja, utána a javított sorok újraíródnak és újrafordulnak. " +
                    "Feldolgozás közben magától lefut; kész feliratnál a szerkesztőben a „Szövegjavítás online szótárral + újraírás” gombbal indíthatod."
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Online szótár használata", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Switch(checked = dictOn, onCheckedChange = { dictOn = it; OnlinePrefs.setDictEnabled(context, it) })
            }
            Text(
                "Csak az ellenőrzendő, különálló szavak mennek ki, mondat nem.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = { onOpen(FileSubtitleActivity::class.java) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Feliratok megnyitása")
            }
        }
        PremiumCard {
            Text("2. Automatikus szótár", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "A szótár magától gyűjti a javításaidat: az elírás-szerű cserét másodszor, a többit harmadszor ugyanúgy javítva szabállyá teszi. " +
                    "A feldolgozás a ritkán előforduló, hasonló neveket is összevonja."
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Magától tanul", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Switch(checked = autoOn, onCheckedChange = { autoOn = it; VocabLearner.setAuto(prefs, it) })
            }
            Text("Tanult szabályok: $rules", fontWeight = FontWeight.SemiBold)
            if (recent.isEmpty()) {
                Text(
                    "Még nincs automatikusan tanult szabály.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                recent.takeLast(6).reversed().forEach {
                    Text("• $it", style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (rules > 0) {
                OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Tanult szabályok törlése")
                }
            }
        }
        PremiumCard {
            Text("3. Online kapcsolat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Ellenőrzi az internetet, az online fordítást és az online szótárat, és megmondja, mi hiányzik vagy mi nem működik."
            )
            if (status.isNotBlank()) Text(status, fontWeight = FontWeight.Medium)
            Button(
                onClick = {
                    if (!testing) {
                        testing = true
                        status = "Ellenőrzés…"
                        Thread {
                            val msg = try { connectionReport(context) } catch (e: Throwable) { "✗ Hiba: ${e.message ?: e.javaClass.simpleName}" }
                            Handler(Looper.getMainLooper()).post { status = msg; testing = false }
                        }.start()
                    }
                },
                enabled = !testing,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text("Kapcsolat ellenőrzése") }
            OutlinedButton(
                onClick = { onOnlineSettings { dictOn = OnlinePrefs.dictEnabled(context) } },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text("Online beállítások") }
        }
        PremiumCard {
            Text("4. Fordításminőség-ellenőrző", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Egy angol mondat és a fordítása összevetése: számok, tagadás, nevek és szakszavak eltérésének jelzése.")
            OutlinedButton(
                onClick = { onOpen(OfflineIntelligenceCenterActivity::class.java) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text("Megnyitás") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onOpen(TranscriptActivity::class.java) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Átiratok") }
            OutlinedButton(onClick = { onOpen(DiagnosticsActivity::class.java) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Diagnosztika") }
            OutlinedButton(onClick = { onOpen(BackupCenterActivity::class.java) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Mentés") }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Tanult szabályok törlése") },
            text = { Text("Az automatikusan tanult szabályok mind törlődnek. A saját, kézzel írt szótárad nem változik.") },
            confirmButton = {
                TextButton(onClick = {
                    VocabLearner.setAutoRules(prefs, "")
                    prefs.raw().edit().remove("vocabAutoLog").apply()
                    rules = 0
                    recent = emptyList()
                    confirmClear = false
                }) { Text("Törlés") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Mégse") } }
        )
    }
}
