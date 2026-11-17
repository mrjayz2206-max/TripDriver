/*
 * Copyright (c) 2026 Nguyên. All rights reserved.
 */

package com.nguyen.tripdriver.tracking

import android.Manifest
import android.location.Location
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.nguyen.tripdriver.data.SqliteTripRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripTrackerManagerTest {

  @get:Rule
  val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.POST_NOTIFICATIONS,
  )

  private lateinit var repository: SqliteTripRepository
  private lateinit var manager: TripTrackerManager

  @Before
  fun setup() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    context.deleteDatabase("tripdriver.db")
    repository = SqliteTripRepository(context)
    repository.initialize()
    manager = TripTrackerManager(context, repository)
  }

  @After
  fun tearDown() = runBlocking(Dispatchers.Main.immediate) {
    manager.discardTrip()
  }

  @Test
  fun pauseAndResume_tracksRestDurationAndSavesExactTripMetrics() = runBlocking(Dispatchers.Main.immediate) {
    assertTrue(manager.startTrip())

    val p1 = Location("gps").apply {
      latitude = 10.770000
      longitude = 106.700000
      accuracy = 5f
      speed = 0f
      time = 1_700_000_000_000L
    }
    val p2 = Location("gps").apply {
      latitude = 10.770250
      longitude = 106.700000
      accuracy = 5f
      speed = 0f
      time = 1_700_000_002_000L
    }
    val p3 = Location("gps").apply {
      latitude = 10.770500
      longitude = 106.700000
      accuracy = 5f
      speed = 0f
      time = 1_700_000_004_000L
    }

    manager.onLocationReceived(p1)
    manager.onLocationReceived(p2)
    manager.onLocationReceived(p3)

    // Pause to accumulate rest duration
    manager.pauseTrip()
    assertEquals(TripPhase.PAUSED, manager.state.value.phase)
    delay(1_250L)
    assertTrue(
      "Expected live rest duration >= 1000ms while paused, was ${manager.state.value.restDurationMillis}",
      manager.state.value.restDurationMillis >= 1_000L,
    )

    // Resume and verify rest timer freezes
    assertTrue(manager.resumeTrip())
    assertEquals(TripPhase.TRACKING, manager.state.value.phase)
    val restAfterResume = manager.state.value.restDurationMillis
    delay(500L)
    assertEquals(restAfterResume, manager.state.value.restDurationMillis)

    val saved = manager.finishAndSaveTrip()
    assertNotNull(saved)
    assertTrue("Saved distance must be > 50m, was ${saved!!.distanceMeters}", saved.distanceMeters > 50.0)
    assertTrue("Saved maxSpeed must be >= 40 km/h, was ${saved.maxSpeedKmh}", saved.maxSpeedKmh >= 40.0)
    assertEquals(restAfterResume, saved.restDurationMillis)
    assertEquals(TripPhase.IDLE, manager.state.value.phase)

    val allTrips = repository.trips.first()
    assertEquals(1, allTrips.size)
    assertEquals(saved.restDurationMillis, allTrips.first().restDurationMillis)

    delay(400L)
  }
}
