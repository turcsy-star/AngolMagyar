package hu.angolmagyar.fordito.ui.processing

enum class StageStatus { PENDING, RUNNING, COMPLETED, WARNING, FAILED, CANCELLED }

data class StageUi(
    val index: Int,
    val title: String,
    val status: StageStatus,
    /** null = nem ismert (nincs kitalált százalék). */
    val progress: Float?,
    val detail: String,
    val durationMs: Long
)

data class ProcessingUiState(
    val running: Boolean = false,
    val finished: Boolean = false,
    val cancelled: Boolean = false,
    val errorMessage: String? = null,
    val warningMessage: String? = null,
    val stages: List<StageUi> = emptyList(),
    val overallPercent: Int? = null,
    val elapsedMs: Long = 0L,
    /** null = még nincs becslés. */
    val remainingMs: Long? = null,
    val etaBasis: String = "",
    val chunksDone: Int = 0,
    val chunksTotal: Int? = null,
    val heard: String = "",
    val translated: String = "",
    val engineLabel: String = "",
    val fileNo: Int = 0,
    val fileTotal: Int = 0,
    val withTranslation: Boolean = false,
    /** Eddig lefordított sorok (online + offline motor együtt). */
    val translatedLines: Int = 0
)
