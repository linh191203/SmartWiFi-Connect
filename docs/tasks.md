# Tasks - SmartWiFi-Connect

## Quy ước trạng thái
- [ ] Chưa làm
- [/] Đang làm
- [x] Hoàn thành
- [!] Bị chặn / cần xử lý

---

## Sprint 1
### FE
- [x] Tạo project Android Studio
- [x] Tạo Git repo + GitHub
- [x] Tạo package structure
- [x] Tạo docs nội bộ
- [ ] Cấu hình CI/CD cơ bản
- [ ] Cài thư viện chính
- [ ] Thiết kế wireframe UI
- [ ] Viết test đầu tiên

### BE
- [x] Thiết lập server cơ bản
- [ ] Khởi tạo DB
- [x] Tạo `/api/health`
- [x] Tạo `/api/ai/validate` dummy
- [x] Viết unit test `GET /api/health` → 2 passed ✅

---

## Sprint 2
### FE
- [x] Tạo HomeScreen
- [x] Tạo OnboardingScreen
- [x] Tạo LoginScreen (Compose)
- [x] Tạo RegisterScreen (Compose)
- [x] Tinh chỉnh Login UI theo design (social icon + typography + spacing)
- [x] Tạo Permission flow
- [x] Tạo camera/photo UI
- [x] Hoàn thiện OCR mock flow để demo FE
- [x] Tích hợp OCR bằng ML Kit
- [x] Nối parse OCR với backend từ OCR Result
- [x] Fix crash camera permission khi mở OCR từ màn scan
- [x] Test mock API integration cho FE (MockWebServer + instrumentation)

### BE
- [ ] Hoàn thiện `/api/ai/validate` dummy
- [ ] Trả JSON mẫu
- [ ] Viết unit test backend

---

## Sprint 3
### FE
- [x] Gợi ý SSID gần đúng
- [x] Gọi API `/api/ai/validate`
- [ ] Tạo ReviewScreen
- [x] Lưu local history bằng SQLite
- [ ] Lưu local bằng Room
- [x] Kiểm thử tích hợp với MockWebServer

### BE
- [ ] Tích hợp AI backend hoặc mock nâng cao
- [ ] Unit test `/api/ai/validate`
- [ ] Sửa lỗi tích hợp

---

## Sprint 4
### FE
- [x] Kết nối Wi-Fi thật trên Android (WifiNetworkSpecifier - phần cơ bản)
- [/] Xử lý loading/success/error/timeout
- [/] Gọi API save network (best-effort sau khi connect thành công, chờ BE endpoint thật)
- [/] Lưu local history bằng SQLite sau connect thành công (Room chưa triển khai)
- [x] Tạo HistoryScreen
- [ ] Test manual flow kết nối và lưu

### BE
- [ ] Tạo `POST /api/networks`
- [ ] Lưu DB
- [ ] Mã hóa password trước khi lưu
- [ ] Unit test API save network

---

## Sprint 5
### FE
- [ ] Unit test xử lý kết nối
- [ ] Unit test lưu local
- [ ] Integration / E2E test
- [ ] Tối ưu OCR
- [ ] Tối ưu UI/UX
- [ ] Fix bug từ test

### BE
- [ ] Test validate dữ liệu
- [ ] Tối ưu timeout/fallback AI
- [ ] Refactor backend
- [ ] Update API docs
- [ ] Fix bug từ test

---

## Sprint 6
### FE
- [ ] Hoàn thiện CI/CD
- [ ] Build APK release
- [ ] Cấu hình version/release
- [ ] Viết hướng dẫn sử dụng
- [ ] Chuẩn bị demo
- [ ] Fix bug cuối

### BE
- [ ] Deploy backend
- [ ] Swagger/Postman
- [ ] Check endpoint demo
- [ ] Fix bug cuối

### Cả nhóm
- [ ] Test end-to-end cuối
- [ ] Final testing
- [ ] Release sprint cuối

---

## phải làm
- [ ] Hoàn thiện package `com.smartwificonnect`
- [x] Tạo AppTheme
- [x] Tạo `Routes.kt`
- [x] Tạo `AppNavHost.kt`
- [x] Tạo `HomeScreen.kt`
- [x] Tạo `OnboardingScreen.kt`
- [x] Tạo `LoginScreen.kt`
- [x] Tạo `RegisterScreen.kt`

## Hôm nay đã làm (2026-04-07)
- [x] Chuyển OCR từ mock sang OCR thật bằng ML Kit (gallery + camera preview)
- [x] Nối parse OCR với backend qua endpoint `/api/v1/ocr/parse`
- [x] Tạo màn `OCR Result` để edit text OCR trước khi parse server
- [x] Fix crash `SecurityException` do chưa cấp quyền `CAMERA`
- [x] Verify build và cài app lên emulator để test thực tế

## Đã làm (2026-04-21)
- [x] FE gọi `POST /api/ai/validate` sau parse OCR
- [x] FE gọi fuzzy SSID endpoint và giữ fallback local khi BE chưa sẵn sàng
- [x] UI `OCR Result` hiển thị OCR/AI result choices
- [x] Lưu OCR/AI/fuzzy result vào SQLite local history
- [x] Tạo `HistoryScreen` đọc dữ liệu SQLite thật
- [x] CameraX preview thật cho QR scanner và chụp ảnh OCR
- [x] QR scanner tự detect QR bằng ML Kit Barcode Scanning
- [x] Camera permission chỉ hỏi khi chưa được cấp
- [x] Thêm animation vạch scan trong QR/OCR capture frame
- [x] OCR Result hiển thị Wi-Fi xung quanh nếu Android cấp quyền scan Wi-Fi
- [x] Đổi indicator sóng Wi-Fi từ cột mobile sang vòng cung Wi-Fi
- [x] Integration test với mock AI/fuzzy API
- [x] Skip phần BE mock nâng cao để tránh conflict với nhánh BE

## Đã làm (2026-04-25 ~ 2026-04-27)
- [x] Tạo `SettingsScreen` và nối toàn bộ button/tab `Cài đặt`
- [x] Thêm dark mode toggle hoạt động ở cấp toàn app
- [x] Cập nhật palette sáng/tối cho các màn chính trong flow hiện tại
- [x] Chỉnh avatar top bar màn `Cài đặt` cho cân trục nhìn
- [x] Chỉnh avatar góc phải top bar màn `Trang chủ` cho đồng bộ với `Cài đặt`
- [x] Tạo `ShareWifiScreen` và nối toàn bộ tab/nút `Chia sẻ` sang route riêng

## Đã làm (2026-04-28)
- [x] Tạo `NetworkDetailScreen`
- [x] Nối click từ `HomeScreen` và `HistoryScreen` sang màn chi tiết mạng
- [x] Hiển thị đánh giá chất lượng sóng tốt/yếu + dBm theo mạng được chọn
- [x] Hiển thị link speed / RX / TX realtime cho mạng đang kết nối
- [x] Cho phép `Kết nối ngay` lại từ màn chi tiết
- [x] Cho phép `Xóa mạng này` khỏi SQLite history
- [x] Cập nhật docs nội bộ cho flow màn chi tiết mạng

---

## Definition of Done
Một task chỉ được tính là xong khi:
- build được
- chạy được hoặc preview được
- không có lỗi UI nghiêm trọng
- có log/test cơ bản nếu cần
- cập nhật `memory.md`
- cập nhật `changelog.md`
