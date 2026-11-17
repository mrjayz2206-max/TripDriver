/*
 * Copyright (c) 2026 Nguyên. All rights reserved.
 */

package com.nguyen.tripdriver.ui.tracker

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.GpsNotFixed
import androidx.compose.material.icons.rounded.GpsOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nguyen.tripdriver.domain.AppMetadata
import com.nguyen.tripdriver.domain.DailySummary
import com.nguyen.tripdriver.domain.TripFormatters
import com.nguyen.tripdriver.domain.TripRecord
import com.nguyen.tripdriver.tracking.ActiveTripState
import com.nguyen.tripdriver.tracking.GpsSignalStatus
import com.nguyen.tripdriver.tracking.TripPhase
import com.nguyen.tripdriver.tracking.TripTrackerManager
import com.nguyen.tripdriver.ui.components.AboutAppDialog
import com.nguyen.tripdriver.ui.components.AppCopyrightFooter
import com.nguyen.tripdriver.ui.components.FinishTripDialog
import com.nguyen.tripdriver.ui.components.VehicleBadge
import com.nguyen.tripdriver.ui.components.motorbikeAccentColor
import com.nguyen.tripdriver.ui.theme.SignalGoodColor
import com.nguyen.tripdriver.ui.theme.SignalOffColor
import com.nguyen.tripdriver.ui.theme.SignalWeakColor

@Composable
fun TrackerRoute(
  viewModel: TrackerViewModel,
  onOpenLogbook: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val activeState by viewModel.activeState.collectAsStateWithLifecycle()
  val todaySummary by viewModel.todaySummary.collectAsStateWithLifecycle()
  val lastSavedTrip by viewModel.lastSavedTrip.collectAsStateWithLifecycle()

  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  var hasLocationPermission by remember {
    mutableStateOf(TripTrackerManager.hasLocationPermission(context))
  }
  var isLocationServiceEnabled by remember {
    mutableStateOf(TripTrackerManager.isLocationEnabled(context))
  }
  var showPermissionDeniedSettingsPrompt by remember { mutableStateOf(false) }

  DisposableEffect(lifecycleOwner, context) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        hasLocationPermission = TripTrackerManager.hasLocationPermission(context)
        isLocationServiceEnabled = TripTrackerManager.isLocationEnabled(context)
        if (hasLocationPermission) {
          showPermissionDeniedSettingsPrompt = false
        }
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  // Granting permissions ONLY unlocks ready state — never auto-starts tracking.
  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions(),
  ) { results ->
    val fineGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
    val coarseGranted = results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    val granted = fineGranted || coarseGranted || TripTrackerManager.hasLocationPermission(context)
    hasLocationPermission = granted
    isLocationServiceEnabled = TripTrackerManager.isLocationEnabled(context)

    if (granted) {
      showPermissionDeniedSettingsPrompt = false
    } else {
      val activity = context.findActivity()
      val canAskAgain = activity != null && (
        ActivityCompat.shouldShowRequestPermissionRationale(
          activity,
          Manifest.permission.ACCESS_FINE_LOCATION,
        ) || ActivityCompat.shouldShowRequestPermissionRationale(
          activity,
          Manifest.permission.ACCESS_COARSE_LOCATION,
        )
      )
      showPermissionDeniedSettingsPrompt = !canAskAgain
    }
  }

  val launchPermissionRequest: () -> Unit = {
    val permissions = buildList {
      add(Manifest.permission.ACCESS_FINE_LOCATION)
      add(Manifest.permission.ACCESS_COARSE_LOCATION)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
      ) {
        add(Manifest.permission.POST_NOTIFICATIONS)
      }
    }.toTypedArray()
    permissionLauncher.launch(permissions)
  }

  // Prompt once on fresh install without auto-starting any trip.
  LaunchedEffect(Unit) {
    if (!TripTrackerManager.hasLocationPermission(context)) {
      val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      if (!prefs.getBoolean(KEY_INITIAL_PERMISSION_PROMPTED, false)) {
        prefs.edit().putBoolean(KEY_INITIAL_PERMISSION_PROMPTED, true).apply()
        launchPermissionRequest()
      }
    }
  }

  val onStartButtonClicked: () -> Unit = {
    viewModel.consumeSavedTripBanner()
    isLocationServiceEnabled = TripTrackerManager.isLocationEnabled(context)
    if (TripTrackerManager.hasLocationPermission(context)) {
      hasLocationPermission = true
      viewModel.startTrip()
    } else {
      launchPermissionRequest()
    }
  }

  TrackerScreenContent(
    activeState = activeState,
    todaySummary = todaySummary,
    lastSavedTrip = lastSavedTrip,
    hasLocationPermission = hasLocationPermission,
    isLocationServiceEnabled = isLocationServiceEnabled,
    showPermissionDeniedSettingsPrompt = showPermissionDeniedSettingsPrompt,
    onRequestPermission = launchPermissionRequest,
    onStartClick = onStartButtonClicked,
    onPauseClick = viewModel::pauseTrip,
    onResumeClick = {
      if (TripTrackerManager.hasLocationPermission(context)) {
        viewModel.resumeTrip()
      } else {
        launchPermissionRequest()
      }
    },
    onSaveTrip = viewModel::finishAndSaveTrip,
    onDiscardTrip = viewModel::discardTrip,
    onDismissSavedBanner = viewModel::consumeSavedTripBanner,
    onOpenLocationSettings = {
      context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    },
    onOpenAppSettings = {
      val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
      }
      context.startActivity(intent)
    },
    onOpenLogbook = onOpenLogbook,
    modifier = modifier,
  )
}

