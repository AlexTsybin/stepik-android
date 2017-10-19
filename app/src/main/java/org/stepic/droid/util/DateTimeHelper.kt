package org.stepic.droid.util

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

object DateTimeHelper {

    private val isoPattern = "yyyy-MM-dd'T'HH:mm:ssZ"
    private val millisecondsInHour = 1000 * 60 * 60
    private val millisecondsInMinute = 1000 * 60
    private val hoursInDay = 24

    fun hourMinutesOfMidnightDiffWithUtc(timeZone: TimeZone, isDaylight: Boolean): String {
        val instance = Calendar.getInstance(timeZone)
        var diff = instance.timeZone.rawOffset
        if (isDaylight) {
            val daylightOffset = instance.get(Calendar.DST_OFFSET)
            diff += daylightOffset
        }

        val notNegative = diff >= 0
        if (!notNegative) {
            diff *= -1
        }
        var hours = diff / millisecondsInHour
        val minutes = (diff % millisecondsInHour) / millisecondsInMinute
        return if (notNegative) {
            String.format("%02d:%02d", hours, minutes)
        } else {
            if (minutes > 0) {
                hours += 1
            }
            String.format("%02d:%02d", hoursInDay - hours, minutes)
        }
    }

    fun getPrintableOfIsoDate(dateInISOFormat: String?, pattern: String, timeZone: TimeZone): String {
        if (dateInISOFormat == null) return ""
        val date = getDateOfIso(dateInISOFormat)

        val finalDateFormat = SimpleDateFormat(pattern, Locale.getDefault())
        finalDateFormat.timeZone = timeZone

        return finalDateFormat.format(date)
    }


    fun isNeededUpdate(timestampStored: Long, deltaInMillis: Long = AppConstants.MILLIS_IN_24HOURS): Boolean {
        //delta is 24 hours by default
        if (timestampStored == -1L) return true

        val nowTemp = nowUtc()
        val delta = nowTemp - timestampStored
        return delta > deltaInMillis
    }

    fun nowLocal(): Long = calendarToLocalMillis(Calendar.getInstance())

    fun calendarToLocalMillis(calendar: Calendar): Long {
        val isDaylight = TimeZone.getDefault().inDaylightTime(calendar.time)

        return calendar.timeInMillis +
                calendar.timeZone.rawOffset +
                if (isDaylight) {
                    calendar.get(Calendar.DST_OFFSET)
                } else {
                    0
                }
    }

    fun nowUtc(): Long = Calendar.getInstance().timeInMillis

    fun isAfterNowUtc(yourMillis: Long): Boolean = yourMillis > nowUtc()

    fun isBeforeNowUtc(yourMillis: Long): Boolean = yourMillis < nowUtc()


    /**
     * Transform ISO 8601 string to Calendar.
     * Helper method for handling a most common subset of ISO 8601 strings
     * (in the following format: "2008-03-01T13:00:00+01:00"). It supports
     * parsing the "Z" timezone, but many other less-used features are
     * missing.
     */
    fun toCalendar(iso8601string: String, timeZone: TimeZone = TimeZone.getTimeZone("UTC")): Calendar {
        val calendar = GregorianCalendar.getInstance()
        val date = getDateOfIso(iso8601string)
        calendar.time = date
        calendar.timeZone = timeZone
        return calendar
    }


    private fun getDateOfIso(iso8601string: String): Date {
        var s = iso8601string.replace("Z", "+00:00")
        try {
            s = s.substring(0, 22) + s.substring(23)  // to get rid of the ":"
        } catch (e: IndexOutOfBoundsException) {
            throw ParseException("Invalid length", 0)
        }
        val dateFormat = SimpleDateFormat(isoPattern, Locale.getDefault())
        return dateFormat.parse(s)
    }

}
