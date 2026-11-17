/*
 * Copyright (c) 2026 Nguyên. All rights reserved.
 * Project: TripDriver (com.nguyen.tripdriver)
 */

package com.nguyen.tripdriver.domain

import com.nguyen.tripdriver.BuildConfig

data class VersionReleaseNote(
  val versionName: String,
  val summary: String,
)

object AppMetadata {
  const val appName: String = "TripDriver"
  val packageName: String = BuildConfig.APPLICATION_ID
  val versionName: String = BuildConfig.VERSION_NAME
  val authorName: String = BuildConfig.AUTHOR_NAME
  val copyrightYear: String = BuildConfig.COPYRIGHT_YEAR

  val versionDisplay: String
    get() = "v$versionName"

  val footerAttribution: String
    get() = "$appName $versionDisplay  •  © $copyrightYear $authorName"

  val copyrightNotice: String
    get() = "© $copyrightYear $authorName. All rights reserved."

  val releaseHistory: List<VersionReleaseNote> = listOf(
    VersionReleaseNote(
      versionName = "2.1.0",
      summary = "Hiển thị TripDriver ở tiêu đề chính, thêm Tốc độ trung bình trực tiếp, chuẩn hóa thời gian dạng 1h39m29s ở Tổng kết & Nhật ký, tinh giản nút phụ và không tự bấm Start ngay sau khi cấp quyền.",
    ),
    VersionReleaseNote(
      versionName = "2.0.0",
      summary = "Đổi tên ứng dụng thành TripDriver (com.nguyen.tripdriver), bổ sung bộ đếm Thời gian nghỉ khi tạm dừng, tinh gọn hộp thoại kết thúc (Lưu / Bỏ chuyến) và bảng tổng kết hôm nay.",
    ),
    VersionReleaseNote(
      versionName = "1.1.0",
      summary = "Tối ưu trải nghiệm cho xe máy, nâng cấp khung nhật ký và bổ sung hệ thống quản lý phiên bản.",
    ),
    VersionReleaseNote(
      versionName = "1.0.0",
      summary = "Phiên bản đầu tiên: đo quãng đường & thời gian GPS chạy nền, lọc nhiễu dừng đèn đỏ và lưu trữ SQLite offline.",
    ),
  )
}
