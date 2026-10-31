package hu.angolmagyar.fordito

import hu.angolmagyar.fordito.capture.CaptureRegistry
import hu.angolmagyar.fordito.sync.SyncRegistry

/**
 * Central readiness gates for features that must not appear "done" without proof.
 * UI and settings should query this instead of assuming Camera/OCR/Cloud are live.
 */
object FeatureReadiness {

    data class Report(
        val translationMemorySecure: Boolean = true,
        val meetingWorkflow: Boolean = true,
        val spacedRepetition: Boolean = true,
        val cameraOcr: Boolean,
        val documentImport: Boolean,
        val cloudSync: Boolean
    ) {
        fun summaryLines(): List<String> = listOf(
            "Fordítási memória (titkosított): ${label(translationMemorySecure)}",
            "Meeting munkafolyamat: ${label(meetingWorkflow)}",
            "Spaced repetition: ${label(spacedRepetition)}",
            "Kamera / OCR: ${label(cameraOcr)}",
            "Dokumentum import: ${label(documentImport)}",
            "Felhőszinkron: ${label(cloudSync)}"
        )

        private fun label(ready: Boolean) = if (ready) "kész" else "nem READY"
    }

    fun current(): Report = Report(
        cameraOcr = CaptureRegistry.cameraReady(),
        documentImport = CaptureRegistry.documentsReady(),
        cloudSync = SyncRegistry.isEffectivelyEnabled()
    )

    /** True only when a user-facing "sync now" control may be shown as functional. */
    fun mayOfferCloudSyncUi(): Boolean = current().cloudSync

    /** True only when camera translate entry point may be shown as functional. */
    fun mayOfferCameraTranslateUi(): Boolean = current().cameraOcr
}
