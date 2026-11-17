# Changelog - TripDriver

Copyright (c) 2026 Nguyên. All rights reserved.

## Quy ước nâng cấp phiên bản (Semantic Versioning)
Dự án tuân thủ chuẩn `MAJOR.MINOR.PATCH` (`versionName`):
- **PATCH (`2.1.1`, `2.1.2`...):** Sửa lỗi nhỏ, tối ưu hiệu năng hoặc tinh chỉnh giao diện nhỏ.
- **MINOR (`2.1.0`, `2.2.0`...):** Bổ sung, lược bỏ hoặc cải tiến tính năng tương thích ngược.
- **MAJOR (`3.0.0`, `4.0.0`...):** Thay đổi lớn về tên/định hướng, kiến trúc hoặc luồng chức năng cốt lõi.

---

## [2.1.0] (2026-11-17)
- Thay tiêu đề màn hình Hành trình thành tên ứng dụng **TripDriver** và bỏ biểu tượng thông tin `(i)` ở cả 2 màn hình.
- Bổ sung chỉ số **Tốc độ trung bình** trực tiếp ngay trên bảng đồng hồ chuyến đi.
- Chuẩn hóa hiển thị thời gian thông minh dạng `1h39m29s` / `12m30s` / `34s` đồng bộ ở cả **Tổng kết hôm nay** và **Nhật ký**.
- Tinh giản giao diện **Nhật ký**: chỉ giữ tiêu đề **Nhật ký hành trình**, bỏ phụ đề, nút Thêm, nút Ghi bù và nút Chia sẻ.
- Cải thiện quy trình cấp quyền GPS: sau khi cấp quyền lần đầu, app chuyển về trạng thái **Sẵn sàng** và chỉ tính giờ khi người dùng bấm nút **Bắt đầu**.

## [2.0.0] (2026-11-17)
- Đổi tên ứng dụng thành **TripDriver** (`com.nguyen.tripdriver`), bổ sung bộ đếm **Thời gian nghỉ**, hộp thoại Kết thúc 2 lựa chọn và bảng Tổng kết hôm nay 4 chỉ số.

## [1.1.0] (2026-11-17)
- Tối ưu trải nghiệm cho xe máy, bổ sung quản lý phiên bản và bản quyền tác giả Nguyên.

## [1.0.0] (2026-11-17)
- Ra mắt phiên bản đầu tiên với đo quãng đường & thời gian GPS chạy nền và nhật ký theo ngày/tháng.