@Composable
internal fun TrackerScreenContent(
  activeState: ActiveTripState,
  todaySummary: DailySummary,
  lastSavedTrip: TripRecord?,
  hasLocationPermission: Boolean,
  isLocationServiceEnabled: Boolean,
  showPermissionDeniedSettingsPrompt: Boolean,
  onRequestPermission: () -> Unit,
  onStartClick: () -> Unit,
  onPauseClick: () -> Unit,
  onResumeClick: () -> Unit,
  onSaveTrip: () -> Unit,
  onDiscardTrip: () -> Unit,
  onDismissSavedBanner: () -> Unit,
  onOpenLocationSettings: () -> Unit,
  onOpenAppSettings: () -> Unit,
  onOpenLogbook: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var showFinishDialog by remember { mutableStateOf(false) }
  var showAboutDialog by remember { mutableStateOf(false) }
  val accentColor = motorbikeAccentColor()

  if (showFinishDialog && activeState.isSessionActive) {
    FinishTripDialog(
      distanceMeters = activeState.distanceMeters,
      activeDurationMillis = activeState.activeDurationMillis,
      restDurationMillis = activeState.restDurationMillis,
      maxSpeedKmh = activeState.maxSpeedKmh,
      onSave = {
        showFinishDialog = false
        onSaveTrip()
      },
      onDiscard = {
        showFinishDialog = false
        onDiscardTrip()
      },
      onDismiss = { showFinishDialog = false },
    )
  }

  if (showAboutDialog) {
    AboutAppDialog(onDismiss = { showAboutDialog = false })
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // Top Header: App Name + GPS Status Pill (no info icon)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = AppMetadata.appName,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      GpsSignalBadge(
        activeState = activeState,
        hasLocationPermission = hasLocationPermission,
        isLocationServiceEnabled = isLocationServiceEnabled,
      )
    }

    // Saved trip confirmation banner
    AnimatedVisibility(visible = lastSavedTrip != null && !activeState.isSessionActive) {
      lastSavedTrip?.let { saved ->
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = SignalGoodColor.copy(alpha = 0.14f),
          border = BorderStroke(1.dp, SignalGoodColor.copy(alpha = 0.35f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.weight(1f),
            ) {
              Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = SignalGoodColor,
                modifier = Modifier.size(20.dp),
              )
              Text(
                text = "Đã lưu ${TripFormatters.formatDistanceKm(saved.distanceMeters)} km • ${TripFormatters.formatDurationSmart(saved.activeDurationMillis)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
            TextButton(onClick = onDismissSavedBanner) {
              Text("Đóng")
            }
          }
        }
      }
    }

    // Location Off / Permission Warning Banner
    if (!hasLocationPermission) {
      AlertActionCard(
        title = "Cần quyền truy cập vị trí",
        message = if (showPermissionDeniedSettingsPrompt) {
          "Bạn đã tắt quyền vị trí. Vào Cài đặt ứng dụng để cấp quyền GPS thì app mới đo được quãng đường."
        } else {
          "Ứng dụng chỉ bật GPS khi bạn bấm Bắt đầu để đo quãng đường chính xác và tiết kiệm pin."
        },
        actionLabel = if (showPermissionDeniedSettingsPrompt) "Mở Cài đặt" else "Cấp quyền vị trí",
        onAction = if (showPermissionDeniedSettingsPrompt) onOpenAppSettings else onRequestPermission,
      )
    } else if (!isLocationServiceEnabled) {
      AlertActionCard(
        title = "GPS trên máy đang tắt",
        message = "Hãy bật Vị trí (Location/GPS) của điện thoại trước khi chạy xe để đo chính xác số km.",
        actionLabel = "Bật Vị trí",
        onAction = onOpenLocationSettings,
      )
    }

    // Main Cockpit Card
    CockpitCard(
      activeState = activeState,
      accent = accentColor,
    )

    // Primary Start / Pause / Resume / Stop Buttons
    TrackerActionButtons(
      phase = activeState.phase,
      accentColor = accentColor,
      onStartClick = onStartClick,
      onPauseClick = onPauseClick,
      onResumeClick = onResumeClick,
      onStopClick = { showFinishDialog = true },
    )

    // Today's Quick Summary Strip (4 metrics)
    TodaySummaryCard(
      todaySummary = todaySummary,
      onOpenLogbook = onOpenLogbook,
    )

    AppCopyrightFooter(onClick = { showAboutDialog = true })
  }
}

