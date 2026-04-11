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
- [ ] Gọi API `/api/ai/validate`
- [ ] Tạo ReviewScreen
- [ ] Lưu local bằng Room
- [ ] Kiểm thử tích hợp

### BE
- [ ] Tích hợp AI backend hoặc mock nâng cao
- [ ] Unit test `/api/ai/validate`
- [ ] Sửa lỗi tích hợp

---

## Sprint 4
### FE
- [ ] Kết nối Wi-Fi thật trên Android
- [ ] Xử lý loading/success/error/timeout
- [ ] Gọi API save network
- [ ] Lưu local history bằng Room
- [ ] Tạo HistoryScreen
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

## Hôm nay phải làm
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

---

## Definition of Done
Một task chỉ được tính là xong khi:
- build được
- chạy được hoặc preview được
- không có lỗi UI nghiêm trọng
- có log/test cơ bản nếu cần
- cập nhật `memory.md`
- cập nhật `changelog.md`
