# TripDriver

Ứng dụng Android đo quãng đường, tốc độ và thời gian di chuyển bằng xe máy theo thao tác **Start / Pause / Stop** thủ công, lưu trữ nhật ký hành trình hoàn toàn **offline** bằng SQLite.

- **Package ID:** `com.nguyen.tripdriver`
- **Phiên bản hiện tại:** `v2.1.0` (`versionCode = 4`)
- **Tác giả:** Nguyên (`© 2026 Nguyên`)

---

## Tính năng chính

- **Điều khiển thủ công:** Chủ động bấm **Bắt đầu hành trình**, **Tạm dừng**, **Tiếp tục** và **Kết thúc** (lựa chọn **Lưu chuyến** hoặc **Bỏ chuyến**). Sau khi cài đặt mới và cấp quyền GPS/thông báo, ứng dụng ở trạng thái **Sẵn sàng** và chỉ tính giờ khi người dùng bấm Bắt đầu.
- **Bộ đếm Thời gian nghỉ:** Tự động tính thời gian nghỉ ngơi từ lúc bấm **Tạm dừng** và dừng đếm khi bấm **Tiếp tục**.
- **Đo trực tiếp & Lọc nhiễu GPS:** Hiển thị quãng đường (`km`), tổng thời gian đi, **Thực chạy**, **Thời gian nghỉ**, **Tốc độ**, **Trung bình** và **Tối đa** (`km/h`); dùng Foreground Service (`FusedLocationProviderClient`) chạy ổn định khi tắt màn hình và lọc sai số GPS khi đứng chờ đèn đỏ.
- **Tổng kết hôm nay & Nhật ký hành trình:**
  - Tổng hợp 4 chỉ số trong ngày: Quãng đường, Tốc độ trung bình, Thời gian đi, Thời gian nghỉ.
  - Thống kê theo từng ngày / từng tháng với bảng đối soát và định dạng thời gian thông minh (`34s`, `2m5s`, `1h39m29s`).
- **Offline 100% & Giao diện Sáng/Tối:** Không thu thập dữ liệu lên máy chủ, hỗ trợ đầy đủ Light Mode và Dark Mode.

---

## Công nghệ sử dụng

- **Ngôn ngữ & UI:** Kotlin `2.3.20`, Jetpack Compose (`compose-bom:2026.03.01`), Material 3, Navigation 3
- **Build & SDK:** Android Gradle Plugin `9.0.1`, Java 17, `minSdk = 26`, `targetSdk = 37`
- **Vị trí & Lưu trữ:** Google Play Services Location `21.3.0`, Android SQLite (`SQLiteOpenHelper`)

---

## Hướng dẫn build từ mã nguồn

Yêu cầu **JDK 17** và **Android SDK**:

```powershell
# Chạy Unit Tests
.\gradlew.bat testDebugUnitTest

# Build APK Debug
.\gradlew.bat assembleDebug

# Chạy Android Instrumented Tests (cần Emulator hoặc thiết bị kết nối ADB)
.\gradlew.bat connectedDebugAndroidTest
```

Để build bản Release có ký số, tạo file `keystore.properties` và file keystore `.jks` tại thư mục gốc dự án (các file này đã được loại trừ khỏi Git trong `.gitignore`):

```properties
storeFile=../tripdriver-release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=tripdriver
keyPassword=YOUR_KEY_PASSWORD
```

Sau đó chạy:

```powershell
.\gradlew.bat assembleRelease
```

---

## Lịch sử phiên bản

Xem chi tiết tại [CHANGELOG.md](CHANGELOG.md).