@Composable
private fun GpsSignalBadge(
  activeState: ActiveTripState,
  hasLocationPermission: Boolean,
  isLocationServiceEnabled: Boolean,
) {
  val (label, color, icon) = when {
    !hasLocationPermission || !isLocationServiceEnabled ||
      activeState.gpsSignalStatus == GpsSignalStatus.LOCATION_OFF ->
      Triple("GPS tắt", SignalOffColor, Icons.Rounded.GpsOff)
    activeState.phase == TripPhase.PAUSED ->
      Triple("Đang nghỉ", SignalWeakColor, Icons.Rounded.GpsNotFixed)
    activeState.phase == TripPhase.IDLE ->
      Triple("Sẵn sàng", SignalGoodColor, Icons.Rounded.GpsFixed)
    activeState.gpsSignalStatus == GpsSignalStatus.GOOD -> {
      val acc = activeState.lastAccuracyMeters?.toInt()
      Triple(if (acc != null) "GPS tốt ±${acc}m" else "GPS tốt", SignalGoodColor, Icons.Rounded.GpsFixed)
    }
    activeState.gpsSignalStatus == GpsSignalStatus.WEAK ->
      Triple("GPS yếu", SignalWeakColor, Icons.Rounded.GpsNotFixed)
    else ->
      Triple("Đang tìm GPS...", SignalWeakColor, Icons.Rounded.GpsNotFixed)
  }

  Surface(
    shape = RoundedCornerShape(999.dp),
    color = color.copy(alpha = 0.14f),
    border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(14.dp),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        fontWeight = FontWeight.SemiBold,
      )
    }
  }
}

@Composable
private fun AlertActionCard(
  title: String,
  message: String,
  actionLabel: String,
  onAction: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.WarningAmber,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.error,
          modifier = Modifier.size(20.dp),
        )
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onErrorContainer,
        )
      }
      Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f),
      )
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Button(
          onClick = onAction,
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
        ) {
          Text(actionLabel)
        }
      }
    }
  }
}

