package hu.angolmagyar.fordito.ui.processing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.angolmagyar.fordito.ui.design.PlatinumColors

/**
 * Broadcast-stílusú lower-third (v3.10 Ultimate Cinema).
 * TV-s becsúszó sáv élő felirathoz / fordításhoz.
 */
@Composable
fun DiamondLowerThird(
    visible: Boolean,
    phaseLabel: String,
    primary: String,
    secondary: String,
    reduced: Boolean,
    cinema: Boolean,
    modifier: Modifier = Modifier
) {
    val ms = if (reduced) 0 else if (cinema) 420 else 280
    AnimatedVisibility(
        visible = visible && (primary.isNotBlank() || secondary.isNotBlank()),
        enter = slideInHorizontally(tween(ms)) { -it / 3 } + fadeIn(tween(ms)),
        exit = slideOutHorizontally(tween(if (reduced) 0 else 220)) { -it / 4 } + fadeOut(tween(200)),
        modifier = modifier
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xE6111520),
                            Color(0xCC171B23),
                            Color(0x99111A22)
                        )
                    )
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(42.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(PlatinumColors.Champagne, PlatinumColors.IceBlue)
                        )
                    )
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    phaseLabel.uppercase(),
                    color = PlatinumColors.Champagne,
                    fontSize = 9.sp,
                    letterSpacing = 1.6.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (primary.isNotBlank()) {
                    Text(
                        primary,
                        color = PlatinumColors.Text,
                        fontSize = if (cinema) 15.sp else 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (secondary.isNotBlank()) {
                    Text(
                        secondary,
                        color = PlatinumColors.IceBlue,
                        fontSize = if (cinema) 14.sp else 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
