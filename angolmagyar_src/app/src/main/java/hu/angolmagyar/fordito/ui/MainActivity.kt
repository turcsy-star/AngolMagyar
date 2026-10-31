package hu.angolmagyar.fordito.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import hu.angolmagyar.fordito.Accent
import hu.angolmagyar.fordito.App
import hu.angolmagyar.fordito.AppState
import hu.angolmagyar.fordito.DiagnosticsActivity
import hu.angolmagyar.fordito.ui.PlatinumIntelligenceActivity
import hu.angolmagyar.fordito.FileSubtitleActivity
import hu.angolmagyar.fordito.ModelDownloadService
import hu.angolmagyar.fordito.ModelManager
import hu.angolmagyar.fordito.OnlinePrefs
import hu.angolmagyar.fordito.OnlineSettingsDialog
import hu.angolmagyar.fordito.ModelsActivity
import hu.angolmagyar.fordito.Prefs
import hu.angolmagyar.fordito.Presets
import hu.angolmagyar.fordito.AsrPrefs
import hu.angolmagyar.fordito.PhrasebookActivity
import hu.angolmagyar.fordito.PhrasebookStore
import hu.angolmagyar.fordito.TranslationStyle
import hu.angolmagyar.fordito.SettingsActivity
import hu.angolmagyar.fordito.SetupWizardActivity
import hu.angolmagyar.fordito.SrtJob
import hu.angolmagyar.fordito.TranscriptActivity
import hu.angolmagyar.fordito.TranslateService
import hu.angolmagyar.fordito.ui.theme.AngolMagyarTheme
import hu.angolmagyar.fordito.ui.design.ProvidePremiumDesignSystem
import hu.angolmagyar.fordito.ui.design.StatusPill
import hu.angolmagyar.fordito.ui.design.PremiumCard
import hu.angolmagyar.fordito.ui.design.PremiumSectionTitle
import hu.angolmagyar.fordito.ui.design.PremiumTranslationCard
import hu.angolmagyar.fordito.ui.design.DiamondBrandMark
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin
import hu.angolmagyar.fordito.LiveTranslateActivity as LiveActivity

/**
 * v2.22.3 – Material 3 kezdőlap: egy érintéssel felirat-készítés, élő fordítás, átiratok.
 * Animált háttér (aurora), lépcsőzetes belépés, nyomásra rugózó kártyák, élő hanghullám.
 *
 * A részletes élő-fordítás képernyő a [LiveActivity] (a manifestben regisztrálva); a Wear, a gyorscsempe, a
 * parancsikonok és a feladat-beállító ennek adják át az `auto_start` / `profile` extrát.
 */
class MainActivity : androidx.appcompat.app.AppCompatActivity() {

    private lateinit var prefs: Prefs
    private var appliedThemeSignature: String? = null

    /**
     * Első indításkor magától letölti az alap modelleket (Whisper large-v3 + offline fordító), ha még nincs
     * Whisper-modell és Wi-Fin (nem mobilneten) vagyunk. Egyszer próbálja; a Modellek képernyőn bármikor kézzel is lehet.
     */
    private fun autoDownloadModels() {
        try {
            // egyszeri áttérés: a pontos profil és a large-v3 az alapértelmezett
            if (!prefs.raw().getBoolean("preciseDefault", false)) {
                prefs.raw().edit().putBoolean("preciseDefault", true).apply()
                Presets.apply(this, "precise")
            }
            if (ModelManager.installedSpecs(this).any { it == ModelManager.WH_LARGE }) {
                AsrPrefs.setWhisperId(this, ModelManager.WH_LARGE.id)
                prefs.raw().edit().putBoolean("autoDlDone", true).apply()
                return
            }
            if (prefs.raw().getBoolean("autoDlDone", false) && !ModelDownloadService.running &&
                !prefs.raw().getBoolean("largeAsked", false)) {
                // régi telepítés (turbo/kisebb modell): a large-v3-at is letöltjük egyszer
                prefs.raw().edit().putBoolean("autoDlDone", false).putBoolean("largeAsked", true).apply()
            }
            if (prefs.raw().getBoolean("autoDlDone", false)) return
            val cm = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            val unmetered = caps != null &&
                caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            if (!unmetered || ModelDownloadService.running) return
            prefs.raw().edit().putBoolean("autoDlDone", true).apply()
            ModelDownloadService.start(this, listOf(ModelManager.WH_LARGE.id, "mlkit"))
        } catch (_: Exception) {
        }
    }

    /** A Beállításokból visszatérve azonnal alkalmazza a módosított Compose-témát. */
    private fun themeSignature(): String = listOf(
        prefs.luxuryThemeId,
        prefs.platinumTheme.toString(),
        prefs.oledBlack.toString()
    ).joinToString("|")

