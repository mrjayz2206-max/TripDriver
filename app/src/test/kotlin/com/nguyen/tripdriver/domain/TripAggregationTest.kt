/*
 * Copyright (c) 2026 Nguyên. All rights reserved.
 */

package com.nguyen.tripdriver.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class TripAggregationTest {

  private val zone = ZoneId.of("Asia/Ho_Chi_Minh")

  @Test
  fun summarizeMonth_aggregatesDistanceDurationRestAndAvgSpeed() {
    val day1Start = LocalDate.of(2026, 11, 5).atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
    val day2Start = LocalDate.of(2026, 11, 17).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()

    val trips = listOf(
      TripRecord(
        id = 1,
        startTimeMillis = day1Start,
        endTimeMillis = day1Start + 2_100_000L,
        activeDurationMillis = 1_800_000L,
        movingDurationMillis = 1_500_000L,
        restDurationMillis = 300_000L,
        distanceMeters = 12_500.0,
        maxSpeedKmh = 52.0,
      ),
      TripRecord(
        id = 2,
        startTimeMillis = day1Start + 3_600_000L,
        endTimeMillis = day1Start + 4_620_000L,
        activeDurationMillis = 900_000L,
        movingDurationMillis = 800_000L,
        restDurationMillis = 120_000L,
        distanceMeters = 5_000.0,
        maxSpeedKmh = 38.0,
      ),
      TripRecord(
        id = 3,
        startTimeMillis = day2Start,
        endTimeMillis = day2Start + 1_200_000L,
        activeDurationMillis = 1_200_000L,
        movingDurationMillis = 1_000_000L,
        restDurationMillis = 60_000L,
        distanceMeters = 7_500.0,
        maxSpeedKmh = 41.0,
      ),
    )

    val summary = TripAggregation.summarizeMonth(YearMonth.of(2026, 11), trips, zone)
    assertEquals(25_000.0, summary.totalDistanceMeters, 0.01)
    assertEquals(3_900_000L, summary.totalDurationMillis)
    assertEquals(480_000L, summary.totalRestDurationMillis)
    assertEquals(3, summary.totalTrips)
    assertEquals(2, summary.activeDaysCount)
    assertEquals(12_500.0, summary.longestTripMeters, 0.01)
    assertEquals(12_500.0, summary.avgDistancePerActiveDayMeters, 0.01)

    val day1Summary = summary.dailySummaries.first { it.date.dayOfMonth == 5 }
    assertEquals(420_000L, day1Summary.totalRestDurationMillis)
    assertEquals(23.33, day1Summary.avgSpeedKmh, 0.1)
  }

  @Test
  fun metadataAndVersionDisplay_haveNoBuildTextAndMatchV210() {
    assertEquals("TripDriver", AppMetadata.appName)
    assertEquals("Nguyên", AppMetadata.authorName)
    assertEquals("com.nguyen.tripdriver", AppMetadata.packageName)
    assertEquals("v2.1.0", AppMetadata.versionDisplay)
    assertFalse(AppMetadata.versionDisplay.contains("Build", ignoreCase = true))
    assertFalse(AppMetadata.footerAttribution.contains("Build", ignoreCase = true))
    assertTrue(AppMetadata.footerAttribution.startsWith("TripDriver v2.1.0"))
    assertTrue(AppMetadata.copyrightNotice.contains("Nguyên"))
  }

  @Test
  fun formatDurationSmart_formatsSecondsMinutesHoursAccurately() {
    assertEquals("0s", TripFormatters.formatDurationSmart(0L))
    assertEquals("34s", TripFormatters.formatDurationSmart(34_000L))
    assertEquals("1m", TripFormatters.formatDurationSmart(60_000L))
    assertEquals("5m12s", TripFormatters.formatDurationSmart(312_000L))
    // 1 hour 39 minutes 29 seconds -> 1h39m29s
    assertEquals("1h39m29s", TripFormatters.formatDurationSmart(((1 * 3600) + (39 * 60) + 29) * 1000L))
    assertEquals("2h15m", TripFormatters.formatDurationSmart(((2 * 3600) + (15 * 60)) * 1000L))
    assertEquals("2h", TripFormatters.formatDurationSmart((2 * 3600) * 1000L))
  }

  @Test
  fun otherFormatters_produceExpectedTabularStrings() {
    assertEquals("01:05:09", TripFormatters.formatStopwatch(3_909_000L))
    assertEquals("12.35", TripFormatters.formatDistanceKm(12_346.0, decimals = 2))
    assertEquals("45", TripFormatters.formatSpeedKmh(44.8))
  }
}
