/*
 * Copyright (c) 2026 Nguyên. All rights reserved.
 */

package com.nguyen.tripdriver.ui.logbook

import com.nguyen.tripdriver.data.ActiveTripCheckpoint
import com.nguyen.tripdriver.data.TripRepository
import com.nguyen.tripdriver.domain.TripRecord
import com.nguyen.tripdriver.domain.VehicleType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class LogbookViewModelTest {

  private val testDispatcher = StandardTestDispatcher()

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun updateAndDeleteTrips_updatesMonthlyAndDailySummaries() = runTest(testDispatcher) {
    val repo = FakeTripRepository()
    val date = LocalDate.of(2026, 11, 17)
    val startMs = date.atTime(8, 30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val insertedId = repo.insertTrip(
      TripRecord(
        vehicleType = VehicleType.MOTORBIKE,
        startTimeMillis = startMs,
        endTimeMillis = startMs + 3_300_000L,
        activeDurationMillis = 2_700_000L,
        movingDurationMillis = 2_400_000L,
        restDurationMillis = 600_000L,
        distanceMeters = 18_500.0,
        maxSpeedKmh = 48.0,
      ),
    )

    val vm = LogbookViewModel(repo)
    val collectJob = launch { vm.uiState.collect() }
    advanceUntilIdle()

    assertEquals(YearMonth.of(2026, 11), vm.uiState.value.selectedMonth)
    assertEquals(18_500.0, vm.uiState.value.monthlySummary.totalDistanceMeters, 0.01)
    assertEquals(600_000L, vm.uiState.value.monthlySummary.totalRestDurationMillis)

    val saved = vm.uiState.value.monthlySummary.dailySummaries.first().trips.first()
    vm.updateTrip(saved, durationMinutes = 50, restMinutes = 15, distanceKm = 20.0)
    advanceUntilIdle()
    assertEquals(20_000.0, vm.uiState.value.monthlySummary.totalDistanceMeters, 0.01)
    assertEquals(900_000L, vm.uiState.value.monthlySummary.totalRestDurationMillis)

    vm.deleteTrip(insertedId)
    advanceUntilIdle()
    assertEquals(0, vm.uiState.value.monthlySummary.totalTrips)

    collectJob.cancel()
  }
}

private class FakeTripRepository : TripRepository {
  private val items = MutableStateFlow<List<TripRecord>>(emptyList())
  private var nextId = 1L

  override val trips: Flow<List<TripRecord>> = items

  override suspend fun insertTrip(trip: TripRecord): Long {
    val id = nextId++
    items.value = listOf(trip.copy(id = id)) + items.value
    return id
  }

  override suspend fun updateTrip(trip: TripRecord) {
    items.value = items.value.map { if (it.id == trip.id) trip else it }
  }

  override suspend fun deleteTrip(tripId: Long) {
    items.value = items.value.filterNot { it.id == tripId }
  }

  override suspend fun saveCheckpoint(checkpoint: ActiveTripCheckpoint) {}
  override suspend fun loadCheckpoint(): ActiveTripCheckpoint? = null
  override suspend fun clearCheckpoint() {}
}
