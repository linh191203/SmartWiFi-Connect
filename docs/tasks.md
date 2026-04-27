# Tasks - SmartWiFi-Connect

## Quy ước trạng thái
- [ ] Chưa làm
- [/] Đang làm
- [x] Hoàn thành
- [!] Bị chặn / cần xử lý

---

## Web Frontend (React)
- [x] Tạo project React + Vite
- [x] Tạo các màn hình: Home, Onboarding, Login, Register
- [x] Tạo các màn hình: QrScanner, ScanImage, ManualEntry
- [x] Tạo ReviewScreen
- [x] Tạo HistoryScreen
- [x] Tạo SettingsScreen
- [x] Lưu lịch sử bằng localStorage
- [ ] Gọi API `/api/ai/validate`
- [ ] Xử lý loading/success/error/timeout
- [ ] Gọi API `POST /api/networks` để lưu mạng
- [ ] Unit test / integration test

## Backend (Node.js)
- [x] Tạo server cơ bản
- [x] `GET /api/health`
- [x] `POST /api/ai/validate` dummy
- [ ] Hoàn thiện `/api/ai/validate`
- [ ] `POST /api/networks` — lưu DB
- [ ] Mã hóa password trước khi lưu
- [ ] Unit test các endpoint
- [ ] Deploy backend

## Chung
- [ ] Test end-to-end
- [ ] Swagger / Postman docs
- [ ] Chuẩn bị demo
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
