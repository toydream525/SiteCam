package com.sitecam.app.feature.gallery

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class CaptureDateRangeTest {
    @Test fun leapMonthAndYearIncludeLastDay() {
        assertEquals(LocalDate.of(2024,2,29), CaptureDateRange.parse("2024-02", "").endInclusive)
        assertEquals(LocalDate.of(2025,12,31), CaptureDateRange.parse("2025", "").endInclusive)
    }
    @Test fun rangeUsesLocalCaptureDateAndInclusiveEnd() {
        val range = CaptureDateRange.parse("2025-12-31", "2026-01-01")
        val zone = ZoneId.of("Asia/Shanghai")
        assertTrue(range.contains(Instant.parse("2026-01-01T15:59:59Z").toEpochMilli(), zone))
        assertFalse(range.contains(Instant.parse("2026-01-01T16:00:00Z").toEpochMilli(), zone))
        assertFalse(range.contains(Instant.parse("2025-12-30T15:59:59Z").toEpochMilli(), zone))
    }
    @Test(expected=IllegalArgumentException::class) fun reversedDatesAreRejected() { CaptureDateRange.parse("2026-09", "2025") }
}
