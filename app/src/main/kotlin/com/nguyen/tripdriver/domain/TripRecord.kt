/*
 * Copyright (c) 2026 Nguyên. All rights reserved.
 */

package com.nguyen.tripdriver.domain

import java.time.LocalDate
import java.time.YearMonth

/**
 * A completed trip saved in local storage.
 */
data class TripRecord(
  val id: Long = 0L,
  val vehicleType: VehicleType = VehicleType.MOTORBIKE,
  val startTimeMillis: Long,
  val endTimeMillis: Long,
  val activeDurationMillis: Long,
  val movingDurationMillis: Long,
  val restDurationMillis: Long = 0L,
  val distanceMeters: Double,
  val maxSpeedKmh: Double,
  val isManualEntry: Boolean = false,
) {
  val distanceKm: Double
    get() = distanceMeters / 1000.0

  val avgSpeedKmh: Double
    get() {
      val hours = activeDurationMillis / 3_600_000.0
      return if (hours > 0.0001) distanceKm / hours else 0.0
    }
}

/**
 * Aggregated metrics for a single calendar date.
 */
data class DailySummary(
  val date: LocalDate,
  val totalDistanceMeters: Double,
  val totalDurationMillis: Long,
  val totalRestDurationMillis: Long,
  val trips: List<TripRecord>,
) {
  val tripCount: Int
    get() = trips.size

  val totalDistanceKm: Double
    get() = totalDistanceMeters / 1000.0

  val avgSpeedKmh: Double
    get() {
      val hours = totalDurationMillis / 3_600_000.0
      return if (hours > 0.0001) totalDistanceKm / hours else 0.0
    }
}

/**
 * Aggregated metrics for a full calendar month.
 */
data class MonthlySummary(
  val yearMonth: YearMonth,
  val totalDistanceMeters: Double,
  val totalDurationMillis: Long,
  val totalRestDurationMillis: Long,
  val totalTrips: Int,
  val activeDaysCount: Int,
  val longestTripMeters: Double,
  val dailySummaries: List<DailySummary>,
) {
  val totalDistanceKm: Double
    get() = totalDistanceMeters / 1000.0

  val avgDistancePerActiveDayMeters: Double
    get() = if (activeDaysCount > 0) totalDistanceMeters / activeDaysCount else 0.0
}
