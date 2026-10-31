package hu.angolmagyar.fordito.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SyncContractTest {
    @Test
    fun defaultProviderIsDisabledNoOp() {
        SyncRegistry.register(null)
        assertEquals("noop", SyncRegistry.current().id)
        assertEquals(SyncState.DISABLED, SyncRegistry.current().state())
        assertFalse(SyncRegistry.isEffectivelyEnabled())
        assertFalse(
            SyncRegistry.current().enqueuePush(
                EncryptedSyncEnvelope(ByteArray(0), "k", 1, 0L)
            )
        )
    }
}
