package com.example.moneytracker.util

import java.util.Calendar
import java.util.TimeZone

/**
 * Computes start-of-day and start-of-month epoch-millisecond timestamps
 * in the device's local time zone.
 *
 * These are recomputed on every query so the "Today" view rolls over
 * correctly at midnight without restarting the app (PROJECT_SPEC section 13).
 */
object DateRanges {

    /**
     * Returns a [LongRange] covering the current calendar day in the local
     * time zone, from 00:00:00.000 to 23:59:59.999.
     */
    fun today(): LongRange {
        val tz = TimeZone.getDefault()
        val cal = Calendar.getInstance(tz)

        // Start: midnight of today
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis

        // End: last millisecond of today
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis

        return start..end
    }

    /**
     * Returns a [LongRange] covering the current calendar month in the
     * local time zone, from the 1st at 00:00:00.000 to the last millisecond
     * of the last day of the month.
     */
    fun thisMonth(): LongRange {
        val tz = TimeZone.getDefault()
        val cal = Calendar.getInstance(tz)

        // Start: first day of month at midnight
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis

        // End: last millisecond of the last day of the month
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis

        return start..end
    }

    /**
     * Returns a [LongRange] covering the given calendar day in the local
     * time zone, from 00:00:00.000 to 23:59:59.999.
     */
    fun dayFor(epochMs: Long): LongRange {
        val tz = TimeZone.getDefault()
        val cal = Calendar.getInstance(tz).apply {
            timeInMillis = epochMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis

        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis

        return start..end
    }
}
