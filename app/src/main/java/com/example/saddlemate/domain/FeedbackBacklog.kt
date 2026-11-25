package com.example.saddlemate.domain

enum class Severity { LOW, MEDIUM, HIGH }

/** A rider's voice pin or report about a trail. */
data class FeedbackItem(
    val id: String,
    val text: String,
    val severity: Severity,
    val reportCount: Int,
)

/** Ranks feedback so the most urgent, most reported issues come first. */
object FeedbackBacklog {

    fun rank(items: List<FeedbackItem>): List<FeedbackItem> =
        items.sortedWith(
            compareByDescending<FeedbackItem> { it.severity.weight() }
                .thenByDescending { it.reportCount },
        )

    private fun Severity.weight(): Int = when (this) {
        Severity.HIGH -> 3
        Severity.MEDIUM -> 2
        Severity.LOW -> 1
    }
}
