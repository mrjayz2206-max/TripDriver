/*
 * Copyright (c) 2026 Nguyên. All rights reserved.
 */

package com.nguyen.tripdriver.domain

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TripAggregation {

  fun localDateOf(
    epochMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
  ): LocalDate = Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()

  fun summarizeDay(
    date: LocalDate,
    trips: List<TripRecord>,
  ): DailySummary {
    val sortedTrips = trips.sortedByDescending { it.startTimeMillis }
    var totalDist = 0.0
    var totalDur = 0L
    var totalRest = 0L

    for (trip in sortedTrips) {
      totalDist += trip.distanceMeters
      totalDur += trip.activeDurationMillis
      totalRest += trip.restDurationMillis
    }

    return DailySummary(
      date = date,
      totalDistanceMeters = totalDist,
      totalDurationMillis = totalDur,
      totalRestDurationMillis = totalRest,
      trips = sortedTrips,
    )
  }

  fun summarizeMonth(
    yearMonth: YearMonth,
    tripsInMonth: List<TripRecord>,
    zoneId: ZoneId = ZoneId.systemDefault(),
  ): MonthlySummary {
    val groupedByDate = tripsInMonth.groupBy { localDateOf(it.startTimeMillis, zoneId) }
    val dailySummaries = groupedByDate.entries
      .sortedByDescending { it.key }
      .map { (date, dayTrips) -> summarizeDay(date, dayTrips) }

    var totalDist = 0.0
    var totalDur = 0L
    var totalRest = 0L
    var longestMeters = 0.0

    for (trip in tripsInMonth) {
      totalDist += trip.distanceMeters
      totalDur += trip.activeDurationMillis
      totalRest += trip.restDurationMillis
      if (trip.distanceMeters > longestMeters) {
        longestMeters = trip.distanceMeters
      }
    }

    return MonthlySummary(
      yearMonth = yearMonth,
      totalDistanceMeters = totalDist,
      totalDurationMillis = totalDur,
      totalRestDurationMillis = totalRest,
      totalTrips = tripsInMonth.size,
      activeDaysCount = dailySummaries.size,
      longestTripMeters = longestMeters,
      dailySummaries = dailySummaries,
    )
  }
}

object TripFormatters {

  fun formatStopwatch(durationMillis: Long): String {
    val totalSeconds = (durationMillis / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
  }

  /**
   * Formats durations cleanly by unit scale:
   * - < 1 minute: `34s`
   * - < 1 hour: `12m30s` (or `12m` if 0s)
   * - >= 1 hour: `1h39m29s` (omitting trailing zero units when exact)
   */
  fun formatDurationSmart(durationMillis: Long): String {
    val totalSeconds = (durationMillis / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
      hours > 0 -> buildString {
        append("${hours}h")
        if (minutes > 0 || seconds > 0) append("${minutes}m")
        if (seconds > 0) append("${seconds}s")
      }
      minutes > 0 -> if (seconds > 0) "${minutes}m${seconds}s" else "${minutes}m"
      else -> "${seconds}s"
    }
  }

  fun formatDistanceKm(
    distanceMeters: Double,
    decimals: Int = 2,
  ): String {
    val km = (distanceMeters / 1000.0).coerceAtLeast(0.0)
    return String.format(Locale.US, "%.${decimals}f", km)
  }

  fun formatSpeedKmh(speedKmh: Double): String {
    return String.format(Locale.US, "%.0f", speedKmh.coerceAtLeast(0.0))
  }

  val VIETNAMESE_LOCALE: Locale = Locale.forLanguageTag("vi-VN")

  fun formatFullDateVi(date: LocalDate, pattern: String = "EEEE, dd/MM/yyyy"): String =
    date.format(DateTimeFormatter.ofPattern(pattern, VIETNAMESE_LOCALE))
      .replaceFirstChar { it.uppercase(VIETNAMESE_LOCALE) }

  fun formatShortWeekdayVi(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("EEE", VIETNAMESE_LOCALE))
}
