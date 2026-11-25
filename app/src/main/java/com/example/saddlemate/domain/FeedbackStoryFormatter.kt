package com.example.saddlemate.domain

/**
 * Turns recorded rider feedback notes into structured User Stories / Backlog tickets
 * so admins can review, prioritize, and export them to a ticket system.
 * Pure logic, unit-testable.
 */
object FeedbackStoryFormatter {

    data class FeedbackNoteInput(
        val id: Long,
        val text: String,
        val createdAtMillis: Long,
        val status: String,
        val priority: String = "MEDIUM",
        val userStory: String = "",
    )

    /** Builds a User Story from a raw note (voice or typed). */
    fun buildUserStory(text: String): String {
        val clause = asWishClause(text)
        return "Als Reiter möchte ich $clause, damit der Ausritt einfacher und sicherer wird."
    }

    /** Suggests an initial admin priority from keywords ("dringend", "stürzt", "wichtig"...). */
    fun suggestPriority(text: String): String {
        val l = text.lowercase()
        return when {
            l.contains("stürzt") || l.contains("fehler") || l.contains("bug") ||
                l.contains("wichtig") || l.contains("dringend") || l.contains("sofort") -> "HIGH"
            l.contains("irgendwann") || l.contains("nice") || l.contains("klein") -> "LOW"
            else -> "MEDIUM"
        }
    }

    fun toMarkdownBacklog(notes: List<FeedbackNoteInput>): String {
        if (notes.isEmpty()) return "# SaddleMate Feedback-Backlog\n\nKeine Einträge vorhanden.\n"
        val sorted = notes.sortedWith(
            compareByDescending<FeedbackNoteInput> { priorityWeight(it.priority) }
                .thenByDescending { it.createdAtMillis },
        )
        val sb = StringBuilder()
        sb.appendLine("# SaddleMate Feedback-Backlog (${sorted.size} Tickets)")
        sb.appendLine()
        sorted.forEachIndexed { idx, note ->
            val category = guessCategory(note.text)
            val story = note.userStory.ifBlank { buildUserStory(note.text) }
            sb.appendLine("## TICKET-${note.id}: ${shortTitle(note.text)}")
            sb.appendLine("- **Priorität:** ${note.priority} · **Status:** ${note.status} · **Kategorie:** $category")
            sb.appendLine("- **User Story:** $story")
            sb.appendLine("- **Originalnotiz:** \"${note.text.trim()}\"")
            if (idx < sorted.lastIndex) sb.appendLine()
        }
        return sb.toString()
    }

    private fun priorityWeight(priority: String): Int = when (priority) {
        "HIGH" -> 3
        "MEDIUM" -> 2
        else -> 1
    }

    private fun guessCategory(text: String): String {
        val l = text.lowercase()
        return when {
            l.contains("fehler") || l.contains("stürzt") || l.contains("bug") || l.contains("geht nicht") -> "Bugfix"
            l.contains("karte") || l.contains("route") || l.contains("navi") -> "Karte & Navigation"
            l.contains("pferd") || l.contains("kalorien") || l.contains("profil") -> "Profil & Gesundheit"
            else -> "Feature-Wunsch"
        }
    }

    private fun shortTitle(text: String): String {
        val clean = text.trim().replace('\n', ' ')
        return if (clean.length <= 55) clean else clean.take(52).trimEnd() + "…"
    }

    private fun asWishClause(text: String): String {
        val clean = text.trim().replace('\n', ' ').trimEnd('.')
        if (clean.isEmpty()) return "eine Verbesserung nutzen"
        return clean.replaceFirstChar { it.lowercase() }
    }
}
