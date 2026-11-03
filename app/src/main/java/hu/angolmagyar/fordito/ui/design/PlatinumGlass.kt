package hu.angolmagyar.fordito.ui.design
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
@Suppress("UNUSED_PARAMETER")
fun Modifier.pressScale(reduced: Boolean = false): Modifier = this
// Tile-olt, fehér zajszemcse: megszünteti a gradiensek csíkozódását, és finom "matt üveg" tapintást ad.
private val grainBrush: ShaderBrush by lazy {
    val n = 64
    val rnd = java.util.Random(7L)
    val px = IntArray(n * n) { (rnd.nextInt(256) shl 24) or 0x00FFFFFF }
    val bmp = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
    ShaderBrush(ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}
/**
 * Valódi üvegfelület: tónusos ambient "bloom" (az elmosott háttér hatása), felső specular csík,
 * belső alsó árnyék, zajszemcse és fényes él. Nincs animáció, ezért reduced motion alatt is azonos.
 * A rajzolás drawWithCache-ben van, így csak méretváltozáskor épül újra.
 */
fun Modifier.glassSurface(
    shape: Shape = RoundedCornerShape(18.dp),
    accent: Color = PlatinumColors.DiamondWhite,
    grain: Boolean = true
): Modifier = this
    .clip(shape)
    .background(Color.White.copy(alpha = 0.045f))
    .drawWithCache {
        val w = size.width
        val h = size.height
        val reach = maxOf(w, h)
        val bloom = Brush.radialGradient(
            listOf(accent.copy(alpha = 0.22f), Color.Transparent), Offset(w * 0.10f, 0f), reach * 0.8f
        )
        val glow = Brush.radialGradient(
            listOf(accent.copy(alpha = 0.09f), Color.Transparent), Offset(w * 0.95f, h * 1.05f), reach * 0.65f
        )
        val sheen = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), 0f, h * 0.5f
        )
        val inner = Brush.verticalGradient(
            listOf(Color.Transparent, Color.Black.copy(alpha = 0.24f)), h * 0.55f, h
        )
        val hair = Brush.horizontalGradient(
            listOf(Color.Transparent, Color.White.copy(alpha = 0.65f), Color.Transparent), w * 0.06f, w * 0.94f
        )
        val hairH = 1.dp.toPx()
        onDrawWithContent {
            drawRect(bloom)
            drawRect(glow)
            drawRect(sheen)
            drawRect(inner)
            if (grain) drawRect(grainBrush, alpha = 0.045f)
            drawLine(hair, Offset(w * 0.06f, hairH * 0.5f), Offset(w * 0.94f, hairH * 0.5f), hairH)
            drawContent()
        }
    }
    .border(
        1.dp,
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.34f), Color.White.copy(alpha = 0.05f), accent.copy(alpha = 0.28f))
        ),
        shape
    )
@Composable
fun PlatinumGlassCard(
    modifier: Modifier = Modifier,
    reduced: Boolean = false,
    accent: Color = PlatinumColors.DiamondWhite,
    content: @Composable () -> Unit
) {
    Box(
        modifier.pressScale(reduced)
            .glassSurface(RoundedCornerShape(18.dp), accent)
            .padding(12.dp)
    ) { content() }
}