    override fun onResume() {
        super.onResume()
        if (!::prefs.isInitialized) return
        val current = themeSignature()
        val previous = appliedThemeSignature
        if (previous != null && current != previous) {
            // Frissítsük az összehasonlítás alapját a recreate() előtt, hogy ne induljon újra végtelenül.
            appliedThemeSignature = current
            recreate()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = Prefs(this)
        appliedThemeSignature = themeSignature()
        if (!prefs.onboarded && savedInstanceState == null) {
            startActivity(Intent(this, SetupWizardActivity::class.java))
        }
        forwardLegacyExtras(intent)
        autoDownloadModels()

        setContent {
            AngolMagyarTheme(isTrueBlack = prefs.oledBlack) {
                ProvidePremiumDesignSystem {
                HomeScreen(
                    onSubtitles = {
                        startActivity(Intent(this, FileSubtitleActivity::class.java)
                            .putExtra(FileSubtitleActivity.EXTRA_AUTOSTART, true))
                    },
                    onOpenJob = { startActivity(Intent(this, FileSubtitleActivity::class.java)) },
                    onLive = { startActivity(Intent(this, LiveActivity::class.java)) },
                    onMic = {
                        startActivity(Intent(this, LiveActivity::class.java)
                            .putExtra(LiveActivity.EXTRA_AUTO_START, TranslateService.MODE_MIC))
                    },
                    onTranscripts = { startActivity(Intent(this, TranscriptActivity::class.java)) },
                    onPhrasebook = { startActivity(Intent(this, PhrasebookActivity::class.java)) },
                    onModels = { startActivity(Intent(this, ModelsActivity::class.java)) },
                    onSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    onDiagnostics = { startActivity(Intent(this, DiagnosticsActivity::class.java)) },
                    onIntelligence = { startActivity(Intent(this, PlatinumIntelligenceActivity::class.java)) },
                    onOverlay = {
                        startActivity(Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:$packageName")
                        ))
                    },
                    onPause = {
                        startService(Intent(this, TranslateService::class.java).apply {
                            action = TranslateService.ACTION_TOGGLE_PAUSE
                        })
                    },
                    onStop = {
                        startService(Intent(this, TranslateService::class.java).apply {
                            action = TranslateService.ACTION_STOP
                        })
                    }
                )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        forwardLegacyExtras(intent)
    }

    /** A régi belépési pontok (óra, csempe, parancsikon) `auto_start` / `profile` extráját az élő képernyő kezeli. */
    private fun forwardLegacyExtras(i: Intent?) {
        if (i == null) return
        if (i.hasExtra(LiveActivity.EXTRA_AUTO_START) || i.hasExtra(LiveActivity.EXTRA_PROFILE)) {
            val fwd = Intent(this, LiveActivity::class.java).putExtras(i)
            i.removeExtra(LiveActivity.EXTRA_AUTO_START)
            i.removeExtra(LiveActivity.EXTRA_PROFILE)
            startActivity(fwd)
        }
    }
}

// ---------------------------------------------------------------------------------------------- kezdőlap

@Composable
fun HomeScreen(
    onSubtitles: () -> Unit,
    onOpenJob: () -> Unit,
    onLive: () -> Unit,
    onMic: () -> Unit,
    onTranscripts: () -> Unit,
    onPhrasebook: () -> Unit,
    onModels: () -> Unit,
    onSettings: () -> Unit,
    onDiagnostics: () -> Unit,
    onIntelligence: () -> Unit,
    onOverlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit
) {
    val snap by AppState.snapshot.collectAsState()

    // futó felirat-feladat (a SrtJob nem StateFlow, ezért félmásodpercenként olvassuk)
    var jobRunning by remember { mutableStateOf(false) }
    var jobPct by remember { mutableStateOf(0) }
    var jobMsg by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            jobRunning = SrtJob.running || SrtJob.batchRunning
            jobPct = SrtJob.overall.coerceIn(0, 100)
            jobMsg = SrtJob.message
            delay(500)
        }
    }

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    var elapsed by remember { mutableStateOf("") }
    val amplitudes = remember { mutableStateListOf<Float>().apply { repeat(24) { add(0.12f) } } }
    LaunchedEffect(snap.running, snap.sessionStartedAt) {
        while (true) {
            if (snap.running && snap.sessionStartedAt > 0L) {
                val sec = ((SystemClock.elapsedRealtime() - snap.sessionStartedAt) / 1000).toInt()
                elapsed = "%d:%02d".format(sec / 60, sec % 60)
                val lvl = AppState.level.coerceIn(0f, 1f)
                val varied = (lvl * 0.82f + 0.12f + 0.08f * sin(System.currentTimeMillis() / 220.0).toFloat())
                    .coerceIn(0.08f, 1f)
                if (amplitudes.isNotEmpty()) { amplitudes.removeAt(0); amplitudes.add(varied) }
                delay(70)
            } else {
                elapsed = ""
                // Idle mode should not poll the audio level at recording cadence.
                // Decay once, then sleep longer to reduce needless recompositions and battery use.
                for (i in amplitudes.indices) amplitudes[i] = (amplitudes[i] * 0.82f).coerceAtLeast(0.08f)
                delay(300)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AuroraBackground(active = snap.running || jobRunning)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Staggered(0, shown) { Header(running = snap.running || jobRunning, error = snap.lastError != null) }
            Staggered(1, shown) { DiamondExperiencePanel(active = snap.running || jobRunning) }
            Staggered(2, shown) {
                PlatinumUltraConsole(
                    active = snap.running,
                    jobRunning = jobRunning,
                    elapsed = elapsed,
                    level = AppState.level.coerceIn(0f, 1f),
                    jobPct = jobPct
                )
            }

            DownloadBanner(onClick = onModels)
            TranslationComfortCard()
            HeroCard(
                title = "Platinum Intelligence Suite",
                subtitle = "Prémium eszközközpont, diagnosztika és fejlesztési státuszok.",
                icon = Icons.Rounded.CloudDownload,
                onClick = onIntelligence
            )
            StateCards(error = snap.lastError?.message, onDiagnostics = onDiagnostics, onModels = onModels)

            Staggered(2, shown) {
                HeroCard(
                    title = "Indítás: felirat készítése",
                    subtitle = "Válassz videót vagy hangfájlt: pontos magyar felirat, egy érintésre.",
                    icon = Icons.Rounded.Movie,
                    onClick = onSubtitles
                )
            }

            AnimatedVisibility(
                visible = jobRunning,
                enter = fadeIn(tween(300)) + slideInVertically(tween(400)) { it / 4 },
                exit = fadeOut(tween(250))
            ) {
                JobCard(pct = jobPct, message = jobMsg, onOpen = onOpenJob)
            }

            Staggered(3, shown) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PremiumSectionTitle(
                        title = "Gyors műveletek",
                        subtitle = "A legfontosabb eszközök egyetlen érintésre"
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        TileCard(
                            title = "Élő fordítás", subtitle = "Mikrofon, belső hang",
                            icon = Icons.Rounded.Mic, onClick = onLive, modifier = Modifier.weight(1f)
                        )
                        TileCard(
                            title = "Átiratok", subtitle = "Mentett beszélgetések",
                            icon = Icons.Rounded.Description, onClick = onTranscripts, modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        TileCard(
                            title = "Saját szókincstár", subtitle = "Kézzel mentett fordítások",
                            icon = Icons.Rounded.BookmarkBorder, onClick = onPhrasebook, modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            AnimatedVisibility(
                visible = snap.running,
                enter = fadeIn(tween(300)) + slideInVertically(tween(400)) { it / 4 },
                exit = fadeOut(tween(250))
            ) {
                LiveCard(
                    snap = snap, elapsed = elapsed, amplitudes = amplitudes.toList(),
                    onPause = onPause, onStop = onStop
                )
            }

            if (!snap.running) {
                Staggered(4, shown) {
                    OutlinedButton(
                        onClick = onMic,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.Mic, contentDescription = null)
                        Text("  Gyors indítás mikrofonnal")
                    }
                }
            }

            Staggered(5, shown) { ThemePicker() }

            Staggered(5, shown) {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TextButton(onClick = onModels) { Text("Modellek") }
                        TextButton(onClick = onSettings) { Text("Beállítások") }
                        TextButton(onClick = onDiagnostics) { Text("Diagnosztika") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        TextButton(onClick = onOverlay) { Text("Lebegő ablak engedélye") }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}



/**
 * Platinum Ultra Console v12: a detailed, accessible status surface.
 * All values come from live app state; no decorative/fabricated activity metrics.
 */
@Composable
private fun PlatinumUltraConsole(
    active: Boolean,
    jobRunning: Boolean,
    elapsed: String,
    level: Float,
    jobPct: Int
) {
    val onyx = Color(0xFF090B10)
    val graphite = Color(0xFF171B23)
    val platinum = Color(0xFFF0EFEA)
    val steel = Color(0xFF9CA6B5)
    val champagne = Color(0xFFE6D5A8)
    val mint = Color(0xFF72D6AD)
    val status = when {
        active -> "ÉLŐ FORDÍTÁS AKTÍV"
        jobRunning -> "FELDOLGOZÁS FOLYAMATBAN"
        else -> "RENDSZER KÉSZENLÉTBEN"
    }
    val statusColor = if (active || jobRunning) mint else champagne
    // A szolgáltatásból érkező értékek megjelenítés előtt korlátozva vannak;
    // hibás vagy átmeneti állapotban sem csúszhat 0–100% tartományon kívül a UI.
    val safeLevel = level.coerceIn(0f, 1f)
    val safeJobPct = jobPct.coerceIn(0, 100)
    val levelPercent = (safeLevel * 100).toInt()
    val pulseTransition = rememberInfiniteTransition(label = "platinum_status_pulse")
    val statusPulse by pulseTransition.animateFloat(
        initialValue = 0.58f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "status_pulse"
    )
    val animatedLevel by animateFloatAsState(
        targetValue = if (active) safeLevel else if (jobRunning) safeJobPct / 100f else 0.04f,
        animationSpec = tween(durationMillis = 260),
        label = "platinum_level"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = onyx),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF252B36), graphite, Color(0xFF0B0D12))
                    )
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "PLATINUM  /  ULTRA CONSOLE",
                        color = champagne,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "Állapotközpont",
                        color = platinum,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFFAF7ED), Color(0xFF9DA6B3), Color(0xFF4B5360))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.size(27.dp)) {
                        val c = center
                        val r = size.minDimension * 0.42f
                        drawLine(Color(0xFF171B23), Offset(c.x, c.y - r), Offset(c.x + r, c.y + r * 0.72f), 2.dp.toPx())
                        drawLine(Color(0xFF171B23), Offset(c.x + r, c.y + r * 0.72f), Offset(c.x - r, c.y + r * 0.72f), 2.dp.toPx())
                        drawLine(Color(0xFF171B23), Offset(c.x - r, c.y + r * 0.72f), Offset(c.x, c.y - r), 2.dp.toPx())
                        drawLine(Color(0xFF171B23), Offset(c.x, c.y - r), Offset(c.x, c.y + r * 0.72f), 1.dp.toPx())
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color.White.copy(alpha = 0.055f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(9.dp)
                        .graphicsLayer { alpha = if (active || jobRunning) statusPulse else 1f; scaleX = if (active || jobRunning) statusPulse else 1f; scaleY = if (active || jobRunning) statusPulse else 1f }
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(Modifier.size(9.dp))
                Text(
                    status,
                    color = statusColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text("V16", color = steel, style = MaterialTheme.typography.labelSmall)
            }

            // Decorative spectrum is a direct visualization of the measured input level.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.22f))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("PLATINUM SPECTRUM  ·  40 CSATORNA", color = steel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(if (active) "BEMENET AKTÍV" else if (jobRunning) "FELADAT AKTÍV" else "NYUGALMI ÁLLAPOT", color = statusColor, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(8.dp))
                Canvas(
                    Modifier.fillMaxWidth().height(34.dp)
                        .semantics { contentDescription = "Hangszint vizualizáció: $levelPercent százalék" }
                ) {
                    val bars = 40
                    val gap = 2.dp.toPx()
                    val barWidth = ((size.width - gap * (bars - 1)) / bars).coerceAtLeast(1f)
                    for (i in 0 until bars) {
                        val centerBias = 1f - kotlin.math.abs(i - (bars - 1) / 2f) / (bars / 2f)
                        val normalized = if (active) (safeLevel * (0.35f + centerBias * 0.65f)).coerceIn(0.06f, 1f) else if (jobRunning) (safeJobPct / 100f) * (0.25f + centerBias * 0.55f) else 0.08f
                        val barHeight = (size.height * normalized).coerceAtLeast(2.dp.toPx())
                        val x = i * (barWidth + gap)
                        drawRoundRect(
                            color = if (active) Color(0xFF9CCDF5).copy(alpha = 0.45f + normalized * 0.55f) else champagne.copy(alpha = 0.3f + normalized * 0.6f),
                            topLeft = Offset(x, (size.height - barHeight) / 2f),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlatinumMetric(
                    modifier = Modifier.weight(1f),
                    label = "HANGSZINT",
                    value = "$levelPercent%",
                    detail = if (active) "Mikrofon / hangforrás" else "Nincs aktív mérés",
                    accent = Color(0xFF9CCDF5)
                )
                PlatinumMetric(
                    modifier = Modifier.weight(1f),
                    label = "MUNKAMENET",
                    value = if (active && elapsed.isNotBlank()) elapsed else if (jobRunning) "$safeJobPct%" else "—",
                    detail = if (active) "Eltelt idő" else if (jobRunning) "Feladat előrehaladása" else "Nincs futó feladat",
                    accent = champagne
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        if (active) "Bemeneti jelszint" else if (jobRunning) "Feldolgozási folyamat" else "Készenléti állapot",
                        color = platinum,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        if (active) "$levelPercent%" else if (jobRunning) "$safeJobPct%" else "OK",
                        color = steel,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                if (jobRunning && !active) {
                    LinearProgressIndicator(
                        progress = { safeJobPct / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(6.dp)),
                        color = champagne,
                        trackColor = Color.White.copy(alpha = 0.12f)
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { animatedLevel },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(6.dp)),
                        color = if (active) Color(0xFF9CCDF5) else mint.copy(alpha = 0.75f),
                        trackColor = Color.White.copy(alpha = 0.12f)
                    )
                }
            }

            Text(
                "PLATINUM INFINITY V16  •  KORLÁTOZOTT MÉRÉSI ÉRTÉKEK  •  40 CSATORNÁS SPEKTRUM",
                color = steel.copy(alpha = 0.82f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun PlatinumMetric(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    detail: String,
    accent: Color
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.09f), Color.White.copy(alpha = 0.025f))
                )
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(label, color = accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text(
            value,
            color = Color(0xFFF5F3ED),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            detail,
            color = Color(0xFF9CA6B5),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Diamond Experience hero: a lightweight, vector-rendered sapphire medallion.
 * Uses the existing reduced-motion and power-save preferences; no bitmap or network assets.
 */
@Composable
private fun DiamondExperiencePanel(active: Boolean) {
    val context = LocalContext.current
    val reducedMotion = remember(context) {
        Prefs(context).reduceMotion ||
            ((context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager)?.isPowerSaveMode == true)
    }
    val transition = rememberInfiniteTransition(label = "diamondExperience")
    val rotation = if (reducedMotion) 0f else {
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Restart),
            label = "diamondOrbit"
        )
        value
    }
    val shimmer = if (reducedMotion) 0.42f else {
        val value by transition.animateFloat(
            initialValue = 0.18f,
            targetValue = 0.72f,
            animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "diamondGlow"
        )
        value
    }

    PremiumCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Canvas(Modifier.size(104.dp).semantics { contentDescription = "Zafírkék gyémánt embléma" }) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension * 0.40f
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFF36CFFF).copy(alpha = 0.22f * shimmer), Color.Transparent),
                        center = c,
                        radius = radius * 1.55f
                    ),
                    radius = radius * 1.55f,
                    center = c
                )
                drawCircle(
                    brush = Brush.sweepGradient(
                        listOf(Color(0xFFBCEEFF), Color(0xFF287BDF), Color(0xFF43E5FF), Color(0xFFBCEEFF)),
                        center = c
                    ),
                    radius = radius,
                    style = Stroke(width = size.minDimension * 0.025f)
                )
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(Color.Transparent, Color(0xFF8DEBFF), Color(0xFF397FEA), Color.Transparent),
                        center = c
                    ),
                    startAngle = rotation,
                    sweepAngle = 112f,
                    useCenter = false,
                    topLeft = Offset(c.x - radius * 1.16f, c.y - radius * 1.16f),
                    size = androidx.compose.ui.geometry.Size(radius * 2.32f, radius * 2.32f),
                    style = Stroke(width = size.minDimension * 0.045f)
                )
                val top = Offset(c.x, c.y - radius * 0.72f)
                val right = Offset(c.x + radius * 0.68f, c.y - radius * 0.10f)
                val bottom = Offset(c.x, c.y + radius * 0.76f)
                val left = Offset(c.x - radius * 0.68f, c.y - radius * 0.10f)
                val center = Offset(c.x, c.y + radius * 0.06f)
                val gem = androidx.compose.ui.graphics.Path().apply {
                    moveTo(top.x, top.y); lineTo(right.x, right.y); lineTo(bottom.x, bottom.y)
                    lineTo(left.x, left.y); close()
                }
                drawPath(gem, Brush.linearGradient(listOf(Color(0xFFE5FAFF), Color(0xFF55BFFF), Color(0xFF124A9F))))
                drawLine(Color.White.copy(alpha = 0.86f), top, center, strokeWidth = size.minDimension * 0.018f)
                drawLine(Color.White.copy(alpha = 0.54f), top, left, strokeWidth = size.minDimension * 0.014f)
                drawLine(Color(0xFFB9F2FF).copy(alpha = 0.72f), center, right, strokeWidth = size.minDimension * 0.014f)
                drawLine(Color(0xFF071F50), left, bottom, strokeWidth = size.minDimension * 0.018f)
                drawLine(Color(0xFF071F50), bottom, right, strokeWidth = size.minDimension * 0.018f)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    "DIAMOND EXPERIENCE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (active) "A motor dolgozik" else "Prémium fordítási központ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Zafírkék felület · intelligens feldolgozás · letisztult vezérlés",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StatusPill(
                    label = if (active) "AKTÍV FOLYAMAT" else "DIAMOND BLUE",
                    accent = if (active) MaterialTheme.colorScheme.secondary else Color(0xFF70CFFF)
                )
            }
        }
    }
}


