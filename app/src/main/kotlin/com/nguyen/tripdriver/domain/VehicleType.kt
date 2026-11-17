/*
 * Copyright (c) 2026 Nguyên. All rights reserved.
 */

package com.nguyen.tripdriver.domain

enum class VehicleType(
  val storageKey: String,
  val displayName: String,
) {
  MOTORBIKE(
    storageKey = "MOTORBIKE",
    displayName = "Xe máy",
  );

  companion object {
    @Suppress("UNUSED_PARAMETER")
    fun fromStorageKey(key: String?): VehicleType = MOTORBIKE
  }
}