@Composable
private fun CockpitCard(
  activeState: ActiveTripState,
  accent: Color,
) {
  val pulseTransition = rememberInfiniteTransition(label = "trackingPulse")
  val pulseAlpha by pulseTransition.animateFloat(
    initialValue = 0.35f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 900),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "pulseAlpha",
  )

  Surface(
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    border = BorderStroke(
      width = 1.2.dp,
      color = when (activeState.phase) {
        TripPhase.TRACKING -> accent.copy(alpha = 0.55f)
        TripPhase.PAUSED -> SignalWeakColor.copy(alpha = 0.65f)
        TripPhase.IDLE -> MaterialTheme.colorScheme.outline
      },
    ),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // Status Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        VehicleBadge()

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          val dotColor = when (activeState.phase) {
            TripPhase.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            TripPhase.TRACKING -> accent.copy(alpha = pulseAlpha)
            TripPhase.PAUSED -> SignalWeakColor.copy(alpha = pulseAlpha)
          }
          Box(
            modifier = Modifier
              .size(9.dp)
              .clip(CircleShape)
              .background(dotColor),
          )
          Text(
            text = when (activeState.phase) {
              TripPhase.IDLE -> "Chưa bắt đầu"
              TripPhase.TRACKING -> "Đang ghi hành trình"
              TripPhase.PAUSED -> "Đang tạm dừng (tính giờ nghỉ)"
            },
            style = MaterialTheme.typography.labelLarge,
            color = when (activeState.phase) {
              TripPhase.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
              TripPhase.TRACKING -> accent
              TripPhase.PAUSED -> SignalWeakColor
            },
          )
        }
      }

      // Hero Distance & Duration Readout
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          text = "QUÃNG ĐƯỜNG ĐÃ ĐI",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          letterSpacing = 1.2.sp,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
          verticalAlignment = Alignment.Bottom,
          horizontalArrangement = Arrangement.Center,
        ) {
          Text(
            text = TripFormatters.formatDistanceKm(activeState.distanceMeters, decimals = 2),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "km",
            style = MaterialTheme.typography.titleLarge,
            color = accent,
            modifier = Modifier.padding(bottom = 8.dp),
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
          text = TripFormatters.formatStopwatch(activeState.activeDurationMillis),
          style = MaterialTheme.typography.displayMedium,
          color = if (activeState.phase == TripPhase.TRACKING) {
            accent
          } else {
            MaterialTheme.colorScheme.onSurfaceVariant
          },
        )
        Text(
          text = "Thời gian đi",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      // Speed Row (3 metrics) + Time Row (2 metrics)
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
          TelemetryMiniMetric(
            label = "Tốc độ",
            value = "${TripFormatters.formatSpeedKmh(activeState.currentSpeedKmh)} km/h",
            modifier = Modifier.weight(1f),
          )
          TelemetryMiniMetric(
            label = "Trung bình",
            value = "${TripFormatters.formatSpeedKmh(activeState.avgSpeedKmh)} km/h",
            modifier = Modifier.weight(1f),
          )
          TelemetryMiniMetric(
            label = "Tối đa",
            value = "${TripFormatters.formatSpeedKmh(activeState.maxSpeedKmh)} km/h",
            modifier = Modifier.weight(1f),
          )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
          TelemetryMiniMetric(
            label = "Thực chạy",
            value = TripFormatters.formatStopwatch(activeState.movingDurationMillis),
            modifier = Modifier.weight(1f),
          )
          TelemetryMiniMetric(
            label = "Thời gian nghỉ",
            value = TripFormatters.formatStopwatch(activeState.restDurationMillis),
            valueColor = if (activeState.phase == TripPhase.PAUSED) {
              SignalWeakColor
            } else {
              MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
          )
        }
      }
    }
  }
}

@Composable
private fun TelemetryMiniMetric(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
  valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.titleMedium,
      color = valueColor,
    )
  }
}

