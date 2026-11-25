package com.example.saddlemate.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackStoryFormatterTest {

    @Test
    fun formatsWishAsUserStoryWithCategory() {
        val md = FeedbackStoryFormatter.toMarkdownBacklog(
            listOf(
                FeedbackStoryFormatter.FeedbackNoteInput(
                    id = 42L,
                    text = "Karte beim Zoomen schärfer machen",
                    createdAtMillis = 0L,
                    status = "NEW",
                    priority = "HIGH",
                ),
            ),
        )
        assertTrue(md.contains("TICKET-42"))
        assertTrue(md.contains("Karte & Navigation"))
        assertTrue(md.contains("Priorität:** HIGH"))
        assertTrue(md.contains("Als Reiter möchte ich karte beim Zoomen schärfer machen"))
    }

    @Test
    fun suggestsHighPriorityForUrgentOrCrashFeedback() {
        assertEquals("HIGH", FeedbackStoryFormatter.suggestPriority("App stürzt beim Ritt ab"))
        assertEquals("HIGH", FeedbackStoryFormatter.suggestPriority("Bitte dringend einbauen"))
        assertEquals("LOW", FeedbackStoryFormatter.suggestPriority("Kleine Änderung irgendwann"))
        assertEquals("MEDIUM", FeedbackStoryFormatter.suggestPriority("Farben etwas heller"))
    }

    @Test
    fun sortsExportedBacklogByAdminPriorityFirst() {
        val md = FeedbackStoryFormatter.toMarkdownBacklog(
            listOf(
                FeedbackStoryFormatter.FeedbackNoteInput(1L, "Niedrig", 200L, "NEW", priority = "LOW"),
                FeedbackStoryFormatter.FeedbackNoteInput(2L, "Hoch", 100L, "NEW", priority = "HIGH"),
            ),
        )
        assertTrue(md.indexOf("TICKET-2") < md.indexOf("TICKET-1"))
    }
}
