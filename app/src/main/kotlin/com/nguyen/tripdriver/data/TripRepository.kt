/*
 * Copyright (c) 2026 Nguyên. All rights reserved.
 */

package com.nguyen.tripdriver.data

import android.content.Context
import com.nguyen.tripdriver.domain.TripRecord
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface TripRepository {
  val trips: Flow<List<TripRecord>>
  suspend fun insertTrip(trip: TripRecord): Long
  suspend fun updateTrip(trip: TripRecord)
  suspend fun deleteTrip(tripId: Long)
  suspend fun saveCheckpoint(checkpoint: ActiveTripCheckpoint)
  suspend fun loadCheckpoint(): ActiveTripCheckpoint?
  suspend fun clearCheckpoint()
}

class SqliteTripRepository(
  context: Context,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TripRepository {

  private val dbHelper = TripDatabaseHelper(context.applicationContext)
  private val dbMutex = Mutex()
  private val _trips = MutableStateFlow<List<TripRecord>>(emptyList())

  override val trips: Flow<List<TripRecord>> = _trips.asStateFlow()

  suspend fun initialize() {
    refreshTrips()
  }

  private suspend fun refreshTrips() {
    val all = withContext(ioDispatcher) {
      dbMutex.withLock { dbHelper.getAllTrips() }
    }
    _trips.value = all
  }

  override suspend fun insertTrip(trip: TripRecord): Long {
    val insertedId = withContext(ioDispatcher) {
      dbMutex.withLock { dbHelper.insertTrip(trip) }
    }
    refreshTrips()
    return insertedId
  }

  override suspend fun updateTrip(trip: TripRecord) {
    withContext(ioDispatcher) {
      dbMutex.withLock { dbHelper.updateTrip(trip) }
    }
    refreshTrips()
  }

  override suspend fun deleteTrip(tripId: Long) {
    withContext(ioDispatcher) {
      dbMutex.withLock { dbHelper.deleteTrip(tripId) }
    }
    refreshTrips()
  }

  override suspend fun saveCheckpoint(checkpoint: ActiveTripCheckpoint) {
    withContext(ioDispatcher) {
      dbMutex.withLock { dbHelper.saveCheckpoint(checkpoint) }
    }
  }

  override suspend fun loadCheckpoint(): ActiveTripCheckpoint? {
    return withContext(ioDispatcher) {
      dbMutex.withLock { dbHelper.loadCheckpoint() }
    }
  }

  override suspend fun clearCheckpoint() {
    withContext(ioDispatcher) {
      dbMutex.withLock { dbHelper.clearCheckpoint() }
    }
  }
}
