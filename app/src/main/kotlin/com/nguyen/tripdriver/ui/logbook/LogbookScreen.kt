/*
 * Copyright (c) 2026 Nguyên. All rights reserved.
 */

package com.nguyen.tripdriver.ui.logbook

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.TableRows
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nguyen.tripdriver.domain.DailySummary
import com.nguyen.tripdriver.domain.MonthlySummary
import com.nguyen.tripdriver.domain.TripFormatters
import com.nguyen.tripdriver.domain.TripRecord
import com.nguyen.tripdriver.ui.components.AboutAppDialog
import com.nguyen.tripdriver.ui.components.AppCopyrightFooter
import com.nguyen.tripdriver.ui.components.TripEntryDialog
import com.nguyen.tripdriver.ui.components.VehicleBadge
import com.nguyen.tripdriver.ui.components.motorbikeAccentColor
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun LogbookRoute(
  viewModel: LogbookViewModel,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  LogbookScreenContent(
    uiState = uiState,
    onPreviousMonth = viewModel::previousMonth,
    onNextMonth = viewModel::nextMonth,
    onJumpCurrentMonth = viewModel::jumpToCurrentMonth,
    onSelectDate = viewModel::selectDate,
    onSetFrameMode = viewModel::setFrameMode,
    onUpdateTrip = viewModel::updateTrip,
    onDeleteTrip = viewModel::deleteTrip,
    modifier = modifier,
  )
}

@Composable
internal fun LogbookScreenContent(
  uiState: LogbookUiState,
  onPreviousMonth: () -> Unit,
  onNextMonth: () -> Unit,
  onJumpCurrentMonth: () -> Unit,
  onSelectDate: (LocalDate) -> Unit,
  onSetFrameMode: (LogbookFrameMode) -> Unit,
  onUpdateTrip: (
    existing: TripRecord,
    durationMinutes: Int,
    restMinutes: Int,
    distanceKm: Double,
  ) -> Unit,
  onDeleteTrip: (Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  var editingTrip by remember { mutableStateOf<TripRecord?>(null) }
  var deletingTrip by remember { mutableStateOf<TripRecord?>(null) }
  var showAboutDialog by remember { mutableStateOf(false) }

  if (showAboutDialog) {
    AboutAppDialog(onDismiss = { showAboutDialog = false })
  }

  editingTrip?.let { trip ->
    TripEntryDialog(
      initialDate = uiState.selectedDate,
      existingTrip = trip,
      onDismiss = { editingTrip = null },
      onConfirm = { _, _, minutes, restMinutes, km ->
        editingTrip = null
        onUpdateTrip(trip, minutes, restMinutes, km)
      },
    )
  }

  deletingTrip?.let { trip ->
    AlertDialog(
      onDismissRequest = { deletingTrip = null },
      title = { Text("Xóa chuyến đi?") },
      text = {
        Text(
          "Xóa chuyến đi ${TripFormatters.formatDistanceKm(trip.distanceMeters)} km • ${TripFormatters.formatDurationSmart(trip.activeDurationMillis)}?",
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            deletingTrip = null
            onDeleteTrip(trip.id)
          },
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
          Text("Xóa")
        }
      },
      dismissButton = {
        TextButton(onClick = { deletingTrip = null }) {
          Text("Hủy")
        }
      },
    )
  }

  val selectedDayTrips = uiState.selectedDaySummary?.trips.orEmpty()

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // Clean single title: Nhật ký hành trình (no subtitle, no info/share/add buttons)
    item {
      Text(
        text = "Nhật ký hành trình",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }

    // Month Selector Strip
    item {
      MonthSelectorCard(
        selectedMonth = uiState.selectedMonth,
        onPreviousMonth = onPreviousMonth,
        onNextMonth = onNextMonth,
        onJumpCurrentMonth = onJumpCurrentMonth,
      )
    }

    // Monthly Summary Card
    item {
      MonthlySummaryCard(summary = uiState.monthlySummary)
    }

    // Frame Mode Switcher Header
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = if (uiState.frameMode == LogbookFrameMode.TABLE) {
            "Khung bảng theo ngày"
          } else {
            "Khung lịch trong tháng"
          },
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )

        FrameModeToggle(
          mode = uiState.frameMode,
          onModeChange = onSetFrameMode,
        )
      }
    }

    // Main Daily Log Frame (Table or Calendar)
    item {
      when (uiState.frameMode) {
        LogbookFrameMode.TABLE -> {
          DailyLogTableFrame(
            monthlySummary = uiState.monthlySummary,
            selectedDate = uiState.selectedDate,
            onSelectDate = onSelectDate,
          )
        }
        LogbookFrameMode.CALENDAR -> {
          MonthlyCalendarFrame(
            yearMonth = uiState.selectedMonth,
            monthlySummary = uiState.monthlySummary,
            selectedDate = uiState.selectedDate,
            onSelectDate = onSelectDate,
          )
        }
      }
    }

    // Selected Day Detail Header (no Ghi bù button)
    item {
      val daySummary = uiState.selectedDaySummary
      val dateFormatted = remember(uiState.selectedDate) {
        TripFormatters.formatFullDateVi(uiState.selectedDate)
      }

      Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
          verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          Text(
            text = dateFormatted,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = if (daySummary == null || daySummary.tripCount == 0) {
              "Chưa có chuyến đi nào trong ngày"
            } else {
              "${TripFormatters.formatDistanceKm(daySummary.totalDistanceMeters)} km • Đi: ${TripFormatters.formatDurationSmart(daySummary.totalDurationMillis)} • Nghỉ: ${TripFormatters.formatDurationSmart(daySummary.totalRestDurationMillis)}"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
          )
        }
      }
    }

    // Individual trips for selected day
    if (selectedDayTrips.isNotEmpty()) {
      items(selectedDayTrips, key = { it.id }) { trip ->
        TripDetailRowCard(
          trip = trip,
          onEdit = { editingTrip = trip },
          onDelete = { deletingTrip = trip },
        )
      }
    }

    item {
      AppCopyrightFooter(onClick = { showAboutDialog = true })
    }
  }
}

