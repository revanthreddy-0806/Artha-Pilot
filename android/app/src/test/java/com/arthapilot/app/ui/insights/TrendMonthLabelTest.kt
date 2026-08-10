package com.arthapilot.app.ui.insights

import org.junit.Assert.assertEquals
import org.junit.Test

class TrendMonthLabelTest {

    @Test
    fun formatsMonthAndLastTwoYearDigits() {
        assertEquals("03/26", trendMonthLabel("2026-03"))
        assertEquals("12/25", trendMonthLabel("2025-12"))
        assertEquals("01/24", trendMonthLabel("2024-01"))
    }

    @Test
    fun firstTwoYearDigitsAreNeverUsed() {
        assertEquals("08/26", trendMonthLabel("2026-08"))
        assertEquals("01/20", trendMonthLabel("2020-01"))
    }

    @Test
    fun malformedInputIsReturnedAsIs() {
        assertEquals("2026", trendMonthLabel("2026"))
        assertEquals("", trendMonthLabel(""))
    }
}