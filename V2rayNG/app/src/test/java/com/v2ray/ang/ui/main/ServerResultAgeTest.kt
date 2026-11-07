package com.v2ray.ang.ui.main

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ServerResultAgeTest {
    @Test
    fun untestedNeverShowsAnAge() {
        assertNull(serverResultAge(0, 0, 1_000))
        assertNull(serverResultAge(0, 500, 1_000))
    }

    @Test
    fun missingOrFutureTimestampsHaveUnknownAge() {
        assertEquals(ResultAge(ResultAgeUnit.UNKNOWN), serverResultAge(100, 0, 1_000))
        assertEquals(ResultAge(ResultAgeUnit.UNKNOWN), serverResultAge(-1, -1, 1_000))
        assertEquals(ResultAge(ResultAgeUnit.UNKNOWN), serverResultAge(100, 2_000, 1_000))
        assertEquals(ResultAge(ResultAgeUnit.UNKNOWN), serverResultAge(100, 100, 0))
    }

    @Test
    fun ageChangesAtMinuteHourAndDayBoundariesForSuccessAndFailure() {
        val base = 1_000L
        assertEquals(ResultAge(ResultAgeUnit.MINUTES, 0), serverResultAge(90, base, base))
        assertEquals(ResultAge(ResultAgeUnit.MINUTES, 0), serverResultAge(90, base, base + 59_999L))
        assertEquals(ResultAge(ResultAgeUnit.MINUTES, 1), serverResultAge(90, base, base + 60_000L))
        assertEquals(ResultAge(ResultAgeUnit.MINUTES, 59), serverResultAge(-1, base, base + 3_599_999L))
        assertEquals(ResultAge(ResultAgeUnit.HOURS, 1), serverResultAge(-1, base, base + 3_600_000L))
        assertEquals(ResultAge(ResultAgeUnit.HOURS, 23), serverResultAge(90, base, base + 86_399_999L))
        assertEquals(ResultAge(ResultAgeUnit.DAYS, 1), serverResultAge(90, base, base + 86_400_000L))
    }
}
