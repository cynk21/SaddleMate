package com.example.saddlemate.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationGuidanceTest {

    private val start = GeoPoint(52.0, 14.0, 0L)
    private val corner = GeoPoint(52.0 + 300.0 / 111_320.0, 14.0, 0L)
    private val end = GeoPoint(
        corner.lat,
        14.0 + 300.0 / (111_320.0 * kotlin.math.cos(Math.toRadians(52.0))),
        0L,
    )

    /** 300 m north, then 300 m east: a right turn at the corner. */
    private val rightTurnRoute = listOf(start, corner, end)

    @Test
    fun riderOnRoute_hasNoOffRouteDistanceAndRemainingIsCorrect() {
        val rider = GeoPoint(52.0 + 100.0 / 111_320.0, 14.0, 0L)
        val s = NavigationGuidance.status(rightTurnRoute, rider)!!
        assertEquals(0.0, s.offRouteM, 1.0)
        assertEquals(500.0, s.remainingM, 2.0)
    }

    @Test
    fun riderBesideRoute_isOffRouteByTheDistance() {
        // About 100 m west of the start, which is the start of the route.
        val west = GeoPoint(52.0, 14.0 - 100.0 / (111_320.0 * kotlin.math.cos(Math.toRadians(52.0))), 0L)
        val s = NavigationGuidance.status(rightTurnRoute, west)!!
        assertEquals(100.0, s.offRouteM, 2.0)
    }

    @Test
    fun rightAngleCorner_isAnnouncedAsRightTurnAhead() {
        val s = NavigationGuidance.status(rightTurnRoute, start)!!
        assertNotNull(s.nextTurn)
        val turn = s.nextTurn!!
        assertEquals(TurnSide.RIGHT, turn.side)
        assertEquals(300.0, turn.inM, 2.0)
    }

    @Test
    fun straightRoute_hasNoTurn() {
        val straight = listOf(
            start,
            GeoPoint(52.0 + 150.0 / 111_320.0, 14.0, 0L),
            GeoPoint(52.0 + 300.0 / 111_320.0, 14.0, 0L),
        )
        val s = NavigationGuidance.status(straight, start)!!
        assertNull(s.nextTurn)
    }

    @Test
    fun routeWithFewerThanTwoPoints_hasNoStatus() {
        assertNull(NavigationGuidance.status(listOf(start), start))
    }

    /** Starts a route at 500 m remaining and moves 100 m, so the coach counts the rider as moving. */
    private fun movedCoach(): NavigationGuidance.Coach {
        val coach = NavigationGuidance.Coach()
        coach.messages(NavStatus(offRouteM = 0.0, remainingM = 500.0, nextTurn = null))
        coach.messages(NavStatus(offRouteM = 0.0, remainingM = 400.0, nextTurn = null))
        return coach
    }

    @Test
    fun coach_staysSilentUntilTheRiderMoves() {
        val coach = NavigationGuidance.Coach()
        val near = NavStatus(offRouteM = 0.0, remainingM = 10.0, nextTurn = Turn(TurnSide.LEFT, 10.0, 20.0))
        assertTrue(coach.messages(near).isEmpty())
        assertTrue(coach.messages(near).isEmpty())
    }

    @Test
    fun coach_announcesOffRouteOnceAndBackOnRoute() {
        val coach = movedCoach()
        val off = NavStatus(offRouteM = 80.0, remainingM = 380.0, nextTurn = null)
        val on = NavStatus(offRouteM = 5.0, remainingM = 380.0, nextTurn = null)
        assertTrue(coach.messages(off).any { it.contains("abgekommen") })
        assertTrue(coach.messages(off).isEmpty())
        assertTrue(coach.messages(on).any { it.contains("Wieder auf der Route") })
    }

    @Test
    fun coach_announcesTurnOnceWithSide() {
        val coach = movedCoach()
        val turn = Turn(TurnSide.RIGHT, inM = 120.0, atM = 300.0)
        val near = NavStatus(offRouteM = 0.0, remainingM = 380.0, nextTurn = turn)
        assertTrue(coach.messages(near).any { it.contains("rechts") && it.contains("abbiegen") })
        assertTrue(coach.messages(near).none { it.contains("abbiegen") })
    }

    @Test
    fun coach_announcesArrivalOnce() {
        val coach = movedCoach()
        val arrive = NavStatus(offRouteM = 0.0, remainingM = 10.0, nextTurn = null)
        assertTrue(coach.messages(arrive).any { it.contains("Ziel erreicht") })
        assertTrue(coach.messages(arrive).none { it.contains("Ziel erreicht") })
    }
}
