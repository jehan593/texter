package com.texter.app.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val TIME_OF_DAY = DateTimeFormatter.ofPattern("HH:mm")
private val OLDER_DATE = DateTimeFormatter.ofPattern("MMM d, HH:mm")

fun formatLastEdited(millis: Long, now: ZonedDateTime = ZonedDateTime.now()): String {
    val then = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    val minutes = ChronoUnit.MINUTES.between(then, now)
    val dayDiff = ChronoUnit.DAYS.between(then.toLocalDate(), now.toLocalDate())
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        dayDiff == 0L -> "Today, ${then.format(TIME_OF_DAY)}"
        dayDiff == 1L -> "Yesterday, ${then.format(TIME_OF_DAY)}"
        else -> then.format(OLDER_DATE)
    }
}
