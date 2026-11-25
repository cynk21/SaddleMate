package com.example.saddlemate.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PinClassifierTest {

    @Test
    fun classifiesCommonTrailKeywords() {
        assertEquals(MarkIcon.DOG, PinClassifier.classify("Hund hinter dem Zaun"))
        assertEquals(MarkIcon.GATE, PinClassifier.classify("Schranke am Waldweg klemmt"))
        assertEquals(MarkIcon.WATER, PinClassifier.classify("Kleine Wasserstelle nach der Brücke"))
        assertEquals(MarkIcon.ROAD_CROSSING, PinClassifier.classify("Achtung Straßenquerung"))
        assertEquals(MarkIcon.BLIND_TURN, PinClassifier.classify("Unübersichtliche Kurve rechts"))
        assertEquals(MarkIcon.LOOSE_HORSE, PinClassifier.classify("Freilaufendes Pferd auf der Wiese"))
        assertEquals(MarkIcon.NOTE, PinClassifier.classify("Vorsicht tiefer Ast"))
    }

    @Test
    fun fallsBackToVoicePinWhenNoKeywordMatches() {
        assertEquals(MarkIcon.VOICE_PIN, PinClassifier.classify("Schöne Aussicht hier"))
    }
}
