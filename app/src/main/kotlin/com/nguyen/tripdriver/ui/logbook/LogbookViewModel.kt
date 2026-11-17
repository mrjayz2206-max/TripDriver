/*
 * Copyright (c) 2026 Nguyên. All rights reserved.
 */

package com.nguyen.tripdriver.ui.logbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nguyen.tripdriver.data.TripRepository
import com.nguyen.tripdriver.domain.DailySummary
import com.nguyen.tripdriver.domain.MonthlySummary
import com.nguyen.tripdriver.domain.TripAggregation
import com.nguyen.tripdriver.domain.TripRecord
import com.nguyen.tripdriver.domain.VehicleType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

enum class LogbookFrameMode {
  TABLE,
  CALENDAR,
}

data class LogbookUiState(
  val selectedMonth: YearMonth = YearMonth.now(),
  val selectedDate: LocalDate = LocalDate.now(),
  val frameMode: LogbookFrameMode = LogbookFrameMode.TABLE,
  val monthlySummary: MonthlySummary = TripAggregation.summarizeMonth(YearMonth.now(), emptyList()),
  val selectedDaySummary: DailySummary? = null,
)

class LogbookViewModel(
  private val repository: TripRepository,
) : ViewModel() {

  private val selectedMonth = MutableStateFlow(YearMonth.now())
  private val selectedDate = MutableStateFlow(LocalDate.now())
  private val frameMode = MutableStateFlow(LogbookFrameMode.TABLE)

  val uiState: StateFlow<LogbookUiState> = combine(
    repository.trips,
    selectedMonth,
    selectedDate,
    frameMode,
  ) { allTrips, month, date, mode ->
    val tripsInMonth = allTrips.filter { trip ->
      YearMonth.from(TripAggregation.localDateOf(trip.startTimeMillis)) == month
    }
    val monthlySummary = TripAggregation.summarizeMonth(month, tripsInMonth)
    val effectiveDate = if (YearMonth.from(date) == month) {
      date
    } else {
      monthlySummary.dailySummaries.firstOrNull()?.date ?: month.atDay(1)
    }
    val selectedDaySummary = monthlySummary.dailySummaries.firstOrNull { it.date == effectiveDate }
      ?: TripAggregation.summarizeDay(effectiveDate, emptyList())

    LogbookUiState(
      selectedMonth = month,
      selectedDate = effectiveDate,
      frameMode = mode,
      monthlySummary = monthlySummary,
      selectedDaySummary = selectedDaySummary,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = LogbookUiState(),
  )

  fun previousMonth() {
    val prev = selectedMonth.value.minusMonths(1)
    selectedMonth.value = prev
    val today = LocalDate.now()
    selectedDate.value = if (YearMonth.from(today) == prev) today else prev.atDay(1)
  }

  fun nextMonth() {
    val next = selectedMonth.value.plusMonths(1)
    selectedMonth.value = next
    val today = LocalDate.now()
    selectedDate.value = if (YearMonth.from(today) == next) today else next.atDay(1)
  }

  fun jumpToCurrentMonth() {
    val today = LocalDate.now()
    selectedMonth.value = YearMonth.from(today)
    selectedDate.value = today
  }

  fun selectDate(date: LocalDate) {
    selectedDate.value = date
    val ym = YearMonth.from(date)
    if (selectedMonth.value != ym) {
      selectedMonth.value = ym
    }
  }

  fun setFrameMode(mode: LogbookFrameMode) {
    frameMode.value = mode
  }

  fun updateTrip(
    existing: TripRecord,
    durationMinutes: Int,
    restMinutes: Int,
    distanceKm: Double,
  ) {
    val safeMinutes = durationMinutes.coerceIn(1, 24 * 60)
    val safeRestMinutes = restMinutes.coerceIn(0, 24 * 60)
    val safeDistanceMeters = (distanceKm.coerceIn(0.0, 2_000.0)) * 1000.0
    val newDurationMs = safeMinutes * 60_000L
    val newRestMs = safeRestMinutes * 60_000L
    val updated = existing.copy(
      vehicleType = VehicleType.MOTORBIKE,
      activeDurationMillis = newDurationMs,
      movingDurationMillis = minOf(existing.movingDurationMillis, newDurationMs),
      restDurationMillis = newRestMs,
      endTimeMillis = existing.startTimeMillis + newDurationMs + newRestMs,
      distanceMeters = safeDistanceMeters,
      maxSpeedKmh = if (existing.isManualEntry) {
        (safeDistanceMeters / 1000.0) / (safeMinutes / 60.0)
      } else {
        existing.maxSpeedKmh
      },
    )
    viewModelScope.launch {
      repository.updateTrip(updated)
    }
  }

  fun deleteTrip(tripId: Long) {
    viewModelScope.launch {
      repository.deleteTrip(tripId)
    }
  }

  class Factory(
    private val repository: TripRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return LogbookViewModel(repository) as T
    }
  }
}
