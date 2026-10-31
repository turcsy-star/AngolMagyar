package hu.angolmagyar.fordito.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import hu.angolmagyar.fordito.Prefs
import hu.angolmagyar.fordito.core.db.AppDatabase
import hu.angolmagyar.fordito.intelligence.BackupArchive
import hu.angolmagyar.fordito.intelligence.BackupPreview
import hu.angolmagyar.fordito.intelligence.LearningBackup
import hu.angolmagyar.fordito.intelligence.SecureBackupArchive
import hu.angolmagyar.fordito.intelligence.AutomaticBackupScheduler
import hu.angolmagyar.fordito.intelligence.AutomaticBackupWorker
import hu.angolmagyar.fordito.ui.design.PremiumCard
import hu.angolmagyar.fordito.ui.theme.AngolMagyarTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupCenterActivity : ComponentActivity() {
    private val database by lazy { AppDatabase.get(this) }
    private var status by mutableStateOf("A Diamond mentés AES-256-GCM titkosítást használ. A jelszót csak te ismered.")
    private var busy by mutableStateOf(false)
    private var passwordDialog by mutableStateOf(false)
    private var passwordPurpose by mutableStateOf(PasswordPurpose.EXPORT)
    private var password by mutableStateOf("")
    private var pendingBackup by mutableStateOf<LearningBackup?>(null)
    private var preview by mutableStateOf<BackupPreview?>(null)
    private var dailyAuto by mutableStateOf(false)
    private var weeklyAuto by mutableStateOf(false)
    private var autoBackupInfo by mutableStateOf("Automatikus mentés nincs bekapcsolva.")

    private enum class PasswordPurpose { EXPORT, IMPORT }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Prefs(this)
        val autoPrefs = getSharedPreferences(AutomaticBackupWorker.PREFS, MODE_PRIVATE)
        dailyAuto = autoPrefs.getBoolean("daily_enabled", false)
        weeklyAuto = autoPrefs.getBoolean("weekly_enabled", false)
        val last = autoPrefs.getLong("last_success", 0L)
        autoBackupInfo = when { last > 0 -> "Utolsó sikeres automatikus mentés: ${SimpleDateFormat("yyyy.MM.dd. HH:mm", Locale.getDefault()).format(Date(last))}"; autoPrefs.getString("last_error", "").orEmpty().isNotBlank() -> "Utolsó hiba: ${autoPrefs.getString("last_error", "")}"; else -> "Automatikus mentés még nem futott le." }
        setContent {
            AngolMagyarTheme(isTrueBlack = prefs.oledBlack) {
                BackupCenterScreen(status, busy, dailyAuto, weeklyAuto, autoBackupInfo, { finish() }, { requestPassword(PasswordPurpose.EXPORT) }, { openBackup() }, { d, w -> dailyAuto = d; weeklyAuto = w; AutomaticBackupScheduler.configure(this, d, w); autoBackupInfo = "Ütemezés mentve. A rendszer a következő megfelelő időablakban futtatja a mentést." })
                if (passwordDialog) {
                    AlertDialog(
                        onDismissRequest = { passwordDialog = false; password = "" },
                        title = { Text(if (passwordPurpose == PasswordPurpose.EXPORT) "Mentés titkosítása" else "Mentés feloldása") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(if (passwordPurpose == PasswordPurpose.EXPORT) "Adj meg legalább 8 karakteres jelszót. Elvesztése esetén a mentés nem állítható vissza." else "Add meg a mentés létrehozásakor használt jelszót.")
                                OutlinedTextField(password, { password = it }, label = { Text("Mentési jelszó") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            }
                        },
                        confirmButton = { Button(onClick = { submitPassword() }, enabled = password.length >= (if (passwordPurpose == PasswordPurpose.EXPORT) 8 else 1)) { Text("Folytatás") } },
                        dismissButton = { TextButton(onClick = { passwordDialog = false; password = "" }) { Text("Mégse") } }
                    )
                }
                preview?.let { p ->
                    AlertDialog(
                        onDismissRequest = { preview = null; pendingBackup = null },
                        title = { Text("Visszaállítás előnézete") },
                        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Mentés dátuma: ${if (p.createdAt > 0) SimpleDateFormat("yyyy.MM.dd. HH:mm", Locale.getDefault()).format(Date(p.createdAt)) else "ismeretlen"}")
                            Text("Szótárbejegyzések: ${p.dictionaryCount}")
                            Text("Fordítási memória: ${p.translationCount}")
                            Text("Ismétlési adatok: ${p.reviewCount}")
                            Text("A folytatás lecseréli a jelenlegi tanulási adatokat.", color = MaterialTheme.colorScheme.error)
                        } },
                        confirmButton = { Button(onClick = { restorePreviewedBackup() }, enabled = !busy) { Text("Visszaállítás") } },
                        dismissButton = { TextButton(onClick = { preview = null; pendingBackup = null }) { Text("Mégse") } }
                    )
                }
            }
        }
    }

    private val createDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) writeBackup(uri)
    }
    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) readBackup(uri)
    }

    private fun requestPassword(purpose: PasswordPurpose) {
        passwordPurpose = purpose
        password = ""
        passwordDialog = true
    }

    private fun submitPassword() {
        val secret = password
        password = ""
        passwordDialog = false
        if (passwordPurpose == PasswordPurpose.EXPORT) {
            exportPassword = secret
            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.ROOT).format(Date())
            createDocument.launch("AngolMagyar_Infinity_Diamond_$stamp.pibackup")
        } else unlockPendingBackup(secret)
    }

    private var exportPassword: String? = null

    private fun openBackup() { openDocument.launch(arrayOf("application/octet-stream", "application/json", "*/*")) }

    private fun writeBackup(uri: Uri) {
        val secret = exportPassword ?: return
        exportPassword = null
        lifecycleScope.launch {
            busy = true
            status = "Titkosított mentés készítése…"
            runCatching {
                val encrypted = withContext(Dispatchers.IO) {
                    val json = BackupArchive.encode(LearningBackup(System.currentTimeMillis(), database.dictionaryDao().all(), database.translationDao().all(), database.reviewScheduleDao().all()))
                    SecureBackupArchive.encrypt(json, secret.toCharArray())
                }
                withContext(Dispatchers.IO) { contentResolver.openOutputStream(uri)?.use { it.write(encrypted) } ?: error("A kiválasztott fájl nem írható.") }
                status = "Sikeres, AES-256-GCM titkosított mentés. Méret: ${encrypted.size / 1024} KB."
            }.onFailure { status = "Mentési hiba: ${it.message ?: "ismeretlen hiba"}" }
            busy = false
        }
    }

    private fun readBackup(uri: Uri) {
        lifecycleScope.launch {
            busy = true
            status = "Titkosított mentés beolvasása…"
            runCatching {
                val encrypted = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { input ->
                        val out = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8 * 1024)
                        var total = 0
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= 24 * 1024 * 1024) { "A mentésfájl túl nagy." }
                            out.write(buffer, 0, count)
                        }
                        out.toByteArray()
                    } ?: error("A kiválasztott fájl nem olvasható.")
                }
                pendingEncrypted = encrypted
                requestPassword(PasswordPurpose.IMPORT)
                status = "Add meg a jelszót a mentés ellenőrzéséhez."
            }.onFailure { status = "A mentés nem olvasható: ${it.message ?: "ismeretlen hiba"}" }
            busy = false
        }
    }

    private var pendingEncrypted: ByteArray? = null

    private fun unlockPendingBackup(secret: String) {
        val encrypted = pendingEncrypted ?: return
        lifecycleScope.launch {
            busy = true
            runCatching {
                val backup = withContext(Dispatchers.IO) {
                    val json = SecureBackupArchive.decrypt(encrypted, secret.toCharArray())
                    BackupArchive.decode(json)
                }
                pendingBackup = backup
                preview = BackupPreview.from(backup)
                pendingEncrypted = null
                status = "A mentés hitelesítése sikeres. Ellenőrizd az előnézetet a visszaállítás előtt."
            }.onFailure { status = "Feloldási hiba: ${it.message ?: "hibás jelszó vagy sérült fájl"}" }
            busy = false
        }
    }

    private fun restorePreviewedBackup() {
        val backup = pendingBackup ?: return
        lifecycleScope.launch {
            busy = true
            preview = null
            status = "Visszaállítás folyamatban…"
            runCatching {
                withContext(Dispatchers.IO) {
                    database.withTransaction {
                        database.dictionaryDao().clear()
                        database.translationDao().clear()
                        database.reviewScheduleDao().clear()
                        database.dictionaryDao().upsertAll(backup.dictionary)
                        backup.translations.forEach { database.translationDao().insert(it) }
                        database.reviewScheduleDao().upsertAll(backup.reviews)
                    }
                }
                status = "Visszaállítás kész: ${backup.dictionary.size} szótár, ${backup.translations.size} fordítás, ${backup.reviews.size} ismétlési adat."
                pendingBackup = null
            }.onFailure { status = "A visszaállítás nem történt meg: ${it.message ?: "ismeretlen hiba"}" }
            busy = false
        }
    }

    @Composable
    private fun BackupCenterScreen(status: String, busy: Boolean, daily: Boolean, weekly: Boolean, autoInfo: String, onBack: () -> Unit, onExport: () -> Unit, onImport: () -> Unit, onSchedule: (Boolean, Boolean) -> Unit) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedButton(onClick = onBack, enabled = !busy) { Text("← Vissza") }
            Text("PLATINUM • INFINITY • DIAMOND", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("Infinity Secure Backup", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            PremiumCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Titkosított biztonsági mentés", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("AES-256-GCM hitelesített titkosítás és PBKDF2-HMAC-SHA256 kulcsszármaztatás. A jelszó nem kerül mentésre.")
                    Button(onClick = onExport, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Titkosított mentés készítése") }
                    OutlinedButton(onClick = onImport, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Mentés ellenőrzése és visszaállítása") }
                }
            }
            PremiumCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Automatikus Diamond mentés", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("A napi és heti mentések helyben, AES-256-GCM titkosítással készülnek. A kulcsot az Android Keystore védi; internetkapcsolat nem szükséges. Az automatikus mentések az alkalmazás privát tárhelyén maradnak.")
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Napi mentés", modifier = Modifier.weight(1f)); Switch(checked = daily, onCheckedChange = { onSchedule(it, weekly) })
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Heti mentés", modifier = Modifier.weight(1f)); Switch(checked = weekly, onCheckedChange = { onSchedule(daily, it) })
                    }
                    Text(autoInfo, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
            PremiumCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Művelet állapota", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (busy) Text("Folyamatban…", color = MaterialTheme.colorScheme.primary)
                    Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("A visszaállítás előtt mindig megjelenik az adat-előnézet. Csak hitelesített és sikeresen dekódolt mentés állítható vissza. A jelszót biztonságos helyen őrizd meg.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
