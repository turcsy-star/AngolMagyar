package hu.angolmagyar.fordito.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureContractsTest {
    @Test
    fun cameraDefaultsUnavailable() {
        assertEquals(CaptureReadiness.UNAVAILABLE, CaptureRegistry.camera.readiness())
        assertFalse(CaptureRegistry.cameraReady())
        assertNull(CaptureRegistry.camera.recognize(ByteArray(16)))
    }

    @Test
    fun plainTextImportWorksWithSizeGuard() {
        val page = CaptureRegistry.documents.importPlainText("Hello".toByteArray())
        assertNotNull(page)
        assertEquals("Hello", page!!.plainText)
        assertFalse(page.isScannedLikely)
    }
}
