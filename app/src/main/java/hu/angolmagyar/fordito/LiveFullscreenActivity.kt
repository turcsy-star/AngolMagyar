package hu.angolmagyar.fordito

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Teljes képernyős élő mód: nagy betűs, kétpaneles nézet (Eredeti / Fordítás) kivetítéshez vagy fektetett telefonhoz.
 * A képernyő nem alszik el, a rendszersávok el vannak rejtve, a betűméret csípéssel vagy gombbal állítható.
 */
class LiveFullscreenActivity : AppCompatActivity() {

    private val ui = Handler(Looper.getMainLooper())
    private lateinit var panels: LivePanels
    private var scale = 1f
    private lateinit var detector: ScaleGestureDetector

    private val poll = object : Runnable {
        override fun run() {
            panels.update(AppState.liveLog.value)
            if (!AppState.running) { /* a lista megmarad, de a kép nem frissül tovább */ }
            ui.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        App.applyOledToWindow(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val prefs = Prefs(this)
        panels = LivePanels(this, prefs, full = true)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@LiveFullscreenActivity, 16), Ui.dp(this@LiveFullscreenActivity, 12),
                Ui.dp(this@LiveFullscreenActivity, 16), Ui.dp(this@LiveFullscreenActivity, 12))
            setBackgroundColor(Ui.color(this@LiveFullscreenActivity, R.color.bg))
        }
        root.addView(panels.root, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(Ui.smallButton(this, "A−") { scale -= 0.15f; panels.scale(scale) })
        row.addView(Ui.smallButton(this, "A+") { scale += 0.15f; panels.scale(scale) })
        row.addView(Ui.smallButton(this, "Bezár") { finish() })
        root.addView(row)
        setContentView(root)

        detector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                scale = (scale * d.scaleFactor).coerceIn(0.6f, 2.5f)
                panels.scale(scale)
                return true
            }
        })

        WindowInsetsControllerCompat(window, root).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        detector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onResume() { super.onResume(); ui.post(poll) }
    override fun onPause() { ui.removeCallbacks(poll); super.onPause() }
}