@Composable
private fun MonthSelectorCard(
  selectedMonth: YearMonth,
  onPreviousMonth: () -> Unit,
  onNextMonth: () -> Unit,
  onJumpCurrentMonth: () -> Unit,
) {
  val isCurrentMonth = selectedMonth == YearMonth.now()
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onPreviousMonth) {
        Icon(imageVector = Icons.Rounded.ChevronLeft, contentDescription = "Tháng trước")
      }
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = "Tháng ${selectedMonth.monthValue} / ${selectedMonth.year}",
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
        if (!isCurrentMonth) {
          TextButton(onClick = onJumpCurrentMonth) {
            Text("Hiện tại")
          }
        }
      }
      IconButton(onClick = onNextMonth) {
        Icon(imageVector = Icons.Rounded.ChevronRight, contentDescription = "Tháng sau")
      }
    }
  }
}

@Composable
private fun MonthlySummaryCard(summary: MonthlySummary) {
  Surface(
    shape = RoundedCornerShape(22.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
      ) {
        Column {
          Text(
            text = "TỔNG THÁNG ${summary.yearMonth.monthValue}/${summary.yearMonth.year}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Row(verticalAlignment = Alignment.Bottom) {
            Text(
              text = TripFormatters.formatDistanceKm(summary.totalDistanceMeters, decimals = 1),
              style = MaterialTheme.typography.displayMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
              text = "km",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(bottom = 5.dp),
            )
          }
        }

        Column(horizontalAlignment = Alignment.End) {
          Text(
            text = TripFormatters.formatDurationSmart(summary.totalDurationMillis),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = "${summary.activeDaysCount} ngày chạy",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        MonthlyStatTile(
          label = "Số ngày chạy",
          value = "${summary.activeDaysCount} ngày",
          modifier = Modifier.weight(1f),
        )
        MonthlyStatTile(
          label = "TB / ngày chạy",
          value = "${TripFormatters.formatDistanceKm(summary.avgDistancePerActiveDayMeters, decimals = 1)} km",
          modifier = Modifier.weight(1f),
        )
        MonthlyStatTile(
          label = "Chuyến xa nhất",
          value = "${TripFormatters.formatDistanceKm(summary.longestTripMeters, decimals = 1)} km",
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
private fun MonthlyStatTile(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = value,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
      )
    }
  }
}

@Composable
private fun FrameModeToggle(
  mode: LogbookFrameMode,
  onModeChange: (LogbookFrameMode) -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Row(modifier = Modifier.padding(2.dp)) {
      ModeIconChip(
        selected = mode == LogbookFrameMode.TABLE,
        icon = Icons.Rounded.TableRows,
        contentDesc = "Khung bảng",
        onClick = { onModeChange(LogbookFrameMode.TABLE) },
      )
      ModeIconChip(
        selected = mode == LogbookFrameMode.CALENDAR,
        icon = Icons.Rounded.CalendarMonth,
        contentDesc = "Khung lịch",
        onClick = { onModeChange(LogbookFrameMode.CALENDAR) },
      )
    }
  }
}

@Composable
private fun ModeIconChip(
  selected: Boolean,
  icon: ImageVector,
  contentDesc: String,
  onClick: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(10.dp),
    color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
    modifier = Modifier
      .clip(RoundedCornerShape(10.dp))
      .clickable(onClick = onClick),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDesc,
      tint = if (selected) {
        MaterialTheme.colorScheme.primary
      } else {
        MaterialTheme.colorScheme.onSurfaceVariant
      },
      modifier = Modifier
        .padding(horizontal = 10.dp, vertical = 6.dp)
        .size(18.dp),
    )
  }
}

