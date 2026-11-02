package hu.angolmagyar.fordito
import hu.angolmagyar.fordito.capture.CaptureRegistry
import hu.angolmagyar.fordito.sync.SyncRegistry
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
    fun mayOfferCloudSyncUi(): Boolean = current().cloudSync
    fun mayOfferCameraTranslateUi(): Boolean = current().cameraOcr
}