@Composable
private fun TrackerActionButtons(
  phase: TripPhase,
  accentColor: Color,
  onStartClick: () -> Unit,
  onPauseClick: () -> Unit,
  onResumeClick: () -> Unit,
  onStopClick: () -> Unit,
) {
  val primaryButtonContentColor = MaterialTheme.colorScheme.onPrimary
  when (phase) {
    TripPhase.IDLE -> {
      Button(
        onClick = onStartClick,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = accentColor,
          contentColor = primaryButtonContentColor,
        ),
        modifier = Modifier
          .fillMaxWidth()
          .height(58.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.PlayArrow,
          contentDescription = null,
          modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "BẮT ĐẦU HÀNH TRÌNH",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
      }
    }
    TripPhase.TRACKING -> {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedButton(
          onClick = onPauseClick,
          shape = RoundedCornerShape(18.dp),
          border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
          modifier = Modifier
            .weight(1f)
            .height(56.dp),
        ) {
          Icon(imageVector = Icons.Rounded.Pause, contentDescription = null)
          Spacer(modifier = Modifier.width(6.dp))
          Text("Tạm dừng", style = MaterialTheme.typography.titleMedium)
        }
        Button(
          onClick = onStopClick,
          shape = RoundedCornerShape(18.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
          modifier = Modifier
            .weight(1f)
            .height(56.dp),
        ) {
          Icon(imageVector = Icons.Rounded.Stop, contentDescription = null)
          Spacer(modifier = Modifier.width(6.dp))
          Text("Kết thúc", style = MaterialTheme.typography.titleMedium)
        }
      }
    }
    TripPhase.PAUSED -> {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Button(
          onClick = onResumeClick,
          shape = RoundedCornerShape(18.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = accentColor,
            contentColor = primaryButtonContentColor,
          ),
          modifier = Modifier
            .weight(1f)
            .height(56.dp),
        ) {
          Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null)
          Spacer(modifier = Modifier.width(6.dp))
          Text("Tiếp tục", style = MaterialTheme.typography.titleMedium)
        }
        Button(
          onClick = onStopClick,
          shape = RoundedCornerShape(18.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
          modifier = Modifier
            .weight(1f)
            .height(56.dp),
        ) {
          Icon(imageVector = Icons.Rounded.Stop, contentDescription = null)
          Spacer(modifier = Modifier.width(6.dp))
          Text("Kết thúc", style = MaterialTheme.typography.titleMedium)
        }
      }
    }
  }
}

@Composable
private fun TodaySummaryCard(
  todaySummary: DailySummary,
  onOpenLogbook: () -> Unit,
) {
  val dateStr = remember(todaySummary.date) {
    TripFormatters.formatFullDateVi(todaySummary.date, pattern = "EEEE, dd/MM")
  }

  Surface(
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(20.dp))
      .clickable(onClick = onOpenLogbook),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text(
            text = "TỔNG KẾT HÔM NAY",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = dateStr,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
            text = "Nhật ký",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
          )
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        TodayMetricItem(
          label = "Quãng đường",
          value = "${TripFormatters.formatDistanceKm(todaySummary.totalDistanceMeters)} km",
          modifier = Modifier.weight(1f),
        )
        TodayMetricItem(
          label = "Tốc độ trung bình",
          value = "${TripFormatters.formatSpeedKmh(todaySummary.avgSpeedKmh)} km/h",
          modifier = Modifier.weight(1f),
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        TodayMetricItem(
          label = "Thời gian đi",
          value = TripFormatters.formatDurationSmart(todaySummary.totalDurationMillis),
          modifier = Modifier.weight(1f),
        )
        TodayMetricItem(
          label = "Thời gian nghỉ",
          value = TripFormatters.formatDurationSmart(todaySummary.totalRestDurationMillis),
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
private fun TodayMetricItem(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

private const val PREFS_NAME = "tripdriver_ui_prefs"
private const val KEY_INITIAL_PERMISSION_PROMPTED = "initial_permission_prompted"

private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}
