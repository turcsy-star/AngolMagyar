package hu.angolmagyar.fordito

import org.junit.Assert.*
import org.junit.Test

class ModelAdvisorTest {
    @Test fun strongPhonePicksMostAccurateWithinTimeBudget() {
        val a = ModelAdvisor.advise(ModelAdvisor.Device(ramMb = 12_000, cores = 8, freeStorageMb = 20_000), maxRtf = 3.0)
        assertEquals("wh_large", a.pick.id)
        assertTrue(a.needsDownload)
    }

    @Test fun lowRamPhonePicksSmallModel() {
        val a = ModelAdvisor.advise(ModelAdvisor.Device(ramMb = 3_000, cores = 8, freeStorageMb = 20_000))
        assertEquals("wh_small", a.pick.id)
    }

    @Test fun tightTimeBudgetPicksFasterModel() {
        val a = ModelAdvisor.advise(ModelAdvisor.Device(ramMb = 8_000, cores = 4, freeStorageMb = 20_000), maxRtf = 1.0)
        assertTrue(ModelAdvisor.estimateRtf(a.pick, ModelAdvisor.Device(8_000, 4, 20_000)) <= 1.0)
    }

    @Test fun installedModelNeedsNoStorage() {
        val d = ModelAdvisor.Device(ramMb = 8_000, cores = 8, freeStorageMb = 10, installedIds = setOf("wh_turbo"))
        val a = ModelAdvisor.advise(d)
        assertEquals("wh_turbo", a.pick.id)
        assertFalse(a.needsDownload)
    }

    @Test fun thermalStressSlowsEstimate() {
        val c = ModelAdvisor.DEFAULTS.first { it.id == "wh_turbo" }
        assertTrue(ModelAdvisor.estimateRtf(c, ModelAdvisor.Device(8000, 8, 9999, thermalStatus = 3)) > ModelAdvisor.estimateRtf(c, ModelAdvisor.Device(8000, 8, 9999)))
    }

    @Test fun estimatedMinutesRoundUp() {
        val a = ModelAdvisor.advise(ModelAdvisor.Device(8_000, 8, 9_999), audioMinutes = 10)
        assertNotNull(a.estimatedMinutes)
        assertTrue(a.estimatedMinutes!! >= 1)
    }

    @Test fun hopelessDeviceStillGetsSmallestModel() {
        val a = ModelAdvisor.advise(ModelAdvisor.Device(1_000, 2, 10))
        assertEquals("wh_base", a.pick.id)
    }
}
