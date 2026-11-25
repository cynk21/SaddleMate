package com.example.saddlemate.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkIconsTest {

    @Test
    fun everyMarkType_hasAnIcon() {
        // Every type the rider can set by hand has its own entry.
        listOf("DOG", "BLIND_TURN", "ROAD_CROSSING", "GATE", "WATER", "LOOSE_HORSE", "NOTE").forEach { key ->
            assertEquals(key, MarkIcon.forKey(key).name)
        }
    }

    @Test
    fun unknownKey_fallsBackToNote() {
        assertEquals(MarkIcon.NOTE, MarkIcon.forKey("SOMETHING_NEW"))
    }

    @Test
    fun everyIcon_hasAColour() {
        assertTrue(MarkIcon.entries.all { it.colorHex.startsWith("#") && it.colorHex.length == 7 })
    }
}
