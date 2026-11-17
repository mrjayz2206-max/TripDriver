/*
 * Copyright (c) 2026 Nguyên. All rights reserved.
 */

package com.nguyen.tripdriver.ui.tracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nguyen.tripdriver.data.TripRepository
import com.nguyen.tripdriver.domain.DailySummary
import com.nguyen.tripdriver.domain.TripAggregation
import com.nguyen.tripdriver.domain.TripRecord
import com.nguyen.tripdriver.tracking.ActiveTripState
import com.nguyen.tripdriver.tracking.TripTrackerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class TrackerViewModel(
  private val trackerManager: TripTrackerManager,
  private val repository: TripRepository,
) : ViewModel() {

  val activeState: StateFlow<ActiveTripState> = trackerManager.state

  val todaySummary: StateFlow<DailySummary> = repository.trips
    .map { allTrips ->
      val today = LocalDate.now()
      val todayTrips = allTrips.filter { TripAggregation.localDateOf(it.startTimeMillis) == today }
      TripAggregation.summarizeDay(today, todayTrips)
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = TripAggregation.summarizeDay(LocalDate.now(), emptyList()),
    )

  private val _lastSavedTrip = MutableStateFlow<TripRecord?>(null)
  val lastSavedTrip: StateFlow<TripRecord?> = _lastSavedTrip.asStateFlow()

  fun startTrip(): Boolean = trackerManager.startTrip()

  fun pauseTrip() = trackerManager.pauseTrip()

  fun resumeTrip(): Boolean = trackerManager.resumeTrip()

  fun finishAndSaveTrip() {
    viewModelScope.launch {
      val saved = trackerManager.finishAndSaveTrip()
      _lastSavedTrip.value = saved
    }
  }

  fun discardTrip() {
    viewModelScope.launch {
      trackerManager.discardTrip()
    }
  }

  fun consumeSavedTripBanner() {
    _lastSavedTrip.value = null
  }

  class Factory(
    private val trackerManager: TripTrackerManager,
    private val repository: TripRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return TrackerViewModel(trackerManager, repository) as T
    }
  }
}
