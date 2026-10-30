package hu.angolmagyar.fordito

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import hu.angolmagyar.fordito.ui.design.PremiumMotion
import hu.angolmagyar.fordito.ui.processing.PlatinumPanel
import hu.angolmagyar.fordito.ui.processing.ProcessingPanel
import hu.angolmagyar.fordito.ui.processing.ProcessingUiState
import hu.angolmagyar.fordito.ui.processing.SrtJobAdapter

/** Közös felület a régi [PipelineView] és az új [CinematicPipelineView] között. */
interface PipelineHost {
    fun set(newStates: IntArray, activePct: Int)
    fun setLive(heard: String, translated: String)
}

/**
 * A v4 feldolgozó kártya View-burkolója (a képernyő View-alapú). A kapott paramétereket nem használja:
 * a megjelenített állapotot minden híváskor a [SrtJob] aktuális értékeiből veszi ([SrtJobAdapter]).
 */
class CinematicPipelineView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : FrameLayout(ctx, attrs), PipelineHost {

    private var ui by mutableStateOf(SrtJobAdapter.snapshot())
    private val reduced = PremiumMotion.reduced(ctx)
    private val platinum = Prefs(ctx).platinumTheme

    init {
        addView(ComposeView(ctx).apply {
            setContent { if (platinum) PlatinumPanel(ui, reduced) else ProcessingPanel(ui, reduced) }
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun refresh() {
        val n: ProcessingUiState = SrtJobAdapter.snapshot()
        if (n != ui) ui = n
    }

    override fun set(newStates: IntArray, activePct: Int) = refresh()
    override fun setLive(heard: String, translated: String) = refresh()
}
