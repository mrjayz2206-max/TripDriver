/*
 * Copyright (c) 2026 Nguyên. All rights reserved.
 */

package com.nguyen.tripdriver.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nguyen.tripdriver.domain.AppMetadata
import com.nguyen.tripdriver.domain.TripAggregation
import com.nguyen.tripdriver.domain.TripRecord
import com.nguyen.tripdriver.domain.VehicleType
import com.nguyen.tripdriver.tracking.ActiveTripState
import com.nguyen.tripdriver.tracking.GpsSignalStatus
import com.nguyen.tripdriver.tracking.TripPhase
import com.nguyen.tripdriver.ui.logbook.LogbookFrameMode
import com.nguyen.tripdriver.ui.logbook.LogbookScreenContent
import com.nguyen.tripdriver.ui.logbook.LogbookUiState
import com.nguyen.tripdriver.ui.theme.TripDriverTheme
import com.nguyen.tripdriver.ui.tracker.TrackerScreenContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class TripDriverUiTest {

  @get:Rule
  val composeRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun trackerScreen_rendersAppNameHeaderAvgSpeedSmartDurationsAndNoInfoIcon() {
    var startClicked = false
    val date = LocalDate.now()
    val startMs = date.atTime(8, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val trip = TripRecord(
      id = 1L,
      startTimeMillis = startMs,
      endTimeMillis = startMs + 5_969_000L,
      activeDurationMillis = ((1 * 3600) + (39 * 60) + 29) * 1000L, // 1h39m29s
      movingDurationMillis = 5_000_000L,
      restDurationMillis = 125_000L, // 2m5s
      distanceMeters = 50_000.0,
      maxSpeedKmh = 58.0,
    )

    composeRule.setContent {
      TripDriverTheme {
        TrackerScreenContent(
          activeState = ActiveTripState(
            phase = TripPhase.IDLE,
            vehicleType = VehicleType.MOTORBIKE,
          ),
          todaySummary = TripAggregation.summarizeDay(date, listOf(trip)),
          lastSavedTrip = null,
          hasLocationPermission = true,
          isLocationServiceEnabled = true,
          showPermissionDeniedSettingsPrompt = false,
          onRequestPermission = {},
          onStartClick = { startClicked = true },
          onPauseClick = {},
          onResumeClick = {},
          onSaveTrip = {},
          onDiscardTrip = {},
          onDismissSavedBanner = {},
          onOpenLocationSettings = {},
          onOpenAppSettings = {},
          onOpenLogbook = {},
        )
      }
    }

    // Header shows app name TripDriver, no subtitle, no info icon.
    composeRule.onNodeWithText("TripDriver").assertIsDisplayed()
    composeRule.onNodeWithText("Nhật ký quãng đường & thời gian").assertDoesNotExist()
    composeRule.onNodeWithContentDescription("Thông tin ứng dụng").assertDoesNotExist()
    composeRule.onNodeWithText("Số chuyến").assertDoesNotExist()

    // Cockpit shows live average speed label.
    composeRule.onNodeWithText("Trung bình").assertIsDisplayed()

    // Today summary uses smart 1h39m29s & 2m5s formatting.
    composeRule.onNodeWithText("1h39m29s").assertIsDisplayed()
    composeRule.onNodeWithText("2m5s").assertIsDisplayed()

    // Footer opens About dialog cleanly.
    composeRule.onNodeWithText(AppMetadata.footerAttribution).assertIsDisplayed()
    composeRule.onNodeWithText(AppMetadata.footerAttribution).performClick()
    composeRule.onNodeWithText(AppMetadata.copyrightNotice).assertIsDisplayed()
    composeRule.onNodeWithText("Đóng").performClick()

    composeRule.onNodeWithText("BẮT ĐẦU HÀNH TRÌNH").performClick()
    assertTrue(startClicked)
  }

  @Test
  fun trackerScreen_stopOpensTwoOptionFinishDialog() {
    var saved = false

    composeRule.setContent {
      TripDriverTheme {
        TrackerScreenContent(
          activeState = ActiveTripState(
            phase = TripPhase.TRACKING,
            vehicleType = VehicleType.MOTORBIKE,
            activeDurationMillis = 125_000L,
            movingDurationMillis = 110_000L,
            restDurationMillis = 30_000L,
            distanceMeters = 1_450.0,
            currentSpeedKmh = 32.0,
            maxSpeedKmh = 45.0,
            lastAccuracyMeters = 8f,
            gpsSignalStatus = GpsSignalStatus.GOOD,
          ),
          todaySummary = TripAggregation.summarizeDay(LocalDate.now(), emptyList()),
          lastSavedTrip = null,
          hasLocationPermission = true,
          isLocationServiceEnabled = true,
          showPermissionDeniedSettingsPrompt = false,
          onRequestPermission = {},
          onStartClick = {},
          onPauseClick = {},
          onResumeClick = {},
          onSaveTrip = { saved = true },
          onDiscardTrip = {},
          onDismissSavedBanner = {},
          onOpenLocationSettings = {},
          onOpenAppSettings = {},
          onOpenLogbook = {},
        )
      }
    }

    composeRule.onNodeWithText("1.45").assertIsDisplayed()
    composeRule.onNodeWithText("00:00:30").assertIsDisplayed()
    composeRule.onNodeWithText("Kết thúc").performClick()
    composeRule.onNodeWithText("Kết thúc hành trình").assertIsDisplayed()
    composeRule.onNodeWithText("Lưu chuyến").assertIsDisplayed()
    composeRule.onNodeWithText("Bỏ chuyến").assertIsDisplayed()
    composeRule.onNodeWithText("Chạy tiếp").assertDoesNotExist()
    composeRule.onNodeWithText("Lưu chuyến").performClick()
    assertTrue(saved)
  }

  @Test
  fun logbookScreen_hidesRemovedControlsAndFormatsSmartDurations() {
    val date = LocalDate.of(2026, 11, 17)
    val startMs = date.atTime(8, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val sampleTrip = TripRecord(
      id = 1L,
      vehicleType = VehicleType.MOTORBIKE,
      startTimeMillis = startMs,
      endTimeMillis = startMs + 6_269_000L,
      activeDurationMillis = ((1 * 3600) + (39 * 60) + 29) * 1000L,
      movingDurationMillis = 5_500_000L,
      restDurationMillis = 300_000L,
      distanceMeters = 12_400.0,
      maxSpeedKmh = 50.0,
    )
    val monthly = TripAggregation.summarizeMonth(YearMonth.of(2026, 11), listOf(sampleTrip))
    var frameMode = LogbookFrameMode.TABLE

    composeRule.setContent {
      TripDriverTheme {
        LogbookScreenContent(
          uiState = LogbookUiState(
            selectedMonth = YearMonth.of(2026, 11),
            selectedDate = date,
            frameMode = frameMode,
            monthlySummary = monthly,
            selectedDaySummary = monthly.dailySummaries.first(),
          ),
          onPreviousMonth = {},
          onNextMonth = {},
          onJumpCurrentMonth = {},
          onSelectDate = {},
          onSetFrameMode = { frameMode = it },
          onUpdateTrip = { _, _, _, _ -> },
          onDeleteTrip = {},
        )
      }
    }

    composeRule.onNodeWithText("Nhật ký hành trình").assertIsDisplayed()
    composeRule.onNodeWithText("Thống kê theo ngày & tháng").assertDoesNotExist()
    composeRule.onNodeWithContentDescription("Thông tin ứng dụng").assertDoesNotExist()
    composeRule.onNodeWithContentDescription("Chia sẻ báo cáo tháng").assertDoesNotExist()
    composeRule.onNodeWithText("Thêm").assertDoesNotExist()
    composeRule.onNodeWithText("Ghi bù").assertDoesNotExist()

    composeRule.onNodeWithText("Số ngày chạy").assertIsDisplayed()
    composeRule.onNodeWithText("1 ngày chạy").assertIsDisplayed()
    composeRule.onAllNodesWithText("1h39m29s")[0].assertIsDisplayed()
    composeRule.onAllNodesWithText("5m")[0].assertIsDisplayed()

    composeRule.onNodeWithContentDescription("Khung lịch").performClick()
    assertEquals(LogbookFrameMode.CALENDAR, frameMode)
  }
}