/** Gyors elérés: célnyelv és feliratfordítási mód, a beállítások mély menüje nélkül. */
@Composable
private fun TranslationComfortCard() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var target by remember { mutableStateOf(prefs.targetLang) }
    var mode by remember { mutableStateOf(OnlinePrefs.mode(ctx)) }
    var onlineReady by remember { mutableStateOf(OnlinePrefs.usable(ctx)) }
    var translationStyle by remember { mutableStateOf(OnlinePrefs.translationStyle(ctx)) }
    val languages = listOf(
        Prefs.LANG_HU to "Magyar", Prefs.LANG_EN to "Angol", Prefs.LANG_DE to "Német",
        Prefs.LANG_FR to "Francia", Prefs.LANG_ES to "Spanyol", Prefs.LANG_IT to "Olasz",
        Prefs.LANG_UK to "Ukrán"
    )
    PremiumCard(modifier = Modifier.fillMaxWidth()) {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PremiumSectionTitle(
            title = "Fordítási vezérlőpult",
            subtitle = "Célnyelv és fordítási mód · gyors hozzáférés"
        )
        Text("Forrásnyelv: ${languages.firstOrNull { it.first == prefs.sourceLang }?.second ?: prefs.sourceLang.uppercase()} (a Beállításokban módosítható)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            languages.filterNot { it.first == prefs.sourceLang }.forEach { (code, label) ->
                androidx.compose.material3.FilterChip(
                    selected = target == code,
                    onClick = { target = code; prefs.targetLang = code },
                    label = { Text(label) }
                )
            }
        }
        Text("Online / offline fordítási mód", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            androidx.compose.material3.FilterChip(
                selected = mode == OnlinePrefs.OFF,
                onClick = { OnlinePrefs.setMode(ctx, OnlinePrefs.OFF); mode = OnlinePrefs.OFF },
                label = { Text("Offline") }
            )
            androidx.compose.material3.FilterChip(
                selected = mode == OnlinePrefs.ONLINE,
                onClick = {
                    onlineReady = OnlinePrefs.usable(ctx)
                    if (onlineReady) { OnlinePrefs.setMode(ctx, OnlinePrefs.ONLINE); mode = OnlinePrefs.ONLINE }
                    else (ctx as? androidx.appcompat.app.AppCompatActivity)?.let { act ->
                        OnlineSettingsDialog.show(act) { onlineReady = OnlinePrefs.usable(ctx); if (onlineReady) { OnlinePrefs.setMode(ctx, OnlinePrefs.ONLINE); mode = OnlinePrefs.ONLINE } }
                    }
                },
                label = { Text("Online") }
            )
            androidx.compose.material3.FilterChip(
                selected = mode == OnlinePrefs.AUTO,
                onClick = {
                    onlineReady = OnlinePrefs.usable(ctx)
                    if (onlineReady) { OnlinePrefs.setMode(ctx, OnlinePrefs.AUTO); mode = OnlinePrefs.AUTO }
                    else (ctx as? androidx.appcompat.app.AppCompatActivity)?.let { act ->
                        OnlineSettingsDialog.show(act) { onlineReady = OnlinePrefs.usable(ctx); if (onlineReady) { OnlinePrefs.setMode(ctx, OnlinePrefs.AUTO); mode = OnlinePrefs.AUTO } }
                    }
                },
                label = { Text("Online + tartalék") }
            )
        }
        val modeDescription = when (mode) {
            OnlinePrefs.OFF -> "Eszközön futó fordítás · internet nélkül is használható, ha a nyelvi modell telepítve van."
            OnlinePrefs.ONLINE -> if (onlineReady) "Online nyelvi modell · a felismert szöveg elküldhető a beállított szolgáltatónak." else "Online módhoz szolgáltatás, API-kulcs és adatküldési hozzájárulás szükséges."
            else -> if (onlineReady) "Online fordítás, sikertelenség esetén offline tartalék." else "Jelenleg offline tartalék használható; az online fordításhoz beállítás szükséges."
        }
        Text(modeDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (OnlinePrefs.provider(ctx) != hu.angolmagyar.fordito.OnlineTranslator.Provider.DEEPL) {
            Text("Online LLM fordítási stílusa", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                TranslationStyle.entries.forEach { style ->
                    androidx.compose.material3.FilterChip(
                        selected = translationStyle == style,
                        onClick = { translationStyle = style; OnlinePrefs.setTranslationStyle(ctx, style) },
                        label = { Text(style.labelHu) }
                    )
                }
            }
            Text("A stílus a támogatott online nyelvimodell-fordításra vonatkozik; az offline fordító működését nem módosítja.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
}

@Composable
private fun Staggered(index: Int, shown: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(420, delayMillis = index * 80)) +
            slideInVertically(tween(480, delayMillis = index * 80, easing = FastOutSlowInEasing)) { it / 5 }
    ) { content() }
}

