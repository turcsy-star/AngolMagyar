package hu.angolmagyar.fordito.ui.processing

import hu.angolmagyar.fordito.SrtJob

/** A [SrtJob] valós állapotából készít UI-állapotot. Nem talál ki értéket. */
object SrtJobAdapter {

    private val titles = arrayOf("Előkészítés", "Hangkinyerés", "PCM betöltés", "Felismerés", "Mentés")

    fun snapshot(): ProcessingUiState {
        val job = SrtJob
        val now = System.currentTimeMillis()
        val warnRecog = job.skippedParts > 0 || job.translateNote.isNotBlank()

        val stages = job.steps.mapIndexed { i, s ->
            val warn = i == SrtJob.ST_RECOG && warnRecog
            StageUi(
                index = i,
                title = if (i == SrtJob.ST_RECOG) SrtJob.label(i) else titles.getOrElse(i) { "" },
                status = when (s.state) {
                    SrtJob.ACTIVE -> if (warn) StageStatus.WARNING else StageStatus.RUNNING
                    SrtJob.DONE -> if (warn) StageStatus.WARNING else StageStatus.COMPLETED
                    SrtJob.ERROR -> StageStatus.FAILED
                    SrtJob.STOPPED -> StageStatus.CANCELLED
                    else -> StageStatus.PENDING
                },
                progress = if (s.pct in 0..100) s.pct / 100f else null,
                detail = s.comment,
                durationMs = s.durMs
            )
        }

        val elapsed = when {
            job.startMs == 0L -> 0L
            job.endMs > 0L -> job.endMs - job.startMs
            else -> now - job.startMs
        }
        val warning = buildString {
            if (job.skippedParts > 0) append("${job.skippedParts} rész kihagyva (elakadás-őr)")
            if (job.translateNote.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(job.translateNote)
            }
        }.ifBlank { null }

        return ProcessingUiState(
            running = job.running,
            finished = job.finished,
            cancelled = job.cancelled,
            errorMessage = job.error,
            warningMessage = warning,
            stages = stages,
            overallPercent = if (job.running || job.finished) job.overall else null,
            elapsedMs = elapsed,
            remainingMs = if (job.etaAtMs > 0) job.etaMs else null,
            etaBasis = job.etaBasis,
            chunksDone = job.chunkDone,
            chunksTotal = job.chunkTotal.takeIf { it > 0 },
            heard = job.liveHeard,
            translated = job.liveTranslated,
            engineLabel = job.engineLabel,
            fileNo = SrtJob.fileNo,
            fileTotal = SrtJob.fileTotal,
            withTranslation = job.withTranslation,
            translatedLines = job.engOnline + job.engOffline
        )
    }
}
