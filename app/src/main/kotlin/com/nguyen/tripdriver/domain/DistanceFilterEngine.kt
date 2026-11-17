/*
 * Copyright (c) 2026 Nguyên. All rights reserved.
 */

package com.nguyen.tripdriver.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure GPS sample input independent of Android's Location class so filtering is deterministic and testable.
 */
data class GpsSample(
  val latitude: Double,
  val longitude: Double,
  val accuracyMeters: Float,
  val speedMetersPerSec: Float?,
  val timestampMillis: Long,
)

enum class SampleDecision {
  ANCHORED,
  ACCEPTED,
  IGNORED_INVALID_COORDS,
  IGNORED_POOR_ACCURACY,
  IGNORED_STALE_TIMESTAMP,
  IGNORED_DRIFT,
  IGNORED_TELEPORT_SPIKE,
}

data class FilterResult(
  val decision: SampleDecision,
  val addedDistanceMeters: Double,
  val addedMovingMillis: Long,
  val currentSpeedKmh: Double,
)

/**
 * Filters noisy GPS fixes (stationary drift at traffic lights, poor accuracy indoors,
 * and teleport spikes) while accumulating realistic motorbike distance and moving duration.
 */
class DistanceFilterEngine(
  private val maxAcceptableAccuracyMeters: Float = DEFAULT_MAX_ACCURACY_METERS,
  private val minMovingSpeedMps: Float = DEFAULT_MIN_MOVING_SPEED_MPS,
  private val maxRealisticSpeedMps: Float = DEFAULT_MAX_REALISTIC_SPEED_MPS,
  private val baseMinStepMeters: Double = DEFAULT_BASE_MIN_STEP_METERS,
) {
  private var lastAcceptedSample: GpsSample? = null
  var totalDistanceMeters: Double = 0.0
    private set
  var movingDurationMillis: Long = 0L
    private set
  var maxSpeedKmh: Double = 0.0
    private set

  fun restoreState(
    distanceMeters: Double,
    movingMillis: Long,
    maxSpeedKmh: Double,
  ) {
    totalDistanceMeters = distanceMeters.coerceAtLeast(0.0)
    movingDurationMillis = movingMillis.coerceAtLeast(0L)
    this.maxSpeedKmh = maxSpeedKmh.coerceAtLeast(0.0)
    lastAcceptedSample = null
  }

  /**
   * Clears the spatial anchor (for example after Pause/Resume) so resuming from a different spot
   * does not connect a straight line across paused time.
   */
  fun resetAnchor() {
    lastAcceptedSample = null
  }

  fun processSample(sample: GpsSample): FilterResult {
    if (!isValidCoordinate(sample.latitude, sample.longitude)) {
      return FilterResult(SampleDecision.IGNORED_INVALID_COORDS, 0.0, 0L, 0.0)
    }
    if (sample.accuracyMeters <= 0f || sample.accuracyMeters > maxAcceptableAccuracyMeters) {
      return FilterResult(SampleDecision.IGNORED_POOR_ACCURACY, 0.0, 0L, 0.0)
    }

    val previous = lastAcceptedSample
    if (previous == null) {
      lastAcceptedSample = sample
      val speedKmh = normalizeReportedSpeedKmh(sample.speedMetersPerSec)
      if (speedKmh > maxSpeedKmh) maxSpeedKmh = speedKmh
      return FilterResult(SampleDecision.ANCHORED, 0.0, 0L, speedKmh)
    }

    val deltaMillis = sample.timestampMillis - previous.timestampMillis
    if (deltaMillis <= 0L) {
      return FilterResult(SampleDecision.IGNORED_STALE_TIMESTAMP, 0.0, 0L, 0.0)
    }

    val deltaSeconds = deltaMillis / 1000.0
    val distanceMeters = haversineMeters(
      previous.latitude,
      previous.longitude,
      sample.latitude,
      sample.longitude,
    )
    val impliedSpeedMps = distanceMeters / deltaSeconds

    if (impliedSpeedMps > maxRealisticSpeedMps || (deltaSeconds <= 2.0 && distanceMeters > 150.0)) {
      return FilterResult(SampleDecision.IGNORED_TELEPORT_SPIKE, 0.0, 0L, 0.0)
    }

    val reportedSpeedMps = sample.speedMetersPerSec?.takeIf { it >= 0f && it <= maxRealisticSpeedMps }
    val usableMovingReportedSpeed = reportedSpeedMps?.takeIf { it >= minMovingSpeedMps }
    val effectiveSpeedMps = usableMovingReportedSpeed?.toDouble() ?: impliedSpeedMps

    val jitterFloorMeters = max(baseMinStepMeters, sample.accuracyMeters * 0.45)
    val isStationaryBySpeed = reportedSpeedMps != null && reportedSpeedMps < minMovingSpeedMps
    val isMovingImplied = impliedSpeedMps >= minMovingSpeedMps

    if (distanceMeters < jitterFloorMeters && (isStationaryBySpeed || !isMovingImplied)) {
      return FilterResult(SampleDecision.IGNORED_DRIFT, 0.0, 0L, 0.0)
    }

    if (distanceMeters < baseMinStepMeters && effectiveSpeedMps < minMovingSpeedMps) {
      return FilterResult(SampleDecision.IGNORED_DRIFT, 0.0, 0L, 0.0)
    }

    totalDistanceMeters += distanceMeters
    val addedMovingMillis = if (deltaMillis <= 15_000L) deltaMillis else 0L
    movingDurationMillis += addedMovingMillis

    val speedKmh = (effectiveSpeedMps * 3.6).coerceAtMost(maxRealisticSpeedMps * 3.6)
    if (deltaSeconds >= 0.8 || usableMovingReportedSpeed != null) {
      if (speedKmh > maxSpeedKmh) {
        maxSpeedKmh = speedKmh
      }
    }
    lastAcceptedSample = sample

    return FilterResult(
      decision = SampleDecision.ACCEPTED,
      addedDistanceMeters = distanceMeters,
      addedMovingMillis = addedMovingMillis,
      currentSpeedKmh = speedKmh,
    )
  }

  private fun normalizeReportedSpeedKmh(speedMps: Float?): Double {
    if (speedMps == null || speedMps < minMovingSpeedMps || speedMps > maxRealisticSpeedMps) {
      return 0.0
    }
    return speedMps * 3.6
  }

  companion object {
    const val DEFAULT_MAX_ACCURACY_METERS = 25f
    const val DEFAULT_MIN_MOVING_SPEED_MPS = 1.2f
    const val DEFAULT_MAX_REALISTIC_SPEED_MPS = 45f
    const val DEFAULT_BASE_MIN_STEP_METERS = 5.0
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun isValidCoordinate(lat: Double, lon: Double): Boolean {
      if (lat.isNaN() || lon.isNaN() || lat.isInfinite() || lon.isInfinite()) return false
      if (lat == 0.0 && lon == 0.0) return false
      return lat in -90.0..90.0 && lon in -180.0..180.0
    }

    fun haversineMeters(
      lat1: Double,
      lon1: Double,
      lat2: Double,
      lon2: Double,
    ): Double {
      val dLat = Math.toRadians(lat2 - lat1)
      val dLon = Math.toRadians(lon2 - lon1)
      val rLat1 = Math.toRadians(lat1)
      val rLat2 = Math.toRadians(lat2)

      val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(rLat1) * cos(rLat2) * sin(dLon / 2) * sin(dLon / 2)
      val c = 2 * atan2(sqrt(a), sqrt(1 - a))
      return EARTH_RADIUS_METERS * c
    }
  }
}
