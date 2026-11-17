/*
 * Copyright (c) 2026 Nguyên. All rights reserved.
 */

package com.nguyen.tripdriver.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.nguyen.tripdriver.MainActivity
import com.nguyen.tripdriver.R
import com.nguyen.tripdriver.TripDriverApp
import com.nguyen.tripdriver.domain.TripFormatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground location service that keeps GPS tracking alive while the screen is locked
 * or the user switches to another app.
 */
class TripTrackingService : Service() {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private lateinit var trackerManager: TripTrackerManager
  private lateinit var notificationManager: NotificationManager

  private var fusedClient: FusedLocationProviderClient? = null
  private var fusedCallback: LocationCallback? = null
  private var fallbackLocationManager: LocationManager? = null
  private var fallbackListener: LocationListener? = null
  private var stateObserverJob: Job? = null
  private var isRequestingLocation = false
  private var lastNotifiedSecond = -1L
  private var lastNotifiedRestSecond = -1L
  private var lastNotifiedPhase: TripPhase? = null

  override fun onCreate() {
    super.onCreate()
    trackerManager = (application as TripDriverApp).trackerManager
    notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    ensureNotificationChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      stopAndRemoveForeground()
      return START_NOT_STICKY
    }

    if (!TripTrackerManager.hasLocationPermission(this)) {
      stopSelf()
      return START_NOT_STICKY
    }

    val state = trackerManager.state.value
    promoteToForeground(state)
    isRunning = true

    if (!state.isSessionActive) {
      stopAndRemoveForeground()
      return START_NOT_STICKY
    }

    when (intent?.action) {
      ACTION_START, ACTION_RESUME -> {
        if (intent.action == ACTION_RESUME && state.phase == TripPhase.PAUSED) {
          trackerManager.resumeTrip()
        }
        startLocationUpdates()
      }
      ACTION_PAUSE -> {
        if (state.phase == TripPhase.TRACKING) {
          trackerManager.pauseTrip()
        }
        stopLocationUpdates()
      }
      ACTION_NOTIFICATION_TOGGLE_PAUSE -> {
        when (trackerManager.state.value.phase) {
          TripPhase.TRACKING -> {
            trackerManager.pauseTrip()
            stopLocationUpdates()
          }
          TripPhase.PAUSED -> {
            if (trackerManager.resumeTrip()) {
              startLocationUpdates()
            }
          }
          TripPhase.IDLE -> {
            stopAndRemoveForeground()
            return START_NOT_STICKY
          }
        }
      }
      null -> {
        if (state.phase == TripPhase.TRACKING) {
          startLocationUpdates()
        }
      }
    }