/**
 * Framed daily ledger table: Date | Rest | Riding Time | Distance.
 */
@Composable
private fun DailyLogTableFrame(
  monthlySummary: MonthlySummary,
  selectedDate: LocalDate,
  onSelectDate: (LocalDate) -> Unit,
) {
  val dateFmt = remember { DateTimeFormatter.ofPattern("dd/MM") }

  Surface(
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outline),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.surfaceContainer)
          .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "NGÀY",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.weight(1.05f),
        )
        Text(
          text = "NGHỈ",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.End,
          modifier = Modifier.weight(0.9f),
        )
        Text(
          text = "THỜI GIAN",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.End,
          modifier = Modifier.weight(1f),
        )
        Text(
          text = "QUÃNG ĐƯỜNG",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.End,
          modifier = Modifier.weight(1.05f),
        )
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outline)

      if (monthlySummary.dailySummaries.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp, horizontal = 16.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "Chưa có dữ liệu trong Tháng ${monthlySummary.yearMonth.monthValue}/${monthlySummary.yearMonth.year}.\nBấm 'Bắt đầu' ở tab Hành trình để ghi nhận chuyến đi.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
        }
      } else {
        monthlySummary.dailySummaries.forEachIndexed { index, day ->
          val isSelected = day.date == selectedDate
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .background(
                if (isSelected) {
                  MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                } else {
                  Color.Transparent
                },
              )
              .clickable { onSelectDate(day.date) }
              .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1.05f)) {
              Text(
                text = day.date.format(dateFmt),
                style = MaterialTheme.typography.titleMedium,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = TripFormatters.formatShortWeekdayVi(day.date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            Text(
              text = TripFormatters.formatDurationSmart(day.totalRestDurationMillis),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.End,
              modifier = Modifier.weight(0.9f),
            )

            Text(
              text = TripFormatters.formatDurationSmart(day.totalDurationMillis),
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurface,
              textAlign = TextAlign.End,
              modifier = Modifier.weight(1f),
            )

            Text(
              text = "${TripFormatters.formatDistanceKm(day.totalDistanceMeters, decimals = 1)} km",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
              textAlign = TextAlign.End,
              modifier = Modifier.weight(1.05f),
            )
          }

          if (index < monthlySummary.dailySummaries.lastIndex) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // Frame Footer Total
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 11.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Tổng (${monthlySummary.activeDaysCount} ngày)",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.05f),
          )
          Text(
            text = TripFormatters.formatDurationSmart(monthlySummary.totalRestDurationMillis),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.9f),
          )
          Text(
            text = TripFormatters.formatDurationSmart(monthlySummary.totalDurationMillis),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
          )
          Text(
            text = "${TripFormatters.formatDistanceKm(monthlySummary.totalDistanceMeters, decimals = 1)} km",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.05f),
          )
        }
      }
    }
  }
}

/**
 * Framed 7-column monthly calendar showing km traveled directly inside each day cell.
 */
