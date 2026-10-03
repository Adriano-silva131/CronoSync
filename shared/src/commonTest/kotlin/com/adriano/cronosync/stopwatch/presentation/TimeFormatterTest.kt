package com.adriano.cronosync.stopwatch.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class TimeFormatterTest {

    @Test
    fun formatsZero() = assertEquals("00:00.00", formatElapsed(0L))

    @Test
    fun truncatesToCentiseconds() = assertEquals("00:01.23", formatElapsed(1_239L))

    @Test
    fun formatsMinutes() = assertEquals("12:34.56", formatElapsed(754_560L))

    @Test
    fun showsHoursOnlyWhenNeeded() = assertEquals("1:02:03.04", formatElapsed(3_723_040L))

    @Test
    fun treatsNegativeAsZero() = assertEquals("00:00.00", formatElapsed(-500L))
}
