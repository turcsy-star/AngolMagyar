package hu.angolmagyar.fordito.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import hu.angolmagyar.fordito.DiagnosticsActivity
import hu.angolmagyar.fordito.FileSubtitleActivity
import hu.angolmagyar.fordito.ModelsActivity
import hu.angolmagyar.fordito.PhrasebookActivity
import hu.angolmagyar.fordito.Prefs
import hu.angolmagyar.fordito.PrivacyActivity
import hu.angolmagyar.fordito.TranscriptActivity
import hu.angolmagyar.fordito.LiveTranslateActivity
import hu.angolmagyar.fordito.SettingsActivity
import hu.angolmagyar.fordito.ui.design.LuxuryPalettes
import hu.angolmagyar.fordito.ui.design.PremiumCard
import hu.angolmagyar.fordito.ui.theme.AngolMagyarTheme

/** Feature hub: links to existing, real app screens and clearly labels capabilities still on the roadmap. */
class PlatinumIntelligenceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Prefs(this)
        setContent {
            AngolMagyarTheme(isTrueBlack = prefs.oledBlack) {
                IntelligenceHub(
                    onBack = { finish() },
                    onLive = { open(LiveTranslateActivity::class.java) },
                    onSubtitles = { open(FileSubtitleActivity::class.java) },
                    onPhrasebook = { open(PhrasebookActivity::class.java) },
                    onTranscripts = { open(TranscriptActivity::class.java) },
                    onModels = { open(ModelsActivity::class.java) },
                    onDiagnostics = { open(DiagnosticsActivity::class.java) },
                    onPrivacy = { open(PrivacyActivity::class.java) },
                    onSettings = { open(SettingsActivity::class.java) },
                    onNextWave = { open(NextWaveToolsActivity::class.java) },
                    onReview = { open(ReviewCoachActivity::class.java) },
                    onBackup = { open(BackupCenterActivity::class.java) },
                    onAnalytics = { open(LearningAnalyticsActivity::class.java) },
                    onDashboard = { open(PlatinumGlobalDashboardActivity::class.java) },
                    onUltimate = { open(UltimateEcosystemActivity::class.java) },
                    onOfflineIntelligence = { open(OfflineIntelligenceCenterActivity::class.java) },
                    onDocCamera = { open(DocumentCameraActivity::class.java) }
                )
            }
        }
    }

    private fun open(clazz: Class<*>) = startActivity(Intent(this, clazz))
}

