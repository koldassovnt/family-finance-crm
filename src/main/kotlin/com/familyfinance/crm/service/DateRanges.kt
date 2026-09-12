package com.familyfinance.crm.service

import com.familyfinance.crm.exception.ValidationException
import com.familyfinance.crm.exception.invalidField
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException

/** Inclusive date window. There is no pagination — this is what bounds a list. */
data class DateRange(
    val from: LocalDate,
    val to: LocalDate,
)

const val MAX_HISTORY_YEARS = 1L

fun historyRange(
    from: LocalDate,
    to: LocalDate,
): DateRange {
    if (to.isBefore(from)) {
        throw ValidationException(
            "'to' must not be before 'from'",
            mapOf("to" to "must not be before 'from'"),
        )
    }
    if (from.plusYears(MAX_HISTORY_YEARS).isBefore(to)) {
        throw ValidationException(
            "Date range must not exceed $MAX_HISTORY_YEARS year",
            mapOf("to" to "must be at most $MAX_HISTORY_YEARS year after 'from'"),
        )
    }
    return DateRange(from = from, to = to)
}

/** Parses `2026-09` into that month's inclusive bounds. */
fun monthRange(month: YearMonth): DateRange = DateRange(from = month.atDay(1), to = month.atEndOfMonth())

fun parseMonth(month: String): YearMonth =
    try {
        YearMonth.parse(month)
    } catch (ex: DateTimeParseException) {
        throw invalidField("month", "must be in yyyy-MM format, e.g. 2026-09")
    }
