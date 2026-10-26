package hu.angolmagyar.fordito.ui

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hu.angolmagyar.fordito.Accent
import hu.angolmagyar.fordito.App
import hu.angolmagyar.fordito.AppState
import hu.angolmagyar.fordito.DiagnosticsActivity
import hu.angolmagyar.fordito.FileSubtitleActivity
import hu.angolmagyar.fordito.ModelDownloadService
import hu.angolmagyar.fordito.ModelManager
import hu.angolmagyar.fordito.ModelsActivity
import hu.angolmagyar.fordito.Prefs
import hu.angolmagyar.fordito.SettingsActivity
import hu.angolmagyar.fordito.SetupWizardActivity
import hu.angolmagyar.fordito.SrtJob
import hu.angolmagyar.fordito.TranscriptActivity
import hu.angolmagyar.fordito.TranslateService
import hu.angolmagyar.fordito.ui.theme.AngolMagyarTheme
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin
import hu.angolmagyar.fordito.MainActivity as LiveActivity

/**
 * v2.22.3 – Material 3 kezdőlap: egy érintéssel felirat-készítés, élő fordítás, átiratok.
 * Animált háttér (aurora), lépcsőzetes belépés, nyomásra rugózó kártyák, élő hanghullám.
 *
 * A részletes élő-fordítás képernyő a [LiveActivity] (a manifestben regisztrálva); a Wear, a gyorscsempe, a
 * parancsikonok és a feladat-beállító ennek adják át az `auto_start` / `profile` extrát.
 */
class MainActivity : androidx.appcompat.app.AppCompatActivity() {

    private lateinit var prefs: Prefs

    /**
     * Első indításkor magától letölti az alap modelleket (Whisper large-v3 + offline fordító), ha még nincs
     * Whisper-modell és Wi-Fin (nem mobilneten) vagyunk. Egyszer próbálja; a Modellek képernyőn bármikor kézzel is lehet.
     */
    private fun autoDownloadModels() {
        try {
            if (prefs.raw().getBoolean("autoDlDone", false)) return
            if (ModelManager.installedSpecs(this).any { it == ModelManager.WH_LARGE || it == ModelManager.WH_TURBO }) {
                prefs.raw().edit().putBoolean("autoDlDone", true).apply()
                return
            }
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = Prefs(this)
        if (!prefs.onboarded && savedInstanceState == null) {
            startActivity(Intent(this, SetupWizardActivity::class.java))
        }
        forwardLegacyExtras(intent)
        autoDownloadModels()

        setContent {
            AngolMagyarTheme(isTrueBlack = prefs.oledBlack) {
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
                    onModels = { startActivity(Intent(this, ModelsActivity::class.java)) },
                    onSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    onDiagnostics = { startActivity(Intent(this, DiagnosticsActivity::class.java)) },
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
    onModels: () -> Unit,
    onSettings: () -> Unit,
    onDiagnostics: () -> Unit,
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
            } else {
                elapsed = ""
                for (i in amplitudes.indices) amplitudes[i] = (amplitudes[i] * 0.82f).coerceAtLeast(0.08f)
            }
            delay(70)
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

            DownloadBanner(onClick = onModels)
            StateCards(error = snap.lastError?.message, onDiagnostics = onDiagnostics, onModels = onModels)

            Staggered(1, shown) {
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

            Staggered(2, shown) {
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
                Staggered(3, shown) {
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

            Staggered(4, shown) { ThemePicker() }

            Staggered(4, shown) {
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
    val s by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press"
    )
    return this.graphicsLayer { scaleX = s; scaleY = s }
}

@Composable
private fun AuroraBackground(active: Boolean) {
    val t = rememberInfiniteTransition(label = "aurora")
    val a by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse), label = "a"
    )
    val b by t.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(19000, easing = LinearEasing), RepeatMode.Reverse), label = "b"
    )
    val boost by animateFloatAsState(if (active) 1f else 0.6f, tween(800), label = "boost")
    val p = MaterialTheme.colorScheme.primary
    val s = MaterialTheme.colorScheme.tertiary
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            Brush.radialGradient(
                listOf(p.copy(alpha = 0.30f * boost), Color.Transparent),
                center = Offset(size.width * (0.15f + 0.5f * a), size.height * (0.08f + 0.15f * b)),
                radius = size.maxDimension * 0.7f
            )
        )
        drawRect(
            Brush.radialGradient(
                listOf(s.copy(alpha = 0.24f * boost), Color.Transparent),
                center = Offset(size.width * (0.9f - 0.5f * b), size.height * (0.55f + 0.2f * a)),
                radius = size.maxDimension * 0.6f
            )
        )
    }
}

@Composable
private fun Header(running: Boolean, error: Boolean) {
    val t = rememberInfiniteTransition(label = "dot")
    val pulse by t.animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse"
    )
    val dotColor = when {
        error -> MaterialTheme.colorScheme.error
        running -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    Column(Modifier.padding(top = 8.dp)) {
        Text(
            "Angol–Magyar",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
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
    val t = rememberInfiniteTransition(label = "shine")
    val shine by t.animateFloat(
        initialValue = -0.4f, targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart), label = "shine"
    )
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
    val progress by animateFloatAsState(pct / 100f, tween(500), label = "jobPct")
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Felirat készül", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("$pct%", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
            )
            if (message.isNotBlank()) {
                Text(
                    message, style = MaterialTheme.typography.bodySmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Text(
                "Érintsd meg a részletekért", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp)
            )
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
            Crossfade(targetState = snap.lastCaptionHu, animationSpec = tween(250), label = "hu") { hu ->
                Text(
                    text = hu.ifBlank { "Várakozás a beszédre…" },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (hu.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (snap.lastCaptionEn.isNotBlank()) {
                Text(
                    snap.lastCaptionEn, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
            }
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

/** Téma-választó: kiemelőszín (5 szín) + megjelenés (rendszer / világos / sötét / OLED fekete). Azonnal érvényesül. */
@Composable
fun ThemePicker() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var accent by remember { mutableStateOf(Accent.id(ctx)) }
    var mode by remember { mutableStateOf(prefs.themeMode) }
    var oled by remember { mutableStateOf(prefs.oledBlack) }
    val activity = ctx as? android.app.Activity
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Megjelenés", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
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
    val t = rememberInfiniteTransition(label = "state")
    val bob by t.animateFloat(
        initialValue = -4f, targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob"
    )
    val ring by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), label = "ring"
    )
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