/** Nyomásra enyhén összehúzódó, visszarugózó elem. */
@Composable
private fun Modifier.pressScale(source: MutableInteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val ctx = LocalContext.current
    val reduceMotion = remember(ctx) {
        Prefs(ctx).reduceMotion ||
            ((ctx.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager)?.isPowerSaveMode == true)
    }
    val s by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press"
    )
    return this.graphicsLayer { scaleX = s; scaleY = s }
}

@Composable
private fun AuroraBackground(active: Boolean) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val powerSave = remember { (ctx.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager)?.isPowerSaveMode == true }
    val animate = !prefs.reduceMotion && !powerSave
    val p = MaterialTheme.colorScheme.primary
    val s = MaterialTheme.colorScheme.tertiary
    if (animate) {
        val t = rememberInfiniteTransition(label = "aurora")
        val a by t.animateFloat(initialValue = 0f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
        val b by t.animateFloat(initialValue = 1f, targetValue = 0f, animationSpec = infiniteRepeatable(tween(19000, easing = LinearEasing), RepeatMode.Reverse), label = "b")
        val boost by animateFloatAsState(if (active) 1f else 0.6f, tween(800), label = "boost")
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.radialGradient(listOf(p.copy(alpha = 0.30f * boost), Color.Transparent), center = Offset(size.width * (0.15f + 0.5f * a), size.height * (0.08f + 0.15f * b)), radius = size.maxDimension * 0.7f))
            drawRect(Brush.radialGradient(listOf(s.copy(alpha = 0.24f * boost), Color.Transparent), center = Offset(size.width * (0.9f - 0.5f * b), size.height * (0.55f + 0.2f * a)), radius = size.maxDimension * 0.6f))
        }
    } else {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(p.copy(alpha = 0.12f), Color.Transparent, s.copy(alpha = 0.10f))))
        }
    }
}

