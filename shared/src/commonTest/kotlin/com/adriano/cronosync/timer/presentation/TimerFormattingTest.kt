package com.adriano.cronosync.timer.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class TimerFormattingTest {

    @Test
    fun countdownRoundsSecondsUp() {
        assertEquals("00:05", formatCountdown(4_200L))
        assertEquals("00:01", formatCountdown(1L))
        assertEquals("00:00", formatCountdown(0L))
    }

    @Test
    fun countdownShowsHoursOnlyWhenNeeded() {
        assertEquals("59:59", formatCountdown(3_599_000L))
        assertEquals("1:00:00", formatCountdown(3_600_000L))
    }

    @Test
    fun durationFieldsRoundTripThroughMillis() {
        val fields = DurationFields(hours = 1, minutes = 2, seconds = 3)

        assertEquals(3_723_000L, fields.toMillis())
        assertEquals(fields, 3_723_000L.toDurationFields())
    }

    @Test
    fun stepWrapsAroundLikeAClock() {
        assertEquals(DurationFields(seconds = 0), DurationFields(seconds = 59).step(DurationField.Seconds, +1))
        assertEquals(DurationFields(minutes = 59), DurationFields(minutes = 0).step(DurationField.Minutes, -1))
        assertEquals(DurationFields(hours = 0), DurationFields(hours = 99).step(DurationField.Hours, +1))
    }

    @Test
    fun stepOnlyChangesTheChosenField() {
        val fields = DurationFields(hours = 1, minutes = 59, seconds = 30)

        assertEquals(DurationFields(hours = 1, minutes = 0, seconds = 30), fields.step(DurationField.Minutes, +1))
    }

    @Test
    fun overtimeRoundsSecondsDown() {
        assertEquals("-00:00", formatOvertime(999L))
        assertEquals("-00:12", formatOvertime(12_400L))
        assertEquals("-1:00:00", formatOvertime(3_600_000L))
    }
}
