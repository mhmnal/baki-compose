package app.baki

import kotlinx.datetime.LocalDate

/** Strict ledger date format. Invalid dates must never enter the store. */
fun isValidIsoDate(value: String): Boolean =
    runCatching { LocalDate.parse(value) }.isSuccess

/** 24-hour HH:MM, as used by shift start/end inputs. */
fun isValidClock(value: String): Boolean {
    val parts = value.split(':')
    if (parts.size != 2 || parts[0].length != 2 || parts[1].length != 2) return false
    val hour = parts[0].toIntOrNull() ?: return false
    val minute = parts[1].toIntOrNull() ?: return false
    return hour in 0..23 && minute in 0..59
}
