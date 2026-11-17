/*
 * Copyright (c) 2026 Nguyên. All rights reserved.
 */

package com.nguyen.tripdriver.tracking

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.nguyen.tripdriver.data.ActiveTripCheckpoint
import com.nguyen.tripdriver.data.TripRepository
import com.nguyen.tripdriver.domain.DistanceFilterEngine
import com.nguyen.tripdriver.domain.GpsSample
import com.nguyen.tripdriver.domain.SampleDecision
import com.nguyen.tripdriver.domain.TripRecord
import com.nguyen.tripdriver.domain.VehicleType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Coordinates live trip tracking state, active riding duration, rest duration (while paused),
 * GPS sample filtering, and foreground service lifecycle across Activity restarts.
 */
class TripTrackerManager(
  private val appContext: Context,
  private val repository: TripRepository,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val distanceEngine = DistanceFilterEngine()

  private val _state = MutableStateFlow(ActiveTripState())
  val state: StateFlow<ActiveTripState> = _state.asStateFlow()

  private var accumulatedActiveBeforeSegmentMs: Long = 0L
  private var segmentStartElapsedMs: Long = 0L
  private var accumulatedRestBeforePauseMs: Long = 0L
  private var pauseStartElapsedMs: Long = 0L
  private var lastCheckpointElapsedMs: Long = 0L
  private var tickerJob: Job? = null
  private var checkpointRestored = false

  fun restoreIfNeeded() {
    if (checkpointRestored) return
    checkpointRestored = true
    scope.launch {
      val checkpoint = repository.loadCheckpoint() ?: return@launch
      if (_state.value.isSessionActive) return@launch

      accumulatedActiveBeforeSegmentMs = checkpoint.accumulatedActiveMillis
      accumulatedRestBeforePauseMs = checkpoint.restDurationMillis
      segmentStartElapsedMs = 0L
      pauseStartElapsedMs = if (checkpoint.isPaused) SystemClock.elapsedRealtime() else 0L

      distanceEngine.restoreState(
        distanceMeters = checkpoint.distanceMeters,
        movingMillis = checkpoint.movingDurationMillis,
        maxSpeedKmh = checkpoint.maxSpeedKmh,
      )

      _state.value = ActiveTripState(
        phase = TripPhase.PAUSED,
        vehicleType = checkpoint.vehicleType,
        startTimeWallMillis = checkpoint.tripStartWallMillis,
        activeDurationMillis = checkpoint.accumulatedActiveMillis,
        movingDurationMillis = checkpoint.movingDurationMillis,
        restDurationMillis = checkpoint.restDurationMillis,
        distanceMeters = checkpoint.distanceMeters,
        currentSpeedKmh = 0.0,
        maxSpeedKmh = checkpoint.maxSpeedKmh,
        gpsSignalStatus = GpsSignalStatus.PAUSED,
      )
      startTicker()
    }
  }

  fun startTrip(): Boolean {
    if (!hasLocationPermission(appContext)) return false
    if (_state.value.isSessionActive) return true

    val nowWall = System.currentTimeMillis()
    val nowElapsed = SystemClock.elapsedRealtime()

    accumulatedActiveBeforeSegmentMs = 0L
    segmentStartElapsedMs = nowElapsed
    accumulatedRestBeforePauseMs = 0L
    pauseStartElapsedMs = 0L
    lastCheckpointElapsedMs = nowElapsed
    distanceEngine.restoreState(0.0, 0L, 0.0)

    val initialSignal = if (isLocationEnabled(appContext)) {
      GpsSignalStatus.SEARCHING
    } else {
      GpsSignalStatus.LOCATION_OFF
    }

    _state.value = ActiveTripState(
      phase = TripPhase.TRACKING,
      vehicleType = VehicleType.MOTORBIKE,
      startTimeWallMillis = nowWall,
      activeDurationMillis = 0L,
      movingDurationMillis = 0L,
      restDurationMillis = 0L,
      distanceMeters = 0.0,
      currentSpeedKmh = 0.0,
      maxSpeedKmh = 0.0,
      lastAccuracyMeters = null,
      gpsSignalStatus = initialSignal,
      acceptedPointCount = 0,
    )

    persistCheckpointNow()
    startTicker()
    startServiceAction(TripTrackingService.ACTION_START)
    return true
  }

  fun pauseTrip() {
    val current = _state.value
    if (current.phase != TripPhase.TRACKING) return

    val nowElapsed = SystemClock.elapsedRealtime()
    accumulatedActiveBeforeSegmentMs = currentActiveDurationMillis(nowElapsed)
    segmentStartElapsedMs = 0L
    pauseStartElapsedMs = nowElapsed
    distanceEngine.resetAnchor()

    _state.update {
      it.copy(
        phase = TripPhase.PAUSED,
        activeDurationMillis = accumulatedActiveBeforeSegmentMs,
        restDurationMillis = currentRestDurationMillis(nowElapsed),
        currentSpeedKmh = 0.0,
        gpsSignalStatus = GpsSignalStatus.PAUSED,
      )
    }

    persistCheckpointNow()
    startTicker()
    startServiceAction(TripTrackingService.ACTION_PAUSE)
  }

  fun resumeTrip(): Boolean {
    val current = _state.value
    if (current.phase != TripPhase.PAUSED) return false
    if (!hasLocationPermission(appContext)) return false

    val nowElapsed = SystemClock.elapsedRealtime()
    accumulatedRestBeforePauseMs = currentRestDurationMillis(nowElapsed)
    pauseStartElapsedMs = 0L
    segmentStartElapsedMs = nowElapsed
    lastCheckpointElapsedMs = nowElapsed
    distanceEngine.resetAnchor()

    val signal = if (isLocationEnabled(appContext)) {
      GpsSignalStatus.SEARCHING
    } else {
      GpsSignalStatus.LOCATION_OFF
    }

    _state.update {
      it.copy(
        phase = TripPhase.TRACKING,
        restDurationMillis = accumulatedRestBeforePauseMs,
        gpsSignalStatus = signal,
      )
    }

    persistCheckpointNow()
    startTicker()
    startServiceAction(TripTrackingService.ACTION_RESUME)
    return true
  }

  suspend fun finishAndSaveTrip(): TripRecord? {
    val current = _state.value
    if (!current.isSessionActive) return null

    val nowWall = System.currentTimeMillis()
    val nowElapsed = SystemClock.elapsedRealtime()
    val totalActiveMillis = if (current.phase == TripPhase.TRACKING) {
      currentActiveDurationMillis(nowElapsed)
    } else {
      accumulatedActiveBeforeSegmentMs
    }
    val totalRestMillis = if (current.phase == TripPhase.PAUSED) {
      currentRestDurationMillis(nowElapsed)
    } else {
      accumulatedRestBeforePauseMs
    }

    // Snapshot distance & speed telemetry BEFORE stopTrackingInternal() resets distanceEngine.
    val finalMovingDurationMillis = distanceEngine.movingDurationMillis
    val finalDistanceMeters = distanceEngine.totalDistanceMeters
    val finalMaxSpeedKmh = distanceEngine.maxSpeedKmh

    stopTrackingInternal()

    val trip = TripRecord(
      vehicleType = VehicleType.MOTORBIKE,
      startTimeMillis = current.startTimeWallMillis,
      endTimeMillis = nowWall.coerceAtLeast(current.startTimeWallMillis),
      activeDurationMillis = totalActiveMillis.coerceAtLeast(1_000L),
      movingDurationMillis = finalMovingDurationMillis,
      restDurationMillis = totalRestMillis,
      distanceMeters = finalDistanceMeters,
      maxSpeedKmh = finalMaxSpeedKmh,
      isManualEntry = false,
    )

    val savedId = repository.insertTrip(trip)
    repository.clearCheckpoint()
    return trip.copy(id = savedId)
  }

  suspend fun discardTrip() {
    if (!_state.value.isSessionActive) return
    stopTrackingInternal()
    repository.clearCheckpoint()
  }

  fun onLocationReceived(location: Location) {
    val current = _state.value
    if (current.phase != TripPhase.TRACKING) return

    val sample = GpsSample(
      latitude = location.latitude,
      longitude = location.longitude,
      accuracyMeters = location.accuracy,
      speedMetersPerSec = if (location.hasSpeed()) location.speed else null,
      timestampMillis = location.time.takeIf { it > 0L } ?: System.currentTimeMillis(),
    )

    val result = distanceEngine.processSample(sample)
    val signal = when {
      !isLocationEnabled(appContext) -> GpsSignalStatus.LOCATION_OFF
      result.decision == SampleDecision.IGNORED_POOR_ACCURACY -> GpsSignalStatus.WEAK
      sample.accuracyMeters <= 15f -> GpsSignalStatus.GOOD
      else -> GpsSignalStatus.WEAK
    }

    val nowElapsed = SystemClock.elapsedRealtime()
    val activeMs = currentActiveDurationMillis(nowElapsed)

    _state.update { prev ->
      val newAcceptedCount = if (
        result.decision == SampleDecision.ANCHORED ||
        result.decision == SampleDecision.ACCEPTED
      ) {
        prev.acceptedPointCount + 1
      } else {
        prev.acceptedPointCount
      }
      val displaySpeed = when (result.decision) {
        SampleDecision.ACCEPTED -> result.currentSpeedKmh
        SampleDecision.ANCHORED -> result.currentSpeedKmh
        SampleDecision.IGNORED_DRIFT -> 0.0
        else -> prev.currentSpeedKmh
      }

      prev.copy(
        activeDurationMillis = activeMs,
        movingDurationMillis = distanceEngine.movingDurationMillis,
        restDurationMillis = accumulatedRestBeforePauseMs,
        distanceMeters = distanceEngine.totalDistanceMeters,
        currentSpeedKmh = displaySpeed,
        maxSpeedKmh = distanceEngine.maxSpeedKmh,
        lastAccuracyMeters = sample.accuracyMeters,
        gpsSignalStatus = signal,
        acceptedPointCount = newAcceptedCount,
      )
    }

    if (nowElapsed - lastCheckpointElapsedMs >= CHECKPOINT_INTERVAL_MS) {
      lastCheckpointElapsedMs = nowElapsed
      persistCheckpointNow()
    }
  }

  fun onLocationAvailabilityChanged(enabled: Boolean) {
    _state.update { current ->
      if (current.phase != TripPhase.TRACKING) return@update current
      current.copy(
        gpsSignalStatus = if (enabled) {
          if (current.gpsSignalStatus == GpsSignalStatus.LOCATION_OFF) GpsSignalStatus.SEARCHING
          else current.gpsSignalStatus
        } else {
          GpsSignalStatus.LOCATION_OFF
        },
        currentSpeedKmh = if (enabled) current.currentSpeedKmh else 0.0,
      )
    }
  }

  private fun startTicker() {
    tickerJob?.cancel()
    tickerJob = scope.launch {
      while (isActive) {
        val current = _state.value
        if (!current.isSessionActive) break
        val nowElapsed = SystemClock.elapsedRealtime()

        if (current.phase == TripPhase.TRACKING) {
          val activeMs = currentActiveDurationMillis(nowElapsed)
          val locationOn = isLocationEnabled(appContext)
          _state.update { prev ->
            prev.copy(
              activeDurationMillis = activeMs,
              gpsSignalStatus = if (!locationOn) {
                GpsSignalStatus.LOCATION_OFF
              } else if (prev.gpsSignalStatus == GpsSignalStatus.LOCATION_OFF) {
                GpsSignalStatus.SEARCHING
              } else {
                prev.gpsSignalStatus
              },
            )
          }
        } else if (current.phase == TripPhase.PAUSED) {
          val restMs = currentRestDurationMillis(nowElapsed)
          _state.update { prev ->
            prev.copy(restDurationMillis = restMs)
          }
        }

        if (nowElapsed - lastCheckpointElapsedMs >= CHECKPOINT_INTERVAL_MS) {
          lastCheckpointElapsedMs = nowElapsed
          persistCheckpointNow()
        }
        delay(1_000L)
      }
    }
  }

  private fun stopTrackingInternal() {
    tickerJob?.cancel()
    tickerJob = null
    accumulatedActiveBeforeSegmentMs = 0L
    segmentStartElapsedMs = 0L
    accumulatedRestBeforePauseMs = 0L
    pauseStartElapsedMs = 0L
    distanceEngine.restoreState(0.0, 0L, 0.0)
    _state.value = ActiveTripState()
    stopTrackingService()
  }

  private fun currentActiveDurationMillis(nowElapsed: Long): Long {
    val segmentElapsed = if (segmentStartElapsedMs > 0L) {
      (nowElapsed - segmentStartElapsedMs).coerceAtLeast(0L)
    } else {
      0L
    }
    return accumulatedActiveBeforeSegmentMs + segmentElapsed
  }

  private fun currentRestDurationMillis(nowElapsed: Long): Long {
    val pauseElapsed = if (pauseStartElapsedMs > 0L) {
      (nowElapsed - pauseStartElapsedMs).coerceAtLeast(0L)
    } else {
      0L
    }
    return accumulatedRestBeforePauseMs + pauseElapsed
  }

  private fun persistCheckpointNow() {
    val snapshot = _state.value
    if (!snapshot.isSessionActive) return
    val nowElapsed = SystemClock.elapsedRealtime()
    val activeMs = if (snapshot.phase == TripPhase.TRACKING) {
      currentActiveDurationMillis(nowElapsed)
    } else {
      accumulatedActiveBeforeSegmentMs
    }
    val restMs = if (snapshot.phase == TripPhase.PAUSED) {
      currentRestDurationMillis(nowElapsed)
    } else {
      accumulatedRestBeforePauseMs
    }
    val checkpoint = ActiveTripCheckpoint(
      vehicleType = VehicleType.MOTORBIKE,
      tripStartWallMillis = snapshot.startTimeWallMillis,
      accumulatedActiveMillis = activeMs,
      movingDurationMillis = distanceEngine.movingDurationMillis,
      restDurationMillis = restMs,
      distanceMeters = distanceEngine.totalDistanceMeters,
      maxSpeedKmh = distanceEngine.maxSpeedKmh,
      isPaused = snapshot.phase == TripPhase.PAUSED,
      lastUpdatedWallMillis = System.currentTimeMillis(),
    )
    scope.launch {
      repository.saveCheckpoint(checkpoint)
    }
  }

  private fun startServiceAction(action: String) {
    val intent = Intent(appContext, TripTrackingService::class.java).apply {
      this.action = action
    }
    ContextCompat.startForegroundService(appContext, intent)
  }

  private fun stopTrackingService() {
    if (TripTrackingService.isRunning) {
      val intent = Intent(appContext, TripTrackingService::class.java).apply {
        action = TripTrackingService.ACTION_STOP
      }
      try {
        appContext.startService(intent)
      } catch (_: IllegalStateException) {
        // App may be backgrounded; service observer handles stop cleanly.
      }
    }
  }

  companion object {
    private const val CHECKPOINT_INTERVAL_MS = 10_000L

    fun hasLocationPermission(context: Context): Boolean {
      val fine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
      ) == PackageManager.PERMISSION_GRANTED
      val coarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION,
      ) == PackageManager.PERMISSION_GRANTED
      return fine || coarse
    }

    fun isLocationEnabled(context: Context): Boolean {
      val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
      return LocationManagerCompat.isLocationEnabled(lm)
    }
  }
}