@Composable
private fun Header(running: Boolean, error: Boolean) {
    val ctx = LocalContext.current
    val lowMotion = remember { Prefs(ctx).reduceMotion || ((ctx.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager)?.isPowerSaveMode == true) }
    val pulse = if (running && !lowMotion) {
        val t = rememberInfiniteTransition(label = "dot")
        val value by t.animateFloat(initialValue = 0.35f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
        value
    } else 1f
    val dotColor = when {
        error -> MaterialTheme.colorScheme.error
        running -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    Column(Modifier.padding(top = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DiamondBrandMark(modifier = Modifier.size(38.dp), animated = !lowMotion)
            Text(
                "Angol–Magyar",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            StatusPill(label = "✦ GYÉMÁNT")
        }
        Text(
            text = "INTELLIGENS ÁTIRAT  ·  AI FORDÍTÁS  ·  OFFLINE KÉSZENLÉT",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
        )
        Row(
            modifier = Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = when {
                    error -> "Állapot: hiba történt, diagnosztika szükséges"
                    running -> "Állapot: a feldolgozás folyamatban van"
                    else -> "Állapot: készenlét, offline felismerés"
                }
            },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(9.dp).alpha(if (running) pulse else 1f).clip(CircleShape).background(dotColor)
            )
            Text(
                text = when {
                    error -> "  Hiba történt, nézd meg a diagnosztikát"
                    running -> "  Dolgozom…"
                    else -> "  Készenlét · offline felismerés"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HeroCard(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val ctx = LocalContext.current
    val lowMotion = remember { Prefs(ctx).reduceMotion || ((ctx.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager)?.isPowerSaveMode == true) }
    val shine = if (!lowMotion) {
        val t = rememberInfiniteTransition(label = "shine")
        val value by t.animateFloat(initialValue = -0.4f, targetValue = 1.4f, animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart), label = "shine")
        value
    } else 0.5f
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .pressScale(source)
            .clip(shape)
            .background(
                Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))
            )
            .clickable(interactionSource = source, indication = LocalIndication.current, onClick = onClick)
    ) {
        Canvas(Modifier.matchParentSize()) {
            val x = size.width * shine
            drawRect(
                Brush.linearGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent),
                    start = Offset(x - size.width * 0.25f, 0f),
                    end = Offset(x + size.width * 0.25f, size.height)
                )
            )
        }
        Row(
            Modifier.padding(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp)) }
            Column(Modifier.padding(start = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.92f))
            }
        }
    }
}

