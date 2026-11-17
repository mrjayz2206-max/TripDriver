/*
 * Copyright (c) 2026 Nguyên. All rights reserved.
 */

package com.nguyen.tripdriver.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nguyen.tripdriver.domain.TripRecord
import com.nguyen.tripdriver.domain.VehicleType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SqliteTripRepositoryTest {

  private lateinit var repository: SqliteTripRepository

  @Before
  fun setup() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    context.deleteDatabase("tripdriver.db")
    repository = SqliteTripRepository(context)
    repository.initialize()
  }

  @After
  fun tearDown() = runBlocking {
    repository.clearCheckpoint()
  }

  @Test
  fun insertUpdateDeleteTrip_andCheckpointWithRestDuration() = runBlocking {
    val trip = TripRecord(
      vehicleType = VehicleType.MOTORBIKE,
      startTimeMillis = 1_700_000_000_000L,
      endTimeMillis = 1_700_001_500_000L,
      activeDurationMillis = 1_200_000L,
      movingDurationMillis = 1_000_000L,
      restDurationMillis = 300_000L,
      distanceMeters = 8_420.0,
      maxSpeedKmh = 48.0,
    )

    val id = repository.insertTrip(trip)
    var stored = repository.trips.first()
    assertEquals(1, stored.size)
    assertEquals(8_420.0, stored.first().distanceMeters, 0.01)
    assertEquals(300_000L, stored.first().restDurationMillis)

    repository.updateTrip(stored.first().copy(distanceMeters = 9_100.0, restDurationMillis = 420_000L))
    stored = repository.trips.first()
    assertEquals(9_100.0, stored.first().distanceMeters, 0.01)
    assertEquals(420_000L, stored.first().restDurationMillis)

    repository.saveCheckpoint(
      ActiveTripCheckpoint(
        vehicleType = VehicleType.MOTORBIKE,
        tripStartWallMillis = 1_700_002_000_000L,
        accumulatedActiveMillis = 300_000L,
        movingDurationMillis = 250_000L,
        restDurationMillis = 90_000L,
        distanceMeters = 2_400.0,
        maxSpeedKmh = 36.0,
        isPaused = true,
        lastUpdatedWallMillis = 1_700_002_300_000L,
      ),
    )

    val loaded = repository.loadCheckpoint()
    assertNotNull(loaded)
    assertEquals(90_000L, loaded!!.restDurationMillis)
    assertEquals(2_400.0, loaded.distanceMeters, 0.01)

    repository.clearCheckpoint()
    assertNull(repository.loadCheckpoint())

    repository.deleteTrip(id)
    assertEquals(0, repository.trips.first().size)
  }
}