@Composable
private fun MonthlyCalendarFrame(
  yearMonth: YearMonth,
  monthlySummary: MonthlySummary,
  selectedDate: LocalDate,
  onSelectDate: (LocalDate) -> Unit,
) {
  val dayMap = remember(monthlySummary) {
    monthlySummary.dailySummaries.associateBy { it.date }
  }
  val today = remember { LocalDate.now() }
  val firstDayOfWeekIndex = yearMonth.atDay(1).dayOfWeek.value - 1
  val daysInMonth = yearMonth.lengthOfMonth()
  val totalCells = firstDayOfWeekIndex + daysInMonth
  val rowCount = (totalCells + 6) / 7
  val headers = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
  val accentColor = motorbikeAccentColor()

  Surface(
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outline),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(modifier = Modifier.fillMaxWidth()) {
        headers.forEach { h ->
          Text(
            text = h,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
          )
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      for (row in 0 until rowCount) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          for (col in 0 until 7) {
            val cellIndex = row * 7 + col
            val dayNum = cellIndex - firstDayOfWeekIndex + 1
            if (dayNum !in 1..daysInMonth) {
              Spacer(
                modifier = Modifier
                  .weight(1f)
                  .aspectRatio(0.95f),
              )
            } else {
              val date = yearMonth.atDay(dayNum)
              val daySummary: DailySummary? = dayMap[date]
              val isSelected = date == selectedDate
              val isToday = date == today
              val hasTrips = daySummary != null && daySummary.tripCount > 0

              val cellBg = when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                hasTrips -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f)
                else -> Color.Transparent
              }
              val borderModifier = when {
                isSelected -> Modifier.border(
                  width = 1.5.dp,
                  color = MaterialTheme.colorScheme.primary,
                  shape = RoundedCornerShape(10.dp),
                )
                isToday -> Modifier.border(
                  width = 1.dp,
                  color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                  shape = RoundedCornerShape(10.dp),
                )
                else -> Modifier
              }

              Column(
                modifier = Modifier
                  .weight(1f)
                  .aspectRatio(0.95f)
                  .clip(RoundedCornerShape(10.dp))
                  .background(cellBg)
                  .then(borderModifier)
                  .clickable { onSelectDate(date) }
                  .padding(vertical = 4.dp, horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = dayNum.toString(),
                  style = MaterialTheme.typography.labelLarge,
                  color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                  } else if (hasTrips) {
                    MaterialTheme.colorScheme.onSurface
                  } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                  },
                )

                if (daySummary != null && daySummary.tripCount > 0) {
                  Text(
                    text = TripFormatters.formatDistanceKm(daySummary.totalDistanceMeters, decimals = 1),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                  )
                  Box(
                    modifier = Modifier
                      .size(5.dp)
                      .clip(CircleShape)
                      .background(accentColor),
                  )
                } else {
                  Spacer(modifier = Modifier.height(10.dp))
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TripDetailRowCard(
  trip: TripRecord,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
  val zone = remember { ZoneId.systemDefault() }
  val startStr = remember(trip.startTimeMillis) {
    Instant.ofEpochMilli(trip.startTimeMillis).atZone(zone).format(timeFormatter)
  }
  val endStr = remember(trip.endTimeMillis) {
    Instant.ofEpochMilli(trip.endTimeMillis).atZone(zone).format(timeFormatter)
  }

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          VehicleBadge(compact = true)
          Text(
            text = "$startStr – $endStr",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Row {
          IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(
              imageVector = Icons.Rounded.Edit,
              contentDescription = "Chỉnh sửa",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp),
            )
          }
          IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
              imageVector = Icons.Rounded.Delete,
              contentDescription = "Xóa",
              tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column {
          Text(
            text = "Quãng đường",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = "${TripFormatters.formatDistanceKm(trip.distanceMeters)} km",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
        Column {
          Text(
            text = "Đi / Nghỉ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = "${TripFormatters.formatDurationSmart(trip.activeDurationMillis)} / ${TripFormatters.formatDurationSmart(trip.restDurationMillis)}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
        Column(horizontalAlignment = Alignment.End) {
          Text(
            text = "TB / Tối đa",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = "${TripFormatters.formatSpeedKmh(trip.avgSpeedKmh)} / ${TripFormatters.formatSpeedKmh(trip.maxSpeedKmh)} km/h",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}
