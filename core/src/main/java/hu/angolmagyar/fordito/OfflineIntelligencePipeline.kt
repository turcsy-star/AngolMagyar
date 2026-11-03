package hu.angolmagyar.fordito
object OfflineIntelligencePipeline {
    data class AuditReport(
        val invariantFindings: List<TranslationInvariantAudit.Finding>,
        val terminologyFindings: List<OfflineGlossaryAudit.Finding>
    ) {
        val hasWarnings: Boolean get() = invariantFindings.isNotEmpty() || terminologyFindings.isNotEmpty()
        val warningCount: Int get() = invariantFindings.size + terminologyFindings.size
    }
    fun audit(
        source: String,
        target: String,
        glossary: List<OfflineGlossaryAudit.Entry>
    ): AuditReport = AuditReport(
        invariantFindings = TranslationInvariantAudit.audit(source, target),
        terminologyFindings = OfflineGlossaryAudit.audit(source, target, glossary)
    )
    fun rankMemory(
        query: String,
        memories: List<OfflineMemoryRanker.Item>,
        domain: String? = null,
        limit: Int = 5
    ): List<OfflineMemoryRanker.Match> = OfflineMemoryRanker.rank(query, memories, domain, limit)
    fun scheduleReview(
        previous: AdaptiveReviewScheduler.State,
        quality: Int,
        nowEpochMs: Long
    ): AdaptiveReviewScheduler.Result = AdaptiveReviewScheduler.review(previous, quality, nowEpochMs)
}
