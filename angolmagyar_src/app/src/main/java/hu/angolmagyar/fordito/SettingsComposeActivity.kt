package hu.angolmagyar.fordito

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Compose-alapú Beállítások – Megjelenés / kényelem szekció.
 * A klasszikus SettingsActivity-ból is elérhető; részleges Compose migráció.
 */
class SettingsComposeActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        setContent {
            val ctx = LocalContext.current
            val dark = prefs.themeMode == 2 ||
                (prefs.themeMode == 0 && (resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES)
            val scheme = when {
                dark -> darkColorScheme(
                    primary = Color(0xFF60A5FA), secondary = Color(0xFF22D3EE),
                    tertiary = Color(0xFF38BDF8),
                    background = if (prefs.oledBlack) Color.Black else Color(0xFF070B16),
                    surface = if (prefs.oledBlack) Color(0xFF080D18) else Color(0xFF111C30),
                    surfaceVariant = Color(0xFF17243A),
                    onBackground = Color(0xFFEEF5FF), onSurface = Color(0xFFEEF5FF),
                    onSurfaceVariant = Color(0xFFA9BBD6), outline = Color(0xFF263650)
                )
                else -> lightColorScheme(
                    primary = Color(0xFF2563EB), secondary = Color(0xFF0891B2),
                    tertiary = Color(0xFF0EA5E9), background = Color(0xFFF3F6FC),
                    surface = Color.White, surfaceVariant = Color(0xFFEAF0FA),
                    onBackground = Color(0xFF101827), onSurface = Color(0xFF101827),
                    onSurfaceVariant = Color(0xFF58677D), outline = Color(0xFFD7E1F2)
                )
            }
            MaterialTheme(colorScheme = scheme) {
                SettingsComposeScreen(
                    prefs = prefs,
                    onBack = { NavTransitions.finishWithSlide(this) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsComposeScreen(prefs: Prefs, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var themeMode by remember { mutableIntStateOf(prefs.themeMode) }
    var oled by remember { mutableStateOf(prefs.oledBlack) }
    var haptics by remember { mutableStateOf(prefs.haptics) }
    var keepScreen by remember { mutableStateOf(prefs.keepScreenOn) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Megjelenés & kényelem") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Vissza")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionCard(title = "Téma", icon = Icons.Default.DarkMode) {
                ThemeOption("Rendszer", themeMode == 0) {
                    themeMode = 0; prefs.themeMode = 0; App.applyTheme(0)
                }
                ThemeOption("Világos", themeMode == 1) {
                    themeMode = 1; prefs.themeMode = 1; App.applyTheme(1)
                }
                ThemeOption("Sötét", themeMode == 2) {
                    themeMode = 2; prefs.themeMode = 2; App.applyTheme(2)
                }
                AnimatedVisibility(visible = themeMode != 1) {
                    SwitchRow(
                        title = "OLED pure black",
                        subtitle = "True-black háttér OLED kijelzőkön",
                        checked = oled
                    ) {
                        oled = it
                        prefs.oledBlack = it
                    }
                }
            }

            SectionCard(title = "Visszajelzés", icon = Icons.Default.Vibration) {
                SwitchRow(
                    title = "Haptikus rezgés",
                    subtitle = "Gombok és gesztusok tapintása",
                    checked = haptics
                ) {
                    haptics = it
                    prefs.haptics = it
                    if (it) Haptics.click(ctx)
                }
            }

            SectionCard(title = "Kijelző", icon = Icons.Default.PhoneAndroid) {
                SwitchRow(
                    title = "Képernyő ébren tartása",
                    subtitle = "Futás közben ne aludjon el a kijelző",
                    checked = keepScreen
                ) {
                    keepScreen = it
                    prefs.keepScreenOn = it
                }
            }

            Text(
                "A többi beállítás (felismerő, fordítás, szabályok) a klasszikus Beállításokban érhető el.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun SectionCard(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun ThemeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
