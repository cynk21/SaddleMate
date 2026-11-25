package com.example.saddlemate.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinProximityTest {

    private val here = GeoPoint(52.0, 14.0, 0L)
    private val near = PinProximity.Pin("near", GeoPoint(52.0002, 14.0, 0L), "Hund am Zaun")
    private val far = PinProximity.Pin("far", GeoPoint(52.01, 14.0, 0L), "Tor")

    @Test
    fun pinWithinRadius_isDue() {
        val due = PinProximity.dueNow(here, listOf(near, far), announced = emptySet())
        assertEquals(listOf("near"), due.map { it.id })
    }

    @Test
    fun pinOutsideRadius_isNotDue() {
        val due = PinProximity.dueNow(here, listOf(far), announced = emptySet())
        assertTrue(due.isEmpty())
    }

    @Test
    fun alreadyAnnounced_isNotDueAgain() {
        val due = PinProximity.dueNow(here, listOf(near), announced = setOf("near"))
        assertTrue(due.isEmpty())
    }

    @Test
    fun distance_isAboutTwentyMetresForTwoHundredthsOfALatitudeDegree() {
        // 0.0002 degrees of latitude is roughly 22 m.
        val d = PinProximity.distanceMeters(here, near.location)
        assertEquals(22.0, d, 2.0)
    }
}
