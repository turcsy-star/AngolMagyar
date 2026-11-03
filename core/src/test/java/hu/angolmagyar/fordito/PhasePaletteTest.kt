package hu.angolmagyar.fordito

import hu.angolmagyar.fordito.PhasePalette.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PhasePaletteTest {
    private fun phase(step: Int, running: Boolean = true, finished: Boolean = false, failed: Boolean = false,
                      tr: Boolean = true, msg: String = "", cooling: Boolean = false) =
        PhasePalette.phaseOf(step, running, finished, failed, tr, msg, cooling)

    @Test fun phaseMapping() {
        assertEquals(Phase.IDLE, phase(-1, running = false))
        assertEquals(Phase.PREPARE, phase(PhasePalette.STEP_EXTRACT))
        assertEquals(Phase.RECOGNIZE, phase(PhasePalette.STEP_RECOG, msg = "Whisper 40%"))
        assertEquals(Phase.TRANSLATE, phase(PhasePalette.STEP_RECOG, msg = "Fordítás (online)"))
        assertEquals(Phase.RECOGNIZE, phase(PhasePalette.STEP_RECOG, tr = false, msg = "Fordítás"))
        assertEquals(Phase.SAVE, phase(PhasePalette.STEP_SAVE))
        assertEquals(Phase.DONE, phase(PhasePalette.STEP_SAVE, running = false, finished = true))
        assertEquals(Phase.ERROR, phase(PhasePalette.STEP_RECOG, failed = true))
        assertEquals(Phase.PAUSED, phase(PhasePalette.STEP_RECOG, cooling = true))
    }

    @Test fun allPhaseColorsAreOpaqueAndDistinct() {
        val colors = Phase.values().map { PhasePalette.color(it) }
        assertEquals(colors.size, colors.toSet().size)
        assert(colors.all { (it ushr 24) == 0xFF })
    }

    @Test fun blendEndpointsAndMiddle() {
        val a = 0xFF000000.toInt(); val b = 0xFFFFFFFF.toInt()
        assertEquals(a, PhasePalette.blend(a, b, 0f))
        assertEquals(b, PhasePalette.blend(a, b, 1f))
        assertEquals(0xFF808080.toInt(), PhasePalette.blend(a, b, 0.5f))
        assertEquals(b, PhasePalette.blend(a, b, 7f))
    }

    @Test fun withAlphaKeepsRgb() {
        assertEquals(0x802F9ADA.toInt(), PhasePalette.withAlpha(0xFF2F9ADA.toInt(), 0.5f))
    }

    @Test fun trackerHoldsFlickeringPhases() {
        val t = PhasePalette.Tracker(1500, Phase.IDLE)
        assertEquals(Phase.PREPARE, t.update(0, Phase.PREPARE))          // IDLE-ről azonnal indul
        assertEquals(Phase.PREPARE, t.update(100, Phase.RECOGNIZE))      // még nem stabil
        assertEquals(Phase.PREPARE, t.update(900, Phase.RECOGNIZE))
        assertEquals(Phase.RECOGNIZE, t.update(1700, Phase.RECOGNIZE))   // 1,5 s után vált
        assertEquals(Phase.RECOGNIZE, t.update(1800, Phase.TRANSLATE))   // villanás
        assertEquals(Phase.RECOGNIZE, t.update(1900, Phase.RECOGNIZE))   // visszaáll, a jelölt törlődik
        assertEquals(Phase.RECOGNIZE, t.update(2000, Phase.TRANSLATE))
        assertEquals(Phase.TRANSLATE, t.update(3600, Phase.TRANSLATE))
    }

    @Test fun trackerShowsDoneAndErrorImmediately() {
        val t = PhasePalette.Tracker(1500, Phase.RECOGNIZE)
        t.update(0, Phase.RECOGNIZE)
        assertEquals(Phase.ERROR, t.update(10, Phase.ERROR))
        assertEquals(Phase.DONE, t.update(20, Phase.DONE))
        assertNotEquals(Phase.DONE, t.update(30, Phase.IDLE))
    }
}
