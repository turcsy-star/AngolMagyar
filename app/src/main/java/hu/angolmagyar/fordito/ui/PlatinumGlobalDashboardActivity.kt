package hu.angolmagyar.fordito.ui
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import hu.angolmagyar.fordito.DiagnosticsActivity
import hu.angolmagyar.fordito.ModelsActivity
import hu.angolmagyar.fordito.Prefs
import hu.angolmagyar.fordito.PrivacyActivity
import hu.angolmagyar.fordito.SettingsActivity
import hu.angolmagyar.fordito.SystemStatusSnapshot
import hu.angolmagyar.fordito.SystemStatusSnapshot.Level
import hu.angolmagyar.fordito.SystemStatusSnapshot.ModuleStatus
import hu.angolmagyar.fordito.ui.design.InfinityGlobalUi
import hu.angolmagyar.fordito.ui.design.PremiumCard
import hu.angolmagyar.fordito.ui.design.PremiumMotion
import hu.angolmagyar.fordito.ui.theme.AngolMagyarTheme
class PlatinumGlobalDashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Prefs(this)
        setContent {
            AngolMagyarTheme(isTrueBlack = prefs.oledBlack) {
                GlobalDashboardScreen(
                    onBack = { finish() },
                    onDiagnostics = { startActivity(Intent(this, DiagnosticsActivity::class.java)) },
                    onModels = { startActivity(Intent(this, ModelsActivity::class.java)) },
                    onBackup = { startActivity(Intent(this, BackupCenterActivity::class.java)) },
                    onNextWave = { startActivity(Intent(this, NextWaveToolsActivity::class.java)) },
                    onPrivacy = { startActivity(Intent(this, PrivacyActivity::class.java)) },
                    onSettings = { startActivity(Intent(this, SettingsActivity::class.java)) }
                )
            }
        }
    }
}
@Composable
private fun GlobalDashboardScreen(
    onBack: () -> Unit,
    onDiagnostics: () -> Unit,
    onModels: () -> Unit,
    onBackup: () -> Unit,
    onNextWave: () -> Unit,
    onPrivacy: () -> Unit,
    onSettings: () -> Unit
) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val versionName = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "2.66.0"
    } catch (_: Exception) { "2.66.0" }
    val versionCode = try {
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionCode
        }
    } catch (_: Exception) { 6600 }
    val micOk = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    val modules = remember(micOk, prefs.reduceMotion) {
        SystemStatusSnapshot.templateModules().map { m ->
            when (m.id) {
                "asr" -> m.copy(
                    level = if (micOk) Level.OK else Level.WARN,
                    detail = if (micOk) "Mikrofon engedély megadva" else "Mikrofon engedély hiányzik",
                    actionable = !micOk
                )
                "motion" -> m.copy(
                    level = Level.OK,
                    detail = PremiumMotion.statusLabel(ctx)
                )
                "global_ui" -> m.copy(
                    level = Level.WARN,
                    detail = "Migráció: ${InfinityGlobalUi.progressLabel()}"
                )
                "translate_online" -> m.copy(
                    level = Level.UNKNOWN,
                    detail = "API kulcs és hálózat a Beállítások / Diagnosztika alatt"
                )
                else -> m
            }
        }
    }
    val snapshot = remember(modules, versionName, versionCode) {
        SystemStatusSnapshot.build(
            versionName = versionName,
            versionCode = versionCode,
            modules = modules,
            notes = listOf(
                "A jelzések helyben, a készüléken készülnek; nem küldenek telemetriát.",
                "A Hybrid / Smart Context / Quality modulok a core-ban készen állnak."
            )
        )
    }
    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(onClick = onBack) { Text("← Vissza") }
        Text("DIAMOND • GLOBAL DASHBOARD", color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text("Egységes állapotközpont", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("v$versionName ($versionCode) · Összkép: ${levelLabel(snapshot.overall)}")
        PremiumCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Összefoglaló", fontWeight = FontWeight.SemiBold)
                Text("Rendben: ${snapshot.okCount} · Figyelmeztetés: ${snapshot.warnCount} · Hiba: ${snapshot.errorCount}")
                snapshot.notes.forEach {
                    Text("• $it", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text("Modulok", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        modules.forEach { mod -> ModuleRow(mod) }
        Text("Gyorsműveletek", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        PremiumCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onDiagnostics, modifier = Modifier.weight(1f)) { Text("Diagnosztika") }
                    Button(onClick = onModels, modifier = Modifier.weight(1f)) { Text("Modellek") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onBackup, modifier = Modifier.weight(1f)) { Text("Mentés") }
                    OutlinedButton(onClick = onNextWave, modifier = Modifier.weight(1f)) { Text("Next Wave") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPrivacy, modifier = Modifier.weight(1f)) { Text("Adatvédelem") }
                    OutlinedButton(onClick = onSettings, modifier = Modifier.weight(1f)) { Text("Beállítások") }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
@Composable
private fun ModuleRow(mod: ModuleStatus) {
    PremiumCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(mod.title, fontWeight = FontWeight.SemiBold)
                Text(levelLabel(mod.level), color = levelColor(mod.level),
                    style = MaterialTheme.typography.labelMedium)
            }
            Text(mod.detail, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
private fun levelLabel(level: Level): String = when (level) {
    Level.OK -> "Rendben"
    Level.WARN -> "Figyelmeztetés"
    Level.ERROR -> "Hiba"
    Level.UNKNOWN -> "Ismeretlen"
    Level.DISABLED -> "Kikapcsolva"
}
@Composable
private fun levelColor(level: Level) = when (level) {
    Level.OK -> MaterialTheme.colorScheme.primary
    Level.WARN -> MaterialTheme.colorScheme.secondary
    Level.ERROR -> MaterialTheme.colorScheme.error
    Level.DISABLED -> MaterialTheme.colorScheme.onSurfaceVariant
    Level.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
}
