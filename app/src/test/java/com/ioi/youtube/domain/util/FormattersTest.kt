package com.ioi.youtube.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    @Test
    fun `formatViews formats view counts`() {
        assertEquals("No views", Formatters.formatViews(0))
        assertEquals("No views", Formatters.formatViews(-1))
        assertEquals("999 views", Formatters.formatViews(999))
        assertEquals("1.5K views", Formatters.formatViews(1_500))
        assertEquals("12.3K views", Formatters.formatViews(12_300))
        assertEquals("1.5M views", Formatters.formatViews(1_500_000))
        assertEquals("2.3B views", Formatters.formatViews(2_300_000_000))
    }

    @Test
    fun `formatCount formats plain counts without suffix`() {
        assertEquals("0", Formatters.formatCount(-1))
        assertEquals("0", Formatters.formatCount(0))
        assertEquals("42", Formatters.formatCount(42))
        assertEquals("1.5K", Formatters.formatCount(1_500))
        assertEquals("3.0M", Formatters.formatCount(3_000_000))
    }

    @Test
    fun `formatDuration formats mm ss and h mm ss`() {
        assertEquals("0:00", Formatters.formatDuration(0))
        assertEquals("0:00", Formatters.formatDuration(-5))
        assertEquals("1:15", Formatters.formatDuration(75))
        assertEquals("12:34", Formatters.formatDuration(754))
        assertEquals("1:00:00", Formatters.formatDuration(3600))
        assertEquals("1:02:05", Formatters.formatDuration(3725))
    }
}
