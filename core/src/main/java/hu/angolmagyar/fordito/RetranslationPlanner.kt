package hu.angolmagyar.fordito

/**
 * Safe retranslation planning primitives. Planning is separate from execution: this API
 * produces a preview only. A caller must explicitly approve a change before applying it.
 */
object RetranslationPlanner {
    data class Segment(
        val id: String,
        val sourceText: String,
        val currentTranslation: String,
        val manuallyApproved: Boolean = false,
        val sourceRevision: Int = 1,
        val translationRevision: Int = 1
    )

    enum class Reason { GLOSSARY_CHANGED, SOURCE_CHANGED, PROVIDER_CHANGED, STYLE_CHANGED, MANUAL_REQUEST }

    data class ProposedChange(
        val segmentId: String,
        val before: String,
        val after: String,
        val reason: Reason,
        val protectedManualText: Boolean,
        val sourceRevision: Int,
        val translationRevision: Int
    )

    data class Preview(
        val changes: List<ProposedChange>,
        val skippedManualSegments: List<String>,
        val requiresExplicitApproval: Boolean = true
    ) {
        val changedCount: Int get() = changes.size
        val isEmpty: Boolean get() = changes.isEmpty()
    }

    /** Builds a preview only. Unknown candidate IDs and blank candidates are ignored. */
    fun preview(
        segments: List<Segment>,
        candidates: Map<String, String>,
        reason: Reason,
        forceIncludeManual: Boolean = false
    ): Preview {
        val changes = mutableListOf<ProposedChange>()
        val skipped = mutableListOf<String>()
        segments.forEach { segment ->
            val candidate = candidates[segment.id]?.trim() ?: return@forEach
            if (candidate.isEmpty() || candidate == segment.currentTranslation.trim()) return@forEach
            if (segment.manuallyApproved && !forceIncludeManual) {
                skipped += segment.id
                return@forEach
            }
            changes += ProposedChange(
                segmentId = segment.id,
                before = segment.currentTranslation,
                after = candidate,
                reason = reason,
                protectedManualText = segment.manuallyApproved,
                sourceRevision = segment.sourceRevision,
                translationRevision = segment.translationRevision
            )
        }
        return Preview(changes, skipped)
    }

    /** Applies only explicitly selected IDs whose translation revision still matches the preview. */
    fun applyApproved(
        segments: List<Segment>,
        preview: Preview,
        approvedSegmentIds: Set<String>
    ): List<Segment> {
        val byId = preview.changes.associateBy { it.segmentId }
        return segments.map { segment ->
            val change = byId[segment.id] ?: return@map segment
            if (segment.id !in approvedSegmentIds) return@map segment
            if (segment.translationRevision != change.translationRevision) return@map segment
            if (segment.manuallyApproved && !change.protectedManualText) return@map segment
            segment.copy(
                currentTranslation = change.after,
                manuallyApproved = false,
                translationRevision = segment.translationRevision + 1
            )
        }
    }

    /** Compact line-oriented diff; the content is treated as plain text. */
    fun diff(change: ProposedChange): String = buildString {
        append("--- ").append(change.segmentId).append(" (előtte)\n")
        append("- ").append(change.before).append('\n')
        append("+++ ").append(change.segmentId).append(" (javaslat)\n")
        append("+ ").append(change.after).append('\n')
    }
}
