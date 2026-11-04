package hu.angolmagyar.fordito

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Palette
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
import hu.angolmagyar.fordito.ui.design.LuxuryPalettes
import hu.angolmagyar.fordito.ui.theme.AngolMagyarTheme

class SettingsComposeActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        setContent {
            val dark = prefs.themeMode == 2 ||
                (prefs.themeMode == 0 && (resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES)
            AngolMagyarTheme(darkTheme = dark, isTrueBlack = prefs.oledBlack) {
                SettingsComposeScreen(
                    prefs = prefs,
                    onBack = { NavTransitions.finishWithSlide(this) },
                    onThemeChanged = { recreate() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsComposeScreen(
    prefs: Prefs,
    onBack: () -> Unit,
    onThemeChanged: () -> Unit
) {
    val ctx = LocalContext.current
    var themeMode by remember { mutableIntStateOf(prefs.themeMode) }
    var selectedPalette by remember { mutableStateOf(prefs.luxuryThemeId) }
    var oled by remember { mutableStateOf(prefs.oledBlack) }
    var haptics by remember { mutableStateOf(prefs.haptics) }
    var keepScreen by remember { mutableStateOf(prefs.keepScreenOn) }
    var balancedMotion by remember { mutableStateOf(prefs.motionProfile == "subtle" && !prefs.reduceMotion) }
    var reduceMotion by remember { mutableStateOf(prefs.reduceMotion) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Megjelenés & kényelem") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Vissza")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionCard(title = "Téma", icon = Icons.Default.DarkMode) {
                ThemeOption("Rendszer", themeMode == 0) { themeMode = 0; prefs.themeMode = 0; App.applyTheme(0); onThemeChanged() }
                ThemeOption("Világos", themeMode == 1) { themeMode = 1; prefs.themeMode = 1; App.applyTheme(1); onThemeChanged() }
                ThemeOption("Sötét", themeMode == 2) { themeMode = 2; prefs.themeMode = 2; App.applyTheme(2); onThemeChanged() }
                AnimatedVisibility(visible = themeMode != 1) {
                    SwitchRow("OLED pure black", "True-black háttér OLED kijelzőkön", oled) {
                        oled = it; prefs.oledBlack = it; onThemeChanged()
                    }
                }
            }

            SectionCard(title = "Ultimate témák", icon = Icons.Default.Palette) {
                Text(
                    "A választott paletta az alkalmazás teljes prémium felületén érvényesül.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LuxuryPalettes.ultimate.forEach { palette ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            selectedPalette = palette.id
                            prefs.platinumTheme = true
                            prefs.luxuryThemeId = palette.id
                            onThemeChanged()
                        }.padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedPalette == palette.id, onClick = {
                            selectedPalette = palette.id
                            prefs.platinumTheme = true
                            prefs.luxuryThemeId = palette.id
                            onThemeChanged()
                        })
                        Row(
                            Modifier.weight(1f).padding(start = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                listOf(palette.background, palette.accent, palette.glow).forEach { swatch ->
                                    androidx.compose.foundation.layout.Box(
                                        Modifier.size(17.dp).background(swatch, RoundedCornerShape(6.dp))
                                    )
                                }
                            }
                            Text(palette.title, modifier = Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            SectionCard(title = "Animáció", icon = Icons.Default.Palette) {
                SwitchRow(
                    title = "Balanced mozgás",
                    subtitle = "Finom átmenetek, visszafogott fényhatások és animációk",
                    checked = balancedMotion
                ) {
                    balancedMotion = it
                    if (it) {
                        reduceMotion = false
                        prefs.reduceMotion = false
                        prefs.motionProfile = "subtle"
                    } else {
                        prefs.motionProfile = "reduced"
                    }
                }
                SwitchRow(
                    title = "Animációk csökkentése",
                    subtitle = "Kevesebb mozgás és alacsonyabb vizuális terhelés",
                    checked = reduceMotion
                ) {
                    reduceMotion = it
                    if (it) {
                        balancedMotion = false
                        prefs.reduceMotion = true
                        prefs.motionProfile = "reduced"
                    } else {
                        prefs.reduceMotion = false
                        prefs.motionProfile = "subtle"
                        balancedMotion = true
                    }
                }
            }

            SectionCard(title = "Visszajelzés", icon = Icons.Default.Vibration) {
                SwitchRow("Haptikus rezgés", "Gombok és gesztusok tapintása", haptics) {
                    haptics = it; prefs.haptics = it
                    if (it) Haptics.click(ctx)
                }
            }
            SectionCard(title = "Kijelző", icon = Icons.Default.PhoneAndroid) {
                SwitchRow("Képernyő ébren tartása", "Futás közben ne aludjon el a kijelző", keepScreen) {
                    keepScreen = it; prefs.keepScreenOn = it
                }
            }
            Text(
                "A felismerő, fordítási, modell- és adatkezelési beállítások változatlanul a klasszikus Beállításokban érhetők el.",
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
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 10.dp))
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun ThemeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