    observeStateForNotification()
    return START_STICKY
  }

  override fun onDestroy() {
    isRunning = false
    stopLocationUpdates()
    stateObserverJob?.cancel()
    serviceScope.cancel()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun promoteToForeground(state: ActiveTripState) {
    val notification = buildNotification(state)
    val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
    } else {
      0
    }
    ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, foregroundType)
  }

  private fun stopAndRemoveForeground() {
    isRunning = false
    stopLocationUpdates()
    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  private fun observeStateForNotification() {
    if (stateObserverJob != null) return
    stateObserverJob = serviceScope.launch {
      trackerManager.state.collectLatest { state ->
        if (!state.isSessionActive) {
          stopAndRemoveForeground()
          return@collectLatest
        }

        val currentSecond = state.activeDurationMillis / 1000L
        val currentRestSecond = state.restDurationMillis / 1000L
        if (currentSecond != lastNotifiedSecond ||
          currentRestSecond != lastNotifiedRestSecond ||
          state.phase != lastNotifiedPhase
        ) {
          lastNotifiedSecond = currentSecond
          lastNotifiedRestSecond = currentRestSecond
          lastNotifiedPhase = state.phase
          notificationManager.notify(NOTIFICATION_ID, buildNotification(state))
        }
      }
    }
  }

  private fun startLocationUpdates() {
    if (isRequestingLocation) return
    if (!TripTrackerManager.hasLocationPermission(this)) return

    if (isPlayServicesAvailable()) {
      startFusedLocationUpdates()
    } else {
      startLegacyLocationUpdates()
    }
  }

  private fun isPlayServicesAvailable(): Boolean {
    return try {
      GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this) ==
        ConnectionResult.SUCCESS
    } catch (_: Throwable) {
      false
    }
  }

  private fun startFusedLocationUpdates() {
    try {
      val client = fusedClient ?: LocationServices.getFusedLocationProviderClient(this).also {
        fusedClient = it
      }
      val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000L)
        .setMinUpdateIntervalMillis(1_000L)
        .setMaxUpdateDelayMillis(3_000L)
        .setWaitForAccurateLocation(false)
        .build()

      val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
          for (location in result.locations) {
            trackerManager.onLocationReceived(location)
          }
        }

        override fun onLocationAvailability(availability: LocationAvailability) {
          trackerManager.onLocationAvailabilityChanged(
            availability.isLocationAvailable || TripTrackerManager.isLocationEnabled(this@TripTrackingService),
          )
        }
      }

      fusedCallback = callback
      client.requestLocationUpdates(request, callback, Looper.getMainLooper())
      isRequestingLocation = true
    } catch (_: SecurityException) {
      isRequestingLocation = false
      stopSelf()
    } catch (_: Throwable) {
      startLegacyLocationUpdates()
    }
  }

  private fun startLegacyLocationUpdates() {
    try {
      val lm = fallbackLocationManager
        ?: (getSystemService(Context.LOCATION_SERVICE) as? LocationManager)?.also {
          fallbackLocationManager = it
        }
        ?: return

      val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
          trackerManager.onLocationReceived(location)
        }

        override fun onProviderEnabled(provider: String) {
          trackerManager.onLocationAvailabilityChanged(true)
        }

        override fun onProviderDisabled(provider: String) {
          trackerManager.onLocationAvailabilityChanged(TripTrackerManager.isLocationEnabled(this@TripTrackingService))
        }
      }
      fallbackListener = listener
      if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
        lm.requestLocationUpdates(
          LocationManager.GPS_PROVIDER,
          2_000L,
          0f,
          listener,
          Looper.getMainLooper(),
        )
        isRequestingLocation = true
      }
    } catch (_: SecurityException) {
      isRequestingLocation = false
      stopSelf()
    }
  }

  private fun stopLocationUpdates() {
    isRequestingLocation = false
    fusedCallback?.let { cb ->
      fusedClient?.removeLocationUpdates(cb)
    }
    fusedCallback = null
    fallbackListener?.let { listener ->
      try {
        fallbackLocationManager?.removeUpdates(listener)
      } catch (_: SecurityException) {
        // Ignore if permission was revoked while stopping.
      }
    }
    fallbackListener = null
  }

  private fun ensureNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "Ghi hành trình TripDriver",
        NotificationManager.IMPORTANCE_LOW,
      ).apply {
        description = "Hiển thị quãng đường và thời gian đang đo dưới nền"
        setShowBadge(false)
      }
      notificationManager.createNotificationChannel(channel)
    }
  }

  private fun buildNotification(state: ActiveTripState): Notification {
    val openAppIntent = Intent(this, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val openPendingIntent = PendingIntent.getActivity(
      this,
      101,
      openAppIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val toggleIntent = Intent(this, TripTrackingService::class.java).apply {
      action = ACTION_NOTIFICATION_TOGGLE_PAUSE
    }
    val togglePendingIntent = PendingIntent.getService(
      this,
      102,
      toggleIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val kmText = TripFormatters.formatDistanceKm(state.distanceMeters, decimals = 2)
    val timeText = TripFormatters.formatStopwatch(state.activeDurationMillis)
    val restText = TripFormatters.formatStopwatch(state.restDurationMillis)

    val title = if (state.phase == TripPhase.PAUSED) {
      "TripDriver • Đang nghỉ"
    } else {
      "TripDriver • Đang ghi hành trình"
    }
    val body = if (state.phase == TripPhase.PAUSED) {
      "$kmText km  •  Đi: $timeText  •  Nghỉ: $restText"
    } else {
      "$kmText km  •  $timeText"
    }
    val toggleLabel = if (state.phase == TripPhase.PAUSED) "Tiếp tục" else "Tạm dừng"

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_stat_odochron)
      .setContentTitle(title)
      .setContentText(body)
      .setContentIntent(openPendingIntent)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setSilent(true)
      .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
      .addAction(0, toggleLabel, togglePendingIntent)
      .build()
  }

  companion object {
    @Volatile
    var isRunning: Boolean = false
      private set

    const val ACTION_START = "com.nguyen.tripdriver.action.START"
    const val ACTION_PAUSE = "com.nguyen.tripdriver.action.PAUSE"
    const val ACTION_RESUME = "com.nguyen.tripdriver.action.RESUME"
    const val ACTION_STOP = "com.nguyen.tripdriver.action.STOP"
    const val ACTION_NOTIFICATION_TOGGLE_PAUSE = "com.nguyen.tripdriver.action.TOGGLE_PAUSE"

    private const val CHANNEL_ID = "tripdriver_trip_tracking"
    private const val NOTIFICATION_ID = 7001
  }
}
