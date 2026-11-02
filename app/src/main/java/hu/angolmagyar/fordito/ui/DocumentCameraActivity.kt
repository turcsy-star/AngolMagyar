package hu.angolmagyar.fordito.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.common.model.DownloadConditions
import hu.angolmagyar.fordito.Prefs
import hu.angolmagyar.fordito.capture.CaptureReadiness
import hu.angolmagyar.fordito.capture.MlKitCameraEngine
import hu.angolmagyar.fordito.capture.OcrTextCleaner
import hu.angolmagyar.fordito.capture.UnavailableDocumentEngine
import hu.angolmagyar.fordito.ui.design.PremiumCard
import hu.angolmagyar.fordito.ui.theme.AngolMagyarTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * P8 – Dokumentum / kamera: fotó vagy kép kiválasztása → eszközön futó OCR → szerkeszthető szöveg → fordítás.
 * A képből semmi nem kerül ki a telefonról; a fordítómodell első használatkor tölthető le.
 */
class DocumentCameraActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Prefs(this)
        setContent {
            AngolMagyarTheme(isTrueBlack = prefs.oledBlack) {
                DocumentCameraScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
private fun DocumentCameraScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Készíts fotót, vagy válassz képet / szövegfájlt.") }
    var ocrText by remember { mutableStateOf("") }
    var translated by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    fun recognize(uri: Uri) {
        busy = true
        translated = ""
        status = "Szövegfelismerés a telefonon…"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes == null) null else MlKitCameraEngine.recognize(bytes)
                }.getOrNull()
            }
            busy = false
            if (result == null) {
                status = "A képről nem sikerült szöveget olvasni (nem olvasható vagy túl nagy fájl)."
            } else {
                val text = OcrTextCleaner.fromBlocks(result)
                ocrText = text
                status = if (text.isBlank()) "Nem találtam szöveget a képen. Próbálj élesebb, jobban megvilágított képet."
                else "Kész: ${text.length} karakter. Ellenőrizd és javítsd a szöveget a fordítás előtt."
            }
        }
    }

    fun importText(uri: Uri) {
        busy = true
        scope.launch {
            val page = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes == null) null else UnavailableDocumentEngine.importPlainText(bytes)
                }.getOrNull()
            }
            busy = false
            if (page == null) status = "A szövegfájl nem olvasható vagy túl nagy (max. 2 MB)."
            else {
                ocrText = page.plainText
                translated = ""
                status = "Szövegfájl betöltve. Ellenőrizd a szöveget a fordítás előtt."
            }
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = photoUri
        if (ok && uri != null) recognize(uri) else status = "A fotókészítés megszakadt."
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val type = ctx.contentResolver.getType(uri).orEmpty()
        when {
            type.startsWith("image/") -> recognize(uri)
            type.startsWith("text/") -> importText(uri)
            type == "application/pdf" -> status = "PDF-import még nincs: készíts képernyőképet vagy fotót az oldalról, és azt válaszd ki."
            else -> status = "Ez a fájltípus nem támogatott. Válassz képet vagy szövegfájlt."
        }
    }

    fun launchCamera() {
        runCatching {
            val dir = File(ctx.cacheDir, "ocr").apply { mkdirs() }
            val file = File(dir, "capture.jpg")
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
            photoUri = uri
            takePicture.launch(uri)
        }.onFailure { status = "A kamera nem indítható: ${it.message ?: "ismeretlen hiba"}" }
    }

    fun translate() {
        val source = ocrText.trim()
        if (source.isEmpty()) { status = "Nincs lefordítandó szöveg."; return }
        busy = true
        status = "Fordítás (angol → magyar)… Az első használatkor a modell letöltődhet."
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.HUNGARIAN)
            .build()
        val translator = Translation.getClient(options)
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener {
                translator.translate(source)
                    .addOnSuccessListener { out -> translated = out; busy = false; status = "A fordítás kész."; translator.close() }
                    .addOnFailureListener { e -> busy = false; status = "Fordítási hiba: ${e.message ?: "ismeretlen"}"; translator.close() }
            }
            .addOnFailureListener { e ->
                busy = false
                status = "A fordítómodell nem érhető el (hálózat szükséges az első letöltéshez): ${e.message ?: ""}"
                translator.close()
            }
    }

    fun copy(label: String, text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        status = "Vágólapra másolva."
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("← Vissza") }
        Text("DIAMOND • DOKUMENTUM / KAMERA", color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text("Szöveg képről", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() })
        PremiumCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("A felismerés a telefonon fut, a kép nem kerül ki a készülékről. Latin betűs (pl. angol) szövegre készült; a pontosságot a fény és az élesség határozza meg.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { launchCamera() }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("📷 Fotó készítése")
                }
                OutlinedButton(onClick = { pickFile.launch("*/*") }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("🖼 Kép vagy szövegfájl kiválasztása")
                }
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Állapot: ${readinessLabel(MlKitCameraEngine.readiness())}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary)
            }
        }
        PremiumCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Felismert szöveg", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() })
                OutlinedTextField(ocrText, { ocrText = it; translated = "" }, Modifier.fillMaxWidth(),
                    label = { Text("Szerkeszthető szöveg") }, minLines = 4)
                Button(onClick = { translate() }, enabled = !busy && ocrText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Fordítás magyarra") }
                OutlinedButton(onClick = { copy("OCR", ocrText) }, enabled = ocrText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Szöveg másolása") }
            }
        }
        if (translated.isNotBlank()) {
            PremiumCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Fordítás", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() })
                    Text(translated)
                    OutlinedButton(onClick = { copy("Fordítás", translated) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Fordítás másolása")
                    }
                }
            }
        }
    }
}

private fun readinessLabel(r: CaptureReadiness): String = when (r) {
    CaptureReadiness.READY -> "kész"
    CaptureReadiness.EXPERIMENTAL -> "kísérleti – eszközteszt után lesz végleges"
    CaptureReadiness.UNAVAILABLE -> "nem elérhető"
}