@Composable
private fun TileCard(
    title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier
) {
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .pressScale(source)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .clickable(interactionSource = source, indication = LocalIndication.current, onClick = onClick)
            .padding(18.dp)
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun JobCard(pct: Int, message: String, onOpen: () -> Unit) {
    val progress by animateFloatAsState(pct / 100f, tween(850, easing = FastOutSlowInEasing), label = "jobPct")
    val transition = rememberInfiniteTransition(label = "cinematicPipeline")
    val pulse by transition.animateFloat(
        initialValue = 0.58f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1050, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pipelinePulse"
    )
    val stages = listOf("Hang", "Betöltés", "Felismerés", "Fordítás", "Mentés")
    val activeStage = when {
        pct >= 96 -> 4
        pct >= 76 -> 3
        pct >= 43 -> 2
        pct >= 18 -> 1
        else -> 0
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("FOLYAMATBAN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text("Intelligens feldolgozás", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(58.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))) {
                    Text("$pct%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                }
            }
            // Cinematic stage rail: the active node breathes; completed nodes remain visibly confirmed.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                stages.forEachIndexed { index, stage ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(if (index == activeStage) 24.dp else 18.dp)
                            .clip(CircleShape)
                            .background(when {
                                index < activeStage -> Color(0xFF22C7A0)
                                index == activeStage -> MaterialTheme.colorScheme.primary.copy(alpha = pulse)
                                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                            }), contentAlignment = Alignment.Center) {
                            if (index < activeStage) Text("✓", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            else if (index == activeStage) Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White))
                        }
                        Text(stage, style = MaterialTheme.typography.labelSmall, color = if (index <= activeStage) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(10.dp).clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(Color(0xFF2563EB), Color(0xFF22D3EE), Color(0xFF60A5FA)))))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Teljes folyamat", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            if (message.isNotBlank()) {
                Text(message, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Részletes folyamat és napló", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Részletek", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun LiveCard(
    snap: hu.angolmagyar.fordito.AppSnapshot, elapsed: String, amplitudes: List<Float>,
    onPause: () -> Unit, onStop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
    ) {
        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (snap.paused) "Szünet" else "Élő fordítás",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (elapsed.isNotBlank()) Text(elapsed, style = MaterialTheme.typography.titleSmall)
            }
            LiveWaveform(amplitudes = amplitudes)
            val context = LocalContext.current
            var speakerReady by remember { mutableStateOf(false) }
            val speaker = remember(context) {
                TextToSpeech(context.applicationContext) { status ->
                    speakerReady = status == TextToSpeech.SUCCESS
                }
            }
            DisposableEffect(speaker) {
                onDispose {
                    speaker.stop()
                    speaker.shutdown()
                }
            }
            PremiumTranslationCard(
                sourceLabel = "Angol",
                targetLabel = "Magyar",
                sourceText = snap.lastCaptionEn,
                translatedText = snap.lastCaptionHu,
                onCopySource = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Angol szöveg", snap.lastCaptionEn))
                },
                onCopyTranslation = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Magyar fordítás", snap.lastCaptionHu))
                },
                onSpeakTranslation = {
                    if (speakerReady && snap.lastCaptionHu.isNotBlank()) {
                        val localeResult = speaker.setLanguage(java.util.Locale("hu", "HU"))
                        if (localeResult != TextToSpeech.LANG_MISSING_DATA && localeResult != TextToSpeech.LANG_NOT_SUPPORTED) {
                            speaker.speak(snap.lastCaptionHu, TextToSpeech.QUEUE_FLUSH, null, "live-translation")
                        } else {
                            Toast.makeText(context, "Magyar beszédhang nem érhető el ezen az eszközön.", Toast.LENGTH_SHORT).show()
                        }
                    } else if (!speakerReady) {
                        Toast.makeText(context, "A felolvasó még inicializálódik.", Toast.LENGTH_SHORT).show()
                    }
                },
                onSaveTranslation = {
                    val saved = PhrasebookStore(context).save(snap.lastCaptionEn, snap.lastCaptionHu)
                    Toast.makeText(context, if (saved) "Fordítás elmentve a saját szókincstárba." else "Üres vagy már mentett fordítás.", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (!snap.hint.isNullOrBlank()) {
                Text(
                    snap.hint ?: "", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Icon(if (snap.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, contentDescription = null)
                    Text(if (snap.paused) "  Folytatás" else "  Szünet")
                }
                androidx.compose.material3.Button(
                    onClick = onStop, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Rounded.Stop, contentDescription = null)
                    Text("  Leállítás")
                }
            }
        }
    }
}

