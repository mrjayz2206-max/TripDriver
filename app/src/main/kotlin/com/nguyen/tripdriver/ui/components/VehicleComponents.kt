/*
 * Copyright (c) 2026 Nguyên. All rights reserved.
 */

package com.nguyen.tripdriver.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nguyen.tripdriver.domain.AppMetadata
import com.nguyen.tripdriver.domain.TripFormatters
import com.nguyen.tripdriver.domain.TripRecord
import com.nguyen.tripdriver.domain.VehicleType
import com.nguyen.tripdriver.ui.theme.GoldAccentDark
import com.nguyen.tripdriver.ui.theme.GoldAccentLight
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun motorbikeAccentColor(): Color =
  if (isSystemInDarkTheme()) GoldAccentDark else GoldAccentLight

@Composable
fun VehicleBadge(
  modifier: Modifier = Modifier,
  compact: Boolean = false,
) {
  val accent = motorbikeAccentColor()
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(999.dp),
    color = accent.copy(alpha = 0.14f),
    border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = if (compact) 8.dp else 10.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(
        imageVector = Icons.Rounded.TwoWheeler,
        contentDescription = null,
        tint = accent,
        modifier = Modifier.size(if (compact) 14.dp else 16.dp),
      )
      Text(
        text = VehicleType.MOTORBIKE.displayName,
        style = MaterialTheme.typography.labelMedium,
        color = accent,
        fontWeight = FontWeight.SemiBold,
      )
    }
  }
}

/**
 * Confirmation dialog shown when pressing Stop: provides strictly 2 choices — Lưu chuyến or Bỏ chuyến.
 */
@Composable
fun FinishTripDialog(
  distanceMeters: Double,
  activeDurationMillis: Long,
  restDurationMillis: Long,
  maxSpeedKmh: Double,
  onSave: () -> Unit,
  onDiscard: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = "Kết thúc hành trình",
        style = MaterialTheme.typography.titleLarge,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.surfaceContainer,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            SummaryMetricRow(
              label = "Quãng đường",
              value = "${TripFormatters.formatDistanceKm(distanceMeters)} km",
            )
            SummaryMetricRow(
              label = "Thời gian đi",
              value = TripFormatters.formatStopwatch(activeDurationMillis),
            )
            SummaryMetricRow(
              label = "Thời gian nghỉ",
              value = TripFormatters.formatStopwatch(restDurationMillis),
            )
            SummaryMetricRow(
              label = "Tốc độ tối đa",
              value = "${TripFormatters.formatSpeedKmh(maxSpeedKmh)} km/h",
            )
          }
        }
      }
    },
    confirmButton = {
      Button(onClick = onSave) {
        Text("Lưu chuyến")
      }
    },
    dismissButton = {
      OutlinedButton(
        onClick = onDiscard,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
        colors = ButtonDefaults.outlinedButtonColors(
          contentColor = MaterialTheme.colorScheme.error,
        ),
      ) {
        Text("Bỏ chuyến")
      }
    },
  )
}

@Composable
private fun SummaryMetricRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

