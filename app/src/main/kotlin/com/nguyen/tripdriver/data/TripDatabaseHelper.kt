/*
 * Copyright (c) 2026 Nguyên. All rights reserved.
 */

package com.nguyen.tripdriver.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.nguyen.tripdriver.domain.TripRecord
import com.nguyen.tripdriver.domain.VehicleType

data class ActiveTripCheckpoint(
  val vehicleType: VehicleType,
  val tripStartWallMillis: Long,
  val accumulatedActiveMillis: Long,
  val movingDurationMillis: Long,
  val restDurationMillis: Long,
  val distanceMeters: Double,
  val maxSpeedKmh: Double,
  val isPaused: Boolean,
  val lastUpdatedWallMillis: Long,
)

class TripDatabaseHelper(context: Context) :
  SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

  override fun onCreate(db: SQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE $TABLE_TRIPS (
        $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
        $COL_VEHICLE_TYPE TEXT NOT NULL,
        $COL_START_TIME INTEGER NOT NULL,
        $COL_END_TIME INTEGER NOT NULL,
        $COL_ACTIVE_DURATION INTEGER NOT NULL,
        $COL_MOVING_DURATION INTEGER NOT NULL,
        $COL_REST_DURATION INTEGER NOT NULL DEFAULT 0,
        $COL_DISTANCE_METERS REAL NOT NULL,
        $COL_MAX_SPEED_KMH REAL NOT NULL,
        $COL_NOTE TEXT NOT NULL DEFAULT '',
        $COL_IS_MANUAL INTEGER NOT NULL DEFAULT 0
      )
      """.trimIndent(),
    )
    db.execSQL("CREATE INDEX idx_trips_start_time ON $TABLE_TRIPS ($COL_START_TIME DESC)")

    db.execSQL(
      """
      CREATE TABLE $TABLE_CHECKPOINT (
        $COL_ID INTEGER PRIMARY KEY,
        $COL_VEHICLE_TYPE TEXT NOT NULL,
        $COL_START_TIME INTEGER NOT NULL,
        $COL_ACTIVE_DURATION INTEGER NOT NULL,
        $COL_MOVING_DURATION INTEGER NOT NULL,
        $COL_REST_DURATION INTEGER NOT NULL DEFAULT 0,
        $COL_DISTANCE_METERS REAL NOT NULL,
        $COL_MAX_SPEED_KMH REAL NOT NULL,
        $COL_IS_PAUSED INTEGER NOT NULL,
        $COL_LAST_UPDATED INTEGER NOT NULL,
        $COL_NOTE TEXT NOT NULL DEFAULT ''
      )
      """.trimIndent(),
    )
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    if (oldVersion < 2) {
      db.execSQL("ALTER TABLE $TABLE_TRIPS ADD COLUMN $COL_REST_DURATION INTEGER NOT NULL DEFAULT 0")
      db.execSQL("ALTER TABLE $TABLE_CHECKPOINT ADD COLUMN $COL_REST_DURATION INTEGER NOT NULL DEFAULT 0")
    }
  }

  fun insertTrip(trip: TripRecord): Long {
    val values = ContentValues().apply {
      put(COL_VEHICLE_TYPE, trip.vehicleType.storageKey)
      put(COL_START_TIME, trip.startTimeMillis)
      put(COL_END_TIME, trip.endTimeMillis)
      put(COL_ACTIVE_DURATION, trip.activeDurationMillis)
      put(COL_MOVING_DURATION, trip.movingDurationMillis)
      put(COL_REST_DURATION, trip.restDurationMillis)
      put(COL_DISTANCE_METERS, trip.distanceMeters)
      put(COL_MAX_SPEED_KMH, trip.maxSpeedKmh)
      put(COL_NOTE, "")
      put(COL_IS_MANUAL, if (trip.isManualEntry) 1 else 0)
    }
    return writableDatabase.insertOrThrow(TABLE_TRIPS, null, values)
  }

  fun updateTrip(trip: TripRecord): Int {
    val values = ContentValues().apply {
      put(COL_VEHICLE_TYPE, trip.vehicleType.storageKey)
      put(COL_START_TIME, trip.startTimeMillis)
      put(COL_END_TIME, trip.endTimeMillis)
      put(COL_ACTIVE_DURATION, trip.activeDurationMillis)
      put(COL_MOVING_DURATION, trip.movingDurationMillis)
      put(COL_REST_DURATION, trip.restDurationMillis)
      put(COL_DISTANCE_METERS, trip.distanceMeters)
      put(COL_MAX_SPEED_KMH, trip.maxSpeedKmh)
      put(COL_NOTE, "")
      put(COL_IS_MANUAL, if (trip.isManualEntry) 1 else 0)
    }
    return writableDatabase.update(TABLE_TRIPS, values, "$COL_ID = ?", arrayOf(trip.id.toString()))
  }

  fun deleteTrip(tripId: Long): Int {
    return writableDatabase.delete(TABLE_TRIPS, "$COL_ID = ?", arrayOf(tripId.toString()))
  }

  fun getAllTrips(): List<TripRecord> {
    val result = mutableListOf<TripRecord>()
    val cursor = readableDatabase.query(
      TABLE_TRIPS,
      null,
      null,
      null,
      null,
      null,
      "$COL_START_TIME DESC",
    )
    cursor.use { c ->
      val idIdx = c.getColumnIndexOrThrow(COL_ID)
      val vehicleIdx = c.getColumnIndexOrThrow(COL_VEHICLE_TYPE)
      val startIdx = c.getColumnIndexOrThrow(COL_START_TIME)
      val endIdx = c.getColumnIndexOrThrow(COL_END_TIME)
      val activeDurIdx = c.getColumnIndexOrThrow(COL_ACTIVE_DURATION)
      val movingDurIdx = c.getColumnIndexOrThrow(COL_MOVING_DURATION)
      val restDurIdx = c.getColumnIndexOrThrow(COL_REST_DURATION)
      val distIdx = c.getColumnIndexOrThrow(COL_DISTANCE_METERS)
      val maxSpeedIdx = c.getColumnIndexOrThrow(COL_MAX_SPEED_KMH)
      val manualIdx = c.getColumnIndexOrThrow(COL_IS_MANUAL)

      while (c.moveToNext()) {
        result.add(
          TripRecord(
            id = c.getLong(idIdx),
            vehicleType = VehicleType.fromStorageKey(c.getString(vehicleIdx)),
            startTimeMillis = c.getLong(startIdx),
            endTimeMillis = c.getLong(endIdx),
            activeDurationMillis = c.getLong(activeDurIdx),
            movingDurationMillis = c.getLong(movingDurIdx),
            restDurationMillis = c.getLong(restDurIdx),
            distanceMeters = c.getDouble(distIdx),
            maxSpeedKmh = c.getDouble(maxSpeedIdx),
            isManualEntry = c.getInt(manualIdx) == 1,
          ),
        )
      }
    }
    return result
  }

  fun saveCheckpoint(checkpoint: ActiveTripCheckpoint) {
    val values = ContentValues().apply {
      put(COL_ID, 1)
      put(COL_VEHICLE_TYPE, checkpoint.vehicleType.storageKey)
      put(COL_START_TIME, checkpoint.tripStartWallMillis)
      put(COL_ACTIVE_DURATION, checkpoint.accumulatedActiveMillis)
      put(COL_MOVING_DURATION, checkpoint.movingDurationMillis)
      put(COL_REST_DURATION, checkpoint.restDurationMillis)
      put(COL_DISTANCE_METERS, checkpoint.distanceMeters)
      put(COL_MAX_SPEED_KMH, checkpoint.maxSpeedKmh)
      put(COL_IS_PAUSED, if (checkpoint.isPaused) 1 else 0)
      put(COL_LAST_UPDATED, checkpoint.lastUpdatedWallMillis)
      put(COL_NOTE, "")
    }
    writableDatabase.insertWithOnConflict(TABLE_CHECKPOINT, null, values, SQLiteDatabase.CONFLICT_REPLACE)
  }

  fun loadCheckpoint(): ActiveTripCheckpoint? {
    val cursor = readableDatabase.query(
      TABLE_CHECKPOINT,
      null,
      "$COL_ID = 1",
      null,
      null,
      null,
      null,
    )
    cursor.use { c ->
      if (!c.moveToFirst()) return null
      return ActiveTripCheckpoint(
        vehicleType = VehicleType.fromStorageKey(c.getString(c.getColumnIndexOrThrow(COL_VEHICLE_TYPE))),
        tripStartWallMillis = c.getLong(c.getColumnIndexOrThrow(COL_START_TIME)),
        accumulatedActiveMillis = c.getLong(c.getColumnIndexOrThrow(COL_ACTIVE_DURATION)),
        movingDurationMillis = c.getLong(c.getColumnIndexOrThrow(COL_MOVING_DURATION)),
        restDurationMillis = c.getLong(c.getColumnIndexOrThrow(COL_REST_DURATION)),
        distanceMeters = c.getDouble(c.getColumnIndexOrThrow(COL_DISTANCE_METERS)),
        maxSpeedKmh = c.getDouble(c.getColumnIndexOrThrow(COL_MAX_SPEED_KMH)),
        isPaused = c.getInt(c.getColumnIndexOrThrow(COL_IS_PAUSED)) == 1,
        lastUpdatedWallMillis = c.getLong(c.getColumnIndexOrThrow(COL_LAST_UPDATED)),
      )
    }
  }

  fun clearCheckpoint() {
    writableDatabase.delete(TABLE_CHECKPOINT, "$COL_ID = 1", null)
  }

  companion object {
    private const val DATABASE_NAME = "tripdriver.db"
    private const val DATABASE_VERSION = 2
    private const val TABLE_TRIPS = "trips"
    private const val TABLE_CHECKPOINT = "active_checkpoint"

    private const val COL_ID = "_id"
    private const val COL_VEHICLE_TYPE = "vehicle_type"
    private const val COL_START_TIME = "start_time_ms"
    private const val COL_END_TIME = "end_time_ms"
    private const val COL_ACTIVE_DURATION = "active_duration_ms"
    private const val COL_MOVING_DURATION = "moving_duration_ms"
    private const val COL_REST_DURATION = "rest_duration_ms"
    private const val COL_DISTANCE_METERS = "distance_meters"
    private const val COL_MAX_SPEED_KMH = "max_speed_kmh"
    private const val COL_NOTE = "note"
    private const val COL_IS_MANUAL = "is_manual"
    private const val COL_IS_PAUSED = "is_paused"
    private const val COL_LAST_UPDATED = "last_updated_ms"
  }
}
