/*
 * Copyright (c) 2026 Nguyên. All rights reserved.
 */

package com.nguyen.tripdriver.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nguyen.tripdriver.data.TripRepository
import com.nguyen.tripdriver.tracking.TripTrackerManager
import com.nguyen.tripdriver.ui.logbook.LogbookRoute
import com.nguyen.tripdriver.ui.logbook.LogbookViewModel
import com.nguyen.tripdriver.ui.tracker.TrackerRoute
import com.nguyen.tripdriver.ui.tracker.TrackerViewModel

enum class TripDriverTab(val label: String) {
  TRACKER("Hành trình"),
  LOGBOOK("Nhật ký"),
}

@Composable
fun MainScreen(
  trackerManager: TripTrackerManager,
  repository: TripRepository,
  modifier: Modifier = Modifier,
) {
  var selectedTab by rememberSaveable { mutableStateOf(TripDriverTab.TRACKER) }

  val trackerViewModel: TrackerViewModel = viewModel(
    factory = TrackerViewModel.Factory(trackerManager, repository),
  )
  val logbookViewModel: LogbookViewModel = viewModel(
    factory = LogbookViewModel.Factory(repository),
  )

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    bottomBar = {
      NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
      ) {
        NavigationBarItem(
          selected = selectedTab == TripDriverTab.TRACKER,
          onClick = { selectedTab = TripDriverTab.TRACKER },
          icon = { Icon(imageVector = Icons.Rounded.Route, contentDescription = null) },
          label = { Text(TripDriverTab.TRACKER.label) },
          colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
          ),
        )
        NavigationBarItem(
          selected = selectedTab == TripDriverTab.LOGBOOK,
          onClick = { selectedTab = TripDriverTab.LOGBOOK },
          icon = { Icon(imageVector = Icons.Rounded.History, contentDescription = null) },
          label = { Text(TripDriverTab.LOGBOOK.label) },
          colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
          ),
        )
      }
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
    ) {
      when (selectedTab) {
        TripDriverTab.TRACKER -> {
          TrackerRoute(
            viewModel = trackerViewModel,
            onOpenLogbook = { selectedTab = TripDriverTab.LOGBOOK },
          )
        }
        TripDriverTab.LOGBOOK -> {
          LogbookRoute(viewModel = logbookViewModel)
        }
      }
    }
  }
}
