package hu.angolmagyar.fordito

import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.TextView
import kotlin.math.ceil

/**
 * Kis animációs segédek a View-alapú képernyőkhöz (v2.22.3: a v2.22.0-ban elveszett, most újraírva).
 */
object Anim {
    private val main = Handler(Looper.getMainLooper())

    /** Staggered fade + csúszás a gyerek view-kra (lásd [Ui.animateIn]). */
    fun staggerIn(parent: ViewGroup, stepMs: Long = 60) = Ui.animateIn(parent, stepMs)

    /**
     * A szöveget "begépeli": a [from] karakterig már látható rész marad, a többi gyorsan, lépésenként jelenik meg.
     * Új hívás megszakítja az előző, még futó gépelést ugyanazon a TextView-n.
     */
    fun typeInto(tv: TextView, full: String, from: Int = 0, totalMs: Long = 500L) {
        (tv.getTag(R.id.tag_type_anim) as? Runnable)?.let { main.removeCallbacks(it) }
        val start = from.coerceIn(0, full.length)
        if (start >= full.length || totalMs <= 0L) {
            tv.text = full
            return
        }
        val remaining = full.length - start
        val ticks = 20
        val step = ceil(remaining / ticks.toDouble()).toInt().coerceAtLeast(1)
        val delay = (totalMs / ticks).coerceAtLeast(16L)
        tv.text = full.substring(0, start)
        val r = object : Runnable {
            var pos = start
            override fun run() {
                pos = (pos + step).coerceAtMost(full.length)
                tv.text = full.substring(0, pos)
                if (pos < full.length) main.postDelayed(this, delay)
                else tv.setTag(R.id.tag_type_anim, null)
            }
        }
        tv.setTag(R.id.tag_type_anim, r)
        main.postDelayed(r, delay)
    }
}
