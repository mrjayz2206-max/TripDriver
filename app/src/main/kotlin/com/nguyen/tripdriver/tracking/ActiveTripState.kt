/*
 * Copyright (c) 2026 Nguyên. All rights reserved.
 */

package com.nguyen.tripdriver.tracking

import com.nguyen.tripdriver.domain.VehicleType

enum class TripPhase {
  IDLE,
  TRACKING,
  PAUSED,
}

enum class GpsSignalStatus {
  INACTIVE,
  SEARCHING,
  GOOD,
  WEAK,
  PAUSED,
  LOCATION_OFF,
}

data class ActiveTripState(
  val phase: TripPhase = TripPhase.IDLE,
  val vehicleType: VehicleType = VehicleType.MOTORBIKE,
  val startTimeWallMillis: Long = 0L,
  val activeDurationMillis: Long = 0L,
  val movingDurationMillis: Long = 0L,
  val restDurationMillis: Long = 0L,
  val distanceMeters: Double = 0.0,
  val currentSpeedKmh: Double = 0.0,
  val maxSpeedKmh: Double = 0.0,
  val lastAccuracyMeters: Float? = null,
  val gpsSignalStatus: GpsSignalStatus = GpsSignalStatus.INACTIVE,
  val acceptedPointCount: Int = 0,
) {
  val isSessionActive: Boolean
    get() = phase != TripPhase.IDLE

  val avgSpeedKmh: Double
    get() {
      val hours = activeDurationMillis / 3_600_000.0
      return if (hours > 0.0001) (distanceMeters / 1000.0) / hours else 0.0
    }
}
