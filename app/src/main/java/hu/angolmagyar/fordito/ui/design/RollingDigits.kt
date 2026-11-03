package hu.angolmagyar.fordito.ui.design
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
/**
 * Számjegyenként gördülő szöveg (odometer). Az idő-értékek mindig a valós állapotból jönnek,
 * ez csak a megjelenítésük. Az iránya a változásból adódik: növekvő számjegy alulról érkezik és felfelé gördül,
 * csökkenő (visszaszámlálás) fentről érkezik és lefelé. A pozíciók jobbról vannak kulcsolva, így az egységek
 * számjegye hosszváltáskor ("9:59" → "10:00") is ugyanabban a szlotban marad.
 * A képernyőolvasó egyben, egy szövegként kapja meg az értéket.
 */
@Composable
fun RollingText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily = FontFamily.Monospace,
    reduced: Boolean = false,
    durationMs: Int = 260
) {
    val n = text.length
    Row(modifier.clearAndSetSemantics { contentDescription = text }) {
        for (i in 0 until n) {
            val ch = text[i]
            key(n - 1 - i) {
                if (ch in '0'..'9' && !reduced) {
                    AnimatedContent(
                        targetState = ch,
                        transitionSpec = {
                            val up = ((targetState - '0') - (initialState - '0') + 10) % 10 in 1..5
                            (slideInVertically(tween(durationMs)) { h -> if (up) h / 2 else -h / 2 } + fadeIn(tween(durationMs))) togetherWith
                                (slideOutVertically(tween(durationMs)) { h -> if (up) -h / 2 else h / 2 } + fadeOut(tween(durationMs)))
                        },
                        modifier = Modifier.clipToBounds(),
                        label = "roll_digit"
                    ) { c ->
                        Text(c.toString(), color = color, fontSize = fontSize, fontWeight = fontWeight, fontFamily = fontFamily, maxLines = 1)
                    }
                } else {
                    Text(ch.toString(), color = color, fontSize = fontSize, fontWeight = fontWeight, fontFamily = fontFamily, maxLines = 1)
                }
            }
        }
    }
}