@Composable
private fun IntelligenceHub(
    onBack: () -> Unit,
    onLive: () -> Unit,
    onSubtitles: () -> Unit,
    onPhrasebook: () -> Unit,
    onTranscripts: () -> Unit,
    onModels: () -> Unit,
    onDiagnostics: () -> Unit,
    onPrivacy: () -> Unit,
    onSettings: () -> Unit,
    onNextWave: () -> Unit,
    onReview: () -> Unit,
    onBackup: () -> Unit,
    onAnalytics: () -> Unit,
    onDashboard: () -> Unit,
    onUltimate: () -> Unit,
    onOfflineIntelligence: () -> Unit,
    onDocCamera: () -> Unit
) {
    val context = LocalContext.current
    val prefs = Prefs(context)
    val palette = LuxuryPalettes.byId(prefs.luxuryThemeId)
    Column(
        Modifier.fillMaxSize().background(if (prefs.oledBlack) Color.Black else MaterialTheme.colorScheme.background)
            .navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("← Vissza a főképernyőre") }
        PremiumCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("DIAMOND • INTELLIGENCE SUITE", color = palette.accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text("Intelligence Suite", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Egy központból érheted el a meglévő fordítóeszközöket, a diagnosztikát és a fejlesztési tervet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSettings) { Text("Megjelenés") }
                    OutlinedButton(onClick = onDiagnostics) { Text("Állapotellenőrzés") }
                }
            }
        }
        HubSection("AZONNAL HASZNÁLHATÓ", "A csomagban már meglévő képernyők") {
            HubAction("💎 Platinum Infinity Diamond 4.1", "Mind a tíz prémium fejlesztési pillér egyetlen helyi tanulási központban", "Új · 4.2.0", onUltimate)
            HubAction("🎙 Élő fordítás", "Kétirányú beszédmód és hangfeldolgozás", "Elérhető", onLive)
            HubAction("🎬 Felirat- és médiafeldolgozás", "Fájl kiválasztása és felirat-munkafolyamat", "Elérhető", onSubtitles)
            HubAction("📚 Saját szókincstár", "Mentett kifejezések + SpacedRepetition ismétlés (P4)", "Kész · P4", onPhrasebook)
            HubAction("🧠 Intelligens ismétlő", "Esedékes szókártyák és adaptív ismétlési ütemezés", "Új", onReview)
            HubAction("✨ Offline Intelligence Center", "Fordításminőség-ellenőrzés, helyi memória-keresés és ismétlési állapot", "Új · Phase7", onOfflineIntelligence)
            HubAction("💎 Learning Analytics Diamond", "Napi célok, 7 napos trendek és 30 napos tanulási mutatók", "Új · v2.63.0", onAnalytics)
            HubAction("📊 Platinum Global Dashboard", "Egységes állapotközpont, modulok és gyorsműveletek", "Új · v2.66.0", onDashboard)
            HubAction("💾 Infinity Backup Center", "Szótár, fordítási memória és ismétlési adatok exportja és visszaállítása", "Új", onBackup)
            HubAction("🗂 Átiratok", "Korábbi beszélgetések és átiratok", "Elérhető", onTranscripts)
            HubAction("📦 Modellek és offline mód", "Elérhető modellek kezelése", "Elérhető", onModels)
            HubAction("🛡 Adatvédelem", "Adatkezelési és helyi adatokkal kapcsolatos beállítások", "Elérhető", onPrivacy)
            HubAction("⚙ Diagnosztika", "Engedélyek, modellek és készülékállapot ellenőrzése", "Elérhető", onDiagnostics)
        }
        HubSection("KÖVETKEZŐ FEJLESZTÉSI HULLÁM", "Az alábbi ötletek nem mind kész funkciók; az állapotot külön jelezzük.") {
            HubAction("💎 Infinity Global UI", "Központi paletták, design tokenek, kontraszt-audit és migrációs checklist", "Részben kész · View→Compose migráció folyamatban", onNextWave, roadmap = true)
            HubAction("✨ Motion Engine", "Mozgásprofilok, skálázás, automatikus animációcsökkentés; a gombok animáció nélkül működnek", "Kész · v3.00", onNextWave, roadmap = true)
            HubAction("🧠 Smart Context + Translation Memory", "Kontextus, memória-javaslat, minőségflag-ek (P2/P3)", "Kész · P2-P3", onNextWave, roadmap = true)
            HubAction("📊 Translation Quality Intelligence", "Szám/tagadás/név jelzések + prémium kártya (P3)", "Kész · P3", onNextWave, roadmap = true)
            HubAction("🔀 Hybrid Translation Engine", "Offline/online motorválasztás, fallback, adatvédelmi kapcsolók", "Kész (core) · v2.65.0", onNextWave, roadmap = true)
            HubAction("🔁 Dual Translation", "Két változat szószintű összevetése, számellenőrzés, hangnem, mentés", "Kész (offline) · 4.2.9", onNextWave, roadmap = true)
            HubAction("📖 Dictionary Pro", "100+ szócikk szófajokkal, hamis barátokkal, példákkal; kétirányú keresés", "Kész (offline alapkészlet) · 4.2.9", onNextWave, roadmap = true)
            HubAction("🗣 Pronunciation Coach", "Lassú és szakaszos felolvasás, szavankénti ✓/✗ visszajelzés, tippek, trend", "Kész (szövegalapú) · 4.2.9", onNextWave, roadmap = true)
            HubAction("📄 Document / Camera (P8)", "Fotó vagy kép → eszközön futó OCR → szerkeszthető szöveg → fordítás", "Kész (kód) · kísérleti, eszközteszt hátra", onDocCamera, roadmap = true)
            HubAction("🔤 Subtitle + Meeting Workflow", "SRT, összefoglaló, minőségjelzés, tanulási jelöltek (P5-P7)", "Kész · P5-P7", onSubtitles, roadmap = true)
            HubAction("⚡ Performance & Regression Suite", "Latency, heap, beépített core regressziós készlet", "Kész (core) · v2.67.0", onNextWave, roadmap = true)
            HubAction("💎 Diamond Integration Gate", "Kiadási checklist, regresszió, modul-térkép (v3.00)", "Kész (core) · v3.00.0", onNextWave, roadmap = true)
            HubAction("🧪 Quality Testing", "Build-, eszközös és arany-referencia WER mérés", "Részben kész · Benchmark megvan", onDiagnostics, roadmap = true)
            HubAction("♿ Accessibility Suite (P10)", "Kontraszt-audit minden palettán, touch target, mozgásprofil, állapot-checklist", "Kész (kód + kontraszt) · TalkBack kézi teszt hátra", onNextWave, roadmap = true)
        }
        Text("Megjegyzés: a központ nem állítja késznek azokat a funkciókat, amelyekhez még fejlesztés, külső motor vagy eszközteszt szükséges.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun HubSection(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() })
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun HubAction(title: String, detail: String, status: String, onClick: () -> Unit, roadmap: Boolean = false, enabled: Boolean = true) {
    PremiumCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(status, style = MaterialTheme.typography.labelMedium, color = if (roadmap) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
                OutlinedButton(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(12.dp), modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "$title – ${if (enabled) "megnyitás" else "hamarosan"}" }) {
                    Text(if (enabled) "Megnyitás" else "Hamarosan")
                }
            }
        }
    }
}