@Composable
fun LiveWaveform(amplitudes: List<Float>) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val secondary = MaterialTheme.colorScheme.secondary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    // Multi-band: low / mid / high sávok az amplitude history-ből
    val bands = remember(amplitudes) {
        val n = amplitudes.size.coerceAtLeast(1)
        val low = FloatArray(n)
        val mid = FloatArray(n)
        val high = FloatArray(n)
        for (i in amplitudes.indices) {
            val a = amplitudes[i].coerceIn(0.05f, 1f)
            // időbeli szomszédokból "frekvencia" jellegű sávok
            val prev = amplitudes.getOrElse(i - 1) { a }
            val next = amplitudes.getOrElse(i + 1) { a }
            val smooth = (prev + a + next) / 3f
            val detail = (a - smooth).let { if (it < 0) -it else it }
            low[i] = smooth * 0.95f
            mid[i] = a * 0.85f
            high[i] = (detail * 2.2f + a * 0.35f).coerceIn(0.05f, 1f)
        }
        Triple(low, mid, high)
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val count = amplitudes.size.coerceAtLeast(1)
        val groupW = width / count
        val barW = (groupW * 0.22f).coerceIn(2.5f, 8f)

        drawRoundRect(
            color = surfaceVariant.copy(alpha = 0.3f),
            topLeft = Offset(0f, centerY - 3f),
            size = Size(width, 6f),
            cornerRadius = CornerRadius(3f, 3f)
        )

        fun drawBand(values: FloatArray, colorStart: androidx.compose.ui.graphics.Color,
                     colorEnd: androidx.compose.ui.graphics.Color, xOff: Float, phaseShift: Float) {
            for (i in values.indices) {
                val safe = values[i].coerceIn(0.06f, 1f)
                val animated = safe * (0.62f + 0.38f * sin(phase + i * 0.5f + phaseShift))
                val barHeight = (animated * height * 0.9f).coerceAtLeast(5f)
                val x = i * groupW + groupW / 2f + xOff
                val top = centerY - barHeight / 2f
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(colorStart, colorEnd),
                        startY = top,
                        endY = top + barHeight
                    ),
                    topLeft = Offset(x - barW / 2f, top),
                    size = Size(barW, barHeight),
                    cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                )
            }
        }

        drawBand(bands.first, primary.copy(alpha = 0.85f), primary, -barW * 1.15f, 0f)
        drawBand(bands.second, tertiary, primary, 0f, 0.7f)
        drawBand(bands.third, secondary.copy(alpha = 0.9f), tertiary, barW * 1.15f, 1.4f)
    }
}

