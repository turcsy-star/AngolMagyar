package hu.angolmagyar.fordito.capture
data class OcrBlock(
    val text: String,
    val confidence: Float?,
    val languageHint: String? = null
)
data class DocumentPage(
    val index: Int,
    val plainText: String,
    val isScannedLikely: Boolean
)
enum class CaptureReadiness {
    UNAVAILABLE,
    EXPERIMENTAL,
    READY
}
interface CameraTranslateEngine {
    fun readiness(): CaptureReadiness
    fun recognize(imageJpeg: ByteArray): List<OcrBlock>?
}
interface DocumentImportEngine {
    fun readiness(): CaptureReadiness
    fun importPdf(bytes: ByteArray, maxPages: Int = 50): List<DocumentPage>?
    fun importPlainText(bytes: ByteArray, charsetName: String = "UTF-8"): DocumentPage?
}
object UnavailableCameraEngine : CameraTranslateEngine {
    override fun readiness() = CaptureReadiness.UNAVAILABLE
    override fun recognize(imageJpeg: ByteArray): List<OcrBlock>? = null
}
object UnavailableDocumentEngine : DocumentImportEngine {
    override fun readiness() = CaptureReadiness.UNAVAILABLE
    override fun importPdf(bytes: ByteArray, maxPages: Int): List<DocumentPage>? = null
    override fun importPlainText(bytes: ByteArray, charsetName: String): DocumentPage? {
        if (bytes.size > 2_000_000) return null
        return try {
            DocumentPage(0, bytes.toString(charset(charsetName)).take(500_000), isScannedLikely = false)
        } catch (_: Exception) {
            null
        }
    }
}
object CaptureRegistry {
    @Volatile var camera: CameraTranslateEngine = UnavailableCameraEngine
    @Volatile var documents: DocumentImportEngine = UnavailableDocumentEngine
    fun cameraReady(): Boolean = camera.readiness() == CaptureReadiness.READY
    fun documentsReady(): Boolean = documents.readiness() == CaptureReadiness.READY
}
