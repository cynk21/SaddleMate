package com.example.saddlemate.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackPickerTest {

    private val track = listOf(
        GeoPoint(52.0, 14.0, 0L),
        GeoPoint(52.001, 14.0, 1000L),
        GeoPoint(52.002, 14.0, 2000L),
    )

    @Test
    fun emptyTrack_returnsNull() {
        assertNull(TrackPicker.nearestIndex(emptyList(), 52.0, 14.0))
    }

    @Test
    fun tapOnPoint_returnsItsIndex() {
        assertEquals(1, TrackPicker.nearestIndex(track, 52.001, 14.0))
    }

    @Test
    fun tapNearEnd_returnsLastIndex() {
        assertEquals(2, TrackPicker.nearestIndex(track, 52.0025, 14.0))
    }

    @Test
    fun tapBeforeStart_returnsFirstIndex() {
        assertEquals(0, TrackPicker.nearestIndex(track, 51.9, 14.0))
    }
}
