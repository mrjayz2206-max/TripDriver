/*
 * Copyright (c) 2026 Nguyên. All rights reserved.
 */

package com.nguyen.tripdriver

import android.app.Application
import com.nguyen.tripdriver.data.SqliteTripRepository
import com.nguyen.tripdriver.tracking.TripTrackerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TripDriverApp : Application() {
  private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  lateinit var repository: SqliteTripRepository
    private set

  lateinit var trackerManager: TripTrackerManager
    private set

  override fun onCreate() {
    super.onCreate()
    repository = SqliteTripRepository(this)
    trackerManager = TripTrackerManager(this, repository)

    appScope.launch {
      repository.initialize()
      trackerManager.restoreIfNeeded()
    }
  }
}