@Composable
fun TripEntryDialog(
  initialDate: LocalDate,
  existingTrip: TripRecord?,
  onDismiss: () -> Unit,
  onConfirm: (
    date: LocalDate,
    startTime: LocalTime,
    durationMinutes: Int,
    restMinutes: Int,
    distanceKm: Double,
  ) -> Unit,
) {
  val dateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
  var dateText by remember {
    val date = existingTrip?.let {
      Instant.ofEpochMilli(it.startTimeMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    } ?: initialDate
    mutableStateOf(date.format(dateFormatter))
  }
  var startTimeText by remember {
    val time = existingTrip?.let {
      Instant.ofEpochMilli(it.startTimeMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
    } ?: LocalTime.now().withSecond(0).withNano(0)
    mutableStateOf(time.format(DateTimeFormatter.ofPattern("HH:mm")))
  }
  var distanceText by remember {
    mutableStateOf(
      existingTrip?.let { TripFormatters.formatDistanceKm(it.distanceMeters) } ?: "",
    )
  }
  var durationMinutesText by remember {
    mutableStateOf(
      existingTrip?.let { ((it.activeDurationMillis / 60_000L).coerceAtLeast(1L)).toString() } ?: "",
    )
  }
  var restMinutesText by remember {
    mutableStateOf(
      existingTrip?.let { (it.restDurationMillis / 60_000L).toString() } ?: "0",
    )
  }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = if (existingTrip == null) "Thêm chuyến đi" else "Chỉnh sửa chuyến đi",
        style = MaterialTheme.typography.titleLarge,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (existingTrip == null) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedTextField(
              value = dateText,
              onValueChange = {
                dateText = it
                errorMessage = null
              },
              label = { Text("Ngày (dd/MM/yyyy)") },
              singleLine = true,
              modifier = Modifier.weight(1.2f),
            )
            OutlinedTextField(
              value = startTimeText,
              onValueChange = {
                startTimeText = it
                errorMessage = null
              },
              label = { Text("Giờ (HH:mm)") },
              singleLine = true,
              modifier = Modifier.weight(0.8f),
            )
          }
        }

        OutlinedTextField(
          value = distanceText,
          onValueChange = {
            distanceText = it.replace(',', '.')
            errorMessage = null
          },
          label = { Text("Quãng đường (km)") },
          placeholder = { Text("12.5") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth(),
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedTextField(
            value = durationMinutesText,
            onValueChange = {
              durationMinutesText = it.filter { ch -> ch.isDigit() }
              errorMessage = null
            },
            label = { Text("Thời gian đi (phút)") },
            placeholder = { Text("30") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = restMinutesText,
            onValueChange = {
              restMinutesText = it.filter { ch -> ch.isDigit() }
              errorMessage = null
            },
            label = { Text("Nghỉ (phút)") },
            placeholder = { Text("0") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
          )
        }

        if (errorMessage != null) {
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = errorMessage!!,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val parsedDate = runCatching {
            LocalDate.parse(dateText.trim(), dateFormatter)
          }.getOrNull()
          val parsedTime = runCatching {
            LocalTime.parse(startTimeText.trim(), DateTimeFormatter.ofPattern("HH:mm"))
          }.getOrNull()
          val parsedKm = distanceText.trim().toDoubleOrNull()
          val parsedMinutes = durationMinutesText.trim().toIntOrNull()
          val parsedRest = restMinutesText.trim().ifEmpty { "0" }.toIntOrNull()

          when {
            parsedDate == null -> errorMessage = "Ngày không hợp lệ (đúng dạng dd/MM/yyyy)."
            parsedTime == null -> errorMessage = "Giờ không hợp lệ (đúng dạng HH:mm, vd 08:30)."
            parsedKm == null || parsedKm < 0.0 || parsedKm > 2000.0 ->
              errorMessage = "Nhập quãng đường hợp lệ (0 - 2000 km)."
            parsedMinutes == null || parsedMinutes !in 1..1440 ->
              errorMessage = "Nhập thời gian đi hợp lệ (1 - 1440 phút)."
            parsedRest == null || parsedRest !in 0..1440 ->
              errorMessage = "Nhập thời gian nghỉ hợp lệ (0 - 1440 phút)."
            else -> onConfirm(
              parsedDate,
              parsedTime,
              parsedMinutes,
              parsedRest,
              parsedKm,
            )
          }
        },
      ) {
        Text(if (existingTrip == null) "Lưu" else "Cập nhật")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Đóng")
      }
    },
  )
}

@Composable
fun AppCopyrightFooter(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Text(
    text = AppMetadata.footerAttribution,
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
    textAlign = TextAlign.Center,
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(8.dp))
      .clickable(onClick = onClick)
      .padding(vertical = 8.dp),
  )
}

@Composable
fun AboutAppDialog(onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = AppMetadata.appName,
          style = MaterialTheme.typography.headlineMedium,
        )
        Surface(
          shape = RoundedCornerShape(999.dp),
          color = MaterialTheme.colorScheme.primaryContainer,
        ) {
          Text(
            text = AppMetadata.versionDisplay,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
          )
        }
      }
    },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.surfaceContainer,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            SummaryMetricRow(label = "Tác giả", value = AppMetadata.authorName)
            SummaryMetricRow(label = "Phiên bản", value = AppMetadata.versionDisplay)
            SummaryMetricRow(label = "Package ID", value = AppMetadata.packageName)
          }
        }

        Text(
          text = AppMetadata.copyrightNotice,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.primary,
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Text(
          text = "LỊCH SỬ PHIÊN BẢN",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        AppMetadata.releaseHistory.forEach { release ->
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
              text = "v${release.versionName}",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = release.summary,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Đóng")
      }
    },
  )
}
