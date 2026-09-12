package com.familyfinance.crm.service

import com.familyfinance.crm.exception.ValidationException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals

class DateRangesTest {
    @Test
    fun `accepts a range of exactly one year`() {
        val range = historyRange(from = LocalDate.of(2025, 9, 10), to = LocalDate.of(2026, 9, 10))

        assertEquals(LocalDate.of(2026, 9, 10), range.to)
    }

    @Test
    fun `rejects a range longer than a year`() {
        assertThrows<ValidationException> {
            historyRange(from = LocalDate.of(2025, 9, 10), to = LocalDate.of(2026, 9, 11))
        }
    }

    @Test
    fun `rejects an inverted range`() {
        assertThrows<ValidationException> {
            historyRange(from = LocalDate.of(2026, 9, 10), to = LocalDate.of(2026, 9, 9))
        }
    }

    @Test
    fun `bounds a month inclusively`() {
        val range = monthRange(YearMonth.of(2026, 2))

        assertEquals(LocalDate.of(2026, 2, 1), range.from)
        assertEquals(LocalDate.of(2026, 2, 28), range.to)
    }

    @Test
    fun `rejects a month that is not yyyy-MM`() {
        assertThrows<ValidationException> { parseMonth("September 2026") }
    }
}
