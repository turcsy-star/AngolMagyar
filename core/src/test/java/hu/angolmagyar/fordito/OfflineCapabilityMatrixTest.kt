package hu.angolmagyar.fordito

import org.junit.Assert.*
import org.junit.Test

class OfflineCapabilityMatrixTest {
    @Test fun localVerifiedComponentsAreOfflineReady() {
        val cap = OfflineCapabilityMatrix.Capability("translate", setOf("mt-hu-en"))
        val result = OfflineCapabilityMatrix.assess(cap,
            OfflineCapabilityMatrix.Snapshot(setOf("mt-hu-en"), setOf("mt-hu-en"), false))
        assertEquals(OfflineCapabilityMatrix.State.READY_OFFLINE, result.state)
        assertTrue(result.worksWithoutNetwork)
    }

    @Test fun missingAndUnverifiedModelsAreNotAdvertisedReady() {
        val cap = OfflineCapabilityMatrix.Capability("asr", setOf("whisper-hu"))
        val absent = OfflineCapabilityMatrix.assess(cap, OfflineCapabilityMatrix.Snapshot(emptySet(), emptySet(), false))
        assertEquals(OfflineCapabilityMatrix.State.MISSING_COMPONENT, absent.state)
        val unverified = OfflineCapabilityMatrix.assess(cap,
            OfflineCapabilityMatrix.Snapshot(setOf("whisper-hu"), emptySet(), false))
        assertEquals(OfflineCapabilityMatrix.State.UNVERIFIED_COMPONENT, unverified.state)
    }

    @Test fun onlineOnlyCapabilityDoesNotPretendToWorkOffline() {
        val cap = OfflineCapabilityMatrix.Capability("cloud-translate", requiresNetwork = true)
        val offline = OfflineCapabilityMatrix.assess(cap, OfflineCapabilityMatrix.Snapshot(emptySet(), emptySet(), false))
        assertEquals(OfflineCapabilityMatrix.State.NETWORK_UNAVAILABLE, offline.state)
        assertFalse(offline.worksWithoutNetwork)
        val online = OfflineCapabilityMatrix.assess(cap, OfflineCapabilityMatrix.Snapshot(emptySet(), emptySet(), true))
        assertEquals(OfflineCapabilityMatrix.State.READY_ONLINE_ONLY, online.state)
    }

    @Test fun duplicateCapabilityIdsRejected() {
        val cap = OfflineCapabilityMatrix.Capability("x")
        try {
            OfflineCapabilityMatrix.assessAll(listOf(cap, cap), OfflineCapabilityMatrix.Snapshot(emptySet(), emptySet(), false))
            fail("duplicate ids must fail")
        } catch (_: IllegalArgumentException) { }
    }
}
