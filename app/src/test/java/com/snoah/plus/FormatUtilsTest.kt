package com.snoah.plus

import com.snoah.plus.util.FormatUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatUtilsTest {
    @Test
    fun formatsZero() {
        assertEquals("0:00", FormatUtils.formatDuration(0))
    }

    @Test
    fun formatsMinutesAndSeconds() {
        assertEquals("3:05", FormatUtils.formatDuration(185_000))
    }

    @Test
    fun formatsOneHourAsMinutes() {
        assertEquals("61:00", FormatUtils.formatDuration(3_660_000))
    }

    @Test
    fun initialsFromTwoWords() {
        assertEquals("SM", FormatUtils.initials("Shahzaib Muzaffar"))
    }
}