/** Az automatikus (vagy kézi) modell-letöltés állapota a kezdőlapon: animált csík, koppintásra a Modellek képernyő. */
@Composable
fun DownloadBanner(onClick: () -> Unit) {
    var running by remember { mutableStateOf(false) }
    var pct by remember { mutableStateOf(0) }
    var msg by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            running = ModelDownloadService.running
            pct = ModelDownloadService.pct
            msg = ModelDownloadService.message
            delay(600)
        }
    }
    AnimatedVisibility(
        visible = running,
        enter = fadeIn(tween(300)) + slideInVertically(tween(400)) { -it / 3 },
        exit = fadeOut(tween(250))
    ) {
        androidx.compose.material3.Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("⬇ Modellek letöltése – a háttérben folyik", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                if (msg.isNotBlank()) Text(msg, style = MaterialTheme.typography.bodySmall)
                if (pct in 1..100) {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { pct / 100f }, modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** Téma-választó: kiemelőszín (köztük Gyémánt) + megjelenés (rendszer / világos / sötét / OLED fekete). Azonnal érvényesül. */
@Composable
fun ThemePicker() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var accent by remember { mutableStateOf(Accent.id(ctx)) }
    var mode by remember { mutableStateOf(prefs.themeMode) }
    var oled by remember { mutableStateOf(prefs.oledBlack) }
    var reduceMotion by remember { mutableStateOf(prefs.reduceMotion) }
    val activity = ctx as? android.app.Activity
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Megjelenés", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (t in Accent.ALL) {
                val sel = t.id == accent
                val size by animateFloatAsState(if (sel) 40f else 32f, spring(Spring.DampingRatioMediumBouncy), label = "dot")
                Box(
                    Modifier.size(size.dp).clip(CircleShape).background(Color(if (Accent.isNight(ctx)) t.dark else t.light))
                        .clickable {
                            Accent.set(ctx, t.id); accent = t.id
                            activity?.recreate()
                        },
                    contentAlignment = Alignment.Center
                ) { if (sel) Text("✓", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val labels = listOf(0 to "Rendszer", 1 to "Világos", 2 to "Sötét")
            for ((id, label) in labels) {
                androidx.compose.material3.FilterChip(
                    selected = mode == id,
                    onClick = { mode = id; prefs.themeMode = id; App.applyTheme(id) },
                    label = { Text(label) }
                )
            }
            androidx.compose.material3.FilterChip(
                selected = oled,
                onClick = { oled = !oled; prefs.oledBlack = oled; App.oledBlack = oled; activity?.recreate() },
                label = { Text("OLED") }
            )
        }
        androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Akkukímélő animációk", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text("Kevesebb folyamatos fény- és háttérmozgás", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.material3.Switch(checked = reduceMotion, onCheckedChange = {
                reduceMotion = it
                prefs.reduceMotion = it
                activity?.recreate() // a teljes képernyőn leállítja / újraindítja a folyamatos animációkat
            })
        }
    }
}

/** Üres / hiba állapotok: animált rajzolt ikon + egy gombos megoldás szöveges üzenet helyett. */
@Composable
fun StateCards(error: String?, onDiagnostics: () -> Unit, onModels: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var noModel by remember { mutableStateOf(false) }
    var online by remember { mutableStateOf(true) }
    var downloading by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            try {
                noModel = ModelManager.installedSpecs(ctx).none { it in ModelManager.WHISPER }
                downloading = ModelDownloadService.running
                val cm = ctx.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                val caps = cm.getNetworkCapabilities(cm.activeNetwork)
                online = caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            } catch (_: Exception) {}
            delay(2000)
        }
    }
    AnimatedVisibility(
        visible = error != null,
        enter = fadeIn(tween(300)) + slideInVertically(tween(400)) { -it / 4 }, exit = fadeOut(tween(200))
    ) {
        IllustratedState(Icons.Rounded.ErrorOutline, "Hiba történt", error ?: "", "Diagnosztika", MaterialTheme.colorScheme.error, onDiagnostics)
    }
    AnimatedVisibility(
        visible = error == null && noModel && !downloading && online,
        enter = fadeIn(tween(300)) + slideInVertically(tween(400)) { -it / 4 }, exit = fadeOut(tween(200))
    ) {
        IllustratedState(
            Icons.Rounded.CloudDownload, "Még nincs felismerő modell",
            "A pontos felismeréshez egyszeri letöltés kell (kb. 1 GB, Wi-Fin ajánlott). Utána internet nélkül is megy.",
            "Letöltöm", MaterialTheme.colorScheme.primary
        ) { ModelDownloadService.start(ctx, listOf(ModelManager.WH_LARGE.id, "mlkit")) }
    }
    AnimatedVisibility(
        visible = error == null && noModel && !online,
        enter = fadeIn(tween(300)) + slideInVertically(tween(400)) { -it / 4 }, exit = fadeOut(tween(200))
    ) {
        IllustratedState(
            Icons.Rounded.WifiOff, "Nincs internet",
            "A modellek letöltéséhez hálózat kell. Csatlakozz Wi-Fi-re, a letöltés utána itt folytatható.",
            "Modellek", MaterialTheme.colorScheme.outline, onModels
        )
    }
}

@Composable
private fun IllustratedState(
    icon: ImageVector, title: String, text: String, button: String, accent: Color, onClick: () -> Unit
) {
    val ctx = LocalContext.current
    val lowMotion = remember { Prefs(ctx).reduceMotion || ((ctx.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager)?.isPowerSaveMode == true) }
    val bob: Float
    val ring: Float
    if (!lowMotion) {
        val t = rememberInfiniteTransition(label = "state")
        val b by t.animateFloat(initialValue = -4f, targetValue = 4f, animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
        val r by t.animateFloat(initialValue = 0f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), label = "ring")
        bob = b
        ring = r
    } else { bob = 0f; ring = 0.2f }
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                val c = Offset(size.width / 2, size.height / 2)
                for (k in 0 until 2) {
                    val f = (ring + k * 0.5f) % 1f
                    drawCircle(accent.copy(alpha = (1f - f) * 0.35f), radius = size.minDimension * (0.34f + 0.16f * f), center = c)
                }
                drawCircle(accent.copy(alpha = 0.16f), radius = size.minDimension * 0.34f, center = c)
            }
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(44.dp).offset(y = bob.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        androidx.compose.material3.Button(onClick = onClick, shape = RoundedCornerShape(16.dp)) { Text(button) }
    }
}
