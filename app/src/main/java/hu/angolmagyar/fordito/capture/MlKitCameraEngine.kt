package hu.angolmagyar.fordito.capture
import android.graphics.BitmapFactory
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
object MlKitCameraEngine : CameraTranslateEngine {
    private const val MAX_BYTES = 12_000_000
    override fun readiness(): CaptureReadiness = CaptureReadiness.EXPERIMENTAL
    override fun recognize(imageJpeg: ByteArray): List<OcrBlock>? {
        if (imageJpeg.isEmpty() || imageJpeg.size > MAX_BYTES) return null
        val bitmap = BitmapFactory.decodeByteArray(imageJpeg, 0, imageJpeg.size) ?: return null
        return try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val result = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
            recognizer.close()
            result.textBlocks.map { b ->
                OcrBlock(text = b.text, confidence = null, languageHint = b.recognizedLanguage.takeIf { it.isNotBlank() })
            }
        } catch (_: Exception) {
            null
        } finally {
            bitmap.recycle()
        }
    }
}
