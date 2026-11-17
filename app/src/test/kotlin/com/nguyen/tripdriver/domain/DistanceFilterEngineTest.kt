/*
 * Copyright (c) 2026 Nguyên. All rights reserved.
 */

package com.nguyen.tripdriver.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DistanceFilterEngineTest {

  @Test
  fun firstValidPoint_anchorsWithoutAddingDistance() {
    val engine = DistanceFilterEngine()
    val result = engine.processSample(
      GpsSample(10.7769, 106.7009, accuracyMeters = 8f, speedMetersPerSec = 0f, timestampMillis = 1_000L),
    )

    assertEquals(SampleDecision.ANCHORED, result.decision)
    assertEquals(0.0, engine.totalDistanceMeters, 0.001)
  }

  @Test
  fun invalidCoordsAndPoorAccuracy_areIgnored() {
    val engine = DistanceFilterEngine()
    assertEquals(
      SampleDecision.IGNORED_INVALID_COORDS,
      engine.processSample(GpsSample(0.0, 0.0, 5f, 0f, 1_000L)).decision,
    )
    assertEquals(
      SampleDecision.IGNORED_INVALID_COORDS,
      engine.processSample(GpsSample(95.0, 106.7, 5f, 0f, 2_000L)).decision,
    )
    assertEquals(
      SampleDecision.IGNORED_POOR_ACCURACY,
      engine.processSample(GpsSample(10.7769, 106.7009, 45f, 5f, 3_000L)).decision,
    )
  }

  @Test
  fun stationaryDriftAtTrafficLight_isSuppressed() {
    val engine = DistanceFilterEngine()
    engine.processSample(GpsSample(10.776900, 106.700900, 10f, 0.2f, 10_000L))

    val driftResult = engine.processSample(
      GpsSample(10.776925, 106.700910, 12f, 0.3f, 12_000L),
    )

    assertEquals(SampleDecision.IGNORED_DRIFT, driftResult.decision)
    assertEquals(0.0, engine.totalDistanceMeters, 0.001)
    assertEquals(0L, engine.movingDurationMillis)
  }

  @Test
  fun teleportSpike_isRejected() {
    val engine = DistanceFilterEngine()
    engine.processSample(GpsSample(10.776900, 106.700900, 8f, 8f, 10_000L))

    val spike = engine.processSample(
      GpsSample(10.786900, 106.700900, 10f, 10f, 11_000L),
    )

    assertEquals(SampleDecision.IGNORED_TELEPORT_SPIKE, spike.decision)
    assertEquals(0.0, engine.totalDistanceMeters, 0.001)
  }

  @Test
  fun realisticMotorbikeRide_accumulatesDistanceAndMovingTime() {
    val engine = DistanceFilterEngine()
    engine.processSample(GpsSample(10.770000, 106.700000, 8f, 10f, 10_000L))
    val r1 = engine.processSample(GpsSample(10.770180, 106.700000, 8f, 10f, 12_000L))
    val r2 = engine.processSample(GpsSample(10.770360, 106.700000, 8f, 11f, 14_000L))

    assertEquals(SampleDecision.ACCEPTED, r1.decision)
    assertEquals(SampleDecision.ACCEPTED, r2.decision)
    assertTrue("Expected ~40m accumulated, was ${engine.totalDistanceMeters}", engine.totalDistanceMeters in 35.0..45.0)
    assertEquals(4_000L, engine.movingDurationMillis)
    assertTrue(engine.maxSpeedKmh >= 39.0)
  }

  @Test
  fun resetAnchorDuringPause_doesNotConnectStraightLineAcrossGap() {
    val engine = DistanceFilterEngine()
    engine.processSample(GpsSample(10.770000, 106.700000, 6f, 10f, 10_000L))
    engine.processSample(GpsSample(10.770200, 106.700000, 6f, 10f, 12_000L))
    val distBeforePause = engine.totalDistanceMeters

    engine.resetAnchor()
    val resumeAnchor = engine.processSample(
      GpsSample(10.780000, 106.710000, 6f, 0f, 600_000L),
    )

    assertEquals(SampleDecision.ANCHORED, resumeAnchor.decision)
    assertEquals(distBeforePause, engine.totalDistanceMeters, 0.001)
  }

  @Test
  fun zeroHardwareSpeedWhileMoving_fallsBackToImpliedSpeed() {
    val engine = DistanceFilterEngine()
    engine.processSample(GpsSample(10.770000, 106.700000, 5f, 0f, 10_000L))
    val r1 = engine.processSample(GpsSample(10.770220, 106.700000, 5f, 0f, 12_000L))

    assertEquals(SampleDecision.ACCEPTED, r1.decision)
    assertTrue("Expected speed >= 40 km/h, was ${r1.currentSpeedKmh}", r1.currentSpeedKmh >= 40.0)
    assertTrue("Expected maxSpeed >= 40 km/h, was ${engine.maxSpeedKmh}", engine.maxSpeedKmh >= 40.0)
  }
}
