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
                    onSettings = { open(SettingsActivity::class.java) }
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
    onSettings: () -> Unit
) {
    val context = LocalContext.current
    val prefs = Prefs(context)
    val palette = LuxuryPalettes.byId(prefs.luxuryThemeId)
    Column(
        Modifier.fillMaxSize().background(if (prefs.oledBlack) Color.Black else MaterialTheme.colorScheme.background)
            .navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OutlinedButton(onClick = onBack) { Text("← Vissza a főképernyőre") }
        PremiumCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("PLATINUM • INFINITY", color = palette.accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text("Intelligence Suite", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Egy központból érheted el a meglévő fordítóeszközöket, a diagnosztikát és a fejlesztési tervet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSettings) { Text("Megjelenés") }
                    OutlinedButton(onClick = onDiagnostics) { Text("Állapotellenőrzés") }
                }
            }
        }
        HubSection("AZONNAL HASZNÁLHATÓ", "A csomagban már meglévő képernyők") {
            HubAction("🎙 Élő fordítás", "Kétirányú beszédmód és hangfeldolgozás", "Elérhető", onLive)
            HubAction("🎬 Felirat- és médiafeldolgozás", "Fájl kiválasztása és felirat-munkafolyamat", "Elérhető", onSubtitles)
            HubAction("📚 Saját szókincstár", "Mentett kifejezések és tanulási munkafolyamat", "Elérhető", onPhrasebook)
            HubAction("🗂 Átiratok", "Korábbi beszélgetések és átiratok", "Elérhető", onTranscripts)
            HubAction("📦 Modellek és offline mód", "Elérhető modellek kezelése", "Elérhető", onModels)
            HubAction("🛡 Adatvédelem", "Adatkezelési és helyi adatokkal kapcsolatos beállítások", "Elérhető", onPrivacy)
            HubAction("⚙ Diagnosztika", "Engedélyek, modellek és készülékállapot ellenőrzése", "Elérhető", onDiagnostics)
        }
        HubSection("KÖVETKEZŐ FEJLESZTÉSI HULLÁM", "Az alábbi ötletek nem mind kész funkciók; az állapotot külön jelezzük.") {
            HubAction("💎 Infinity Global UI", "A klasszikus View-képernyők teljes paletta-migrációja", "Részben kész", onSettings, roadmap = true)
            HubAction("✨ Platinum Motion Engine", "Egységes mozgásrendszer és automatikus animációcsökkentés", "Részben kész", onSettings, roadmap = true)
            HubAction("🧠 Smart Context Translation", "Kontextus, kétértelműség és hangnem szerinti alternatívák", "Fejlesztendő", onSettings, roadmap = true)
            HubAction("📊 Translation Quality", "Ellenőrizhető nyelvtani és terminológiai figyelmeztetések", "Fejlesztendő", onDiagnostics, roadmap = true)
            HubAction("🔁 Dual Translation", "Két fordítási változat összehasonlítása", "Fejlesztendő", onSettings, roadmap = true)
            HubAction("📖 Dictionary Pro", "Bővített szótári jelentések, szófajok és példák", "Bővítendő", onPhrasebook, roadmap = true)
            HubAction("🗣 Pronunciation Coach", "Kiejtésgyakorlás és megbízható beszédértékelés", "Fejlesztendő", onLive, roadmap = true)
            HubAction("📄 Document Translator", "PDF/DOCX import, OCR és formázásmegőrzés", "Fejlesztendő", onSubtitles, roadmap = true)
            HubAction("🔤 Subtitle Translator Pro", "SRT-alapú fordítás és export", "Alapfunkció elérhető", onSubtitles, roadmap = true)
            HubAction("⚡ Performance & Cache", "Mért teljesítmény, gyorsítótár-szabályok és erőforrásadatok", "Részben kész", onDiagnostics, roadmap = true)
            HubAction("🧪 Quality Testing", "Build-, regressziós és fizikai eszközös tesztelés", "Kiadás előtt szükséges", onDiagnostics, roadmap = true)
            HubAction("♿ Accessibility Suite", "Kontraszt, nagy betűméret és képernyőolvasó-ellenőrzés", "Folyamatos fejlesztés", onSettings, roadmap = true)
        }
        Text("Megjegyzés: a központ nem állítja késznek azokat a funkciókat, amelyekhez még fejlesztés, külső motor vagy eszközteszt szükséges.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun HubSection(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun HubAction(title: String, detail: String, status: String, onClick: () -> Unit, roadmap: Boolean = false) {
    PremiumCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(status, style = MaterialTheme.typography.labelMedium, color = if (roadmap) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
                OutlinedButton(onClick = onClick, shape = RoundedCornerShape(12.dp)) {
                    Text(if (roadmap) "Kapcsolódó eszköz" else "Megnyitás")
                }
            }
        }
    }
}
