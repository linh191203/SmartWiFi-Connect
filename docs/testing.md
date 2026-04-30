# Testing - SmartWiFi-Connect

## Mục tiêu kiểm thử
- UI không vỡ
- Flow quét QR/OCR chạy đúng
- Review chỉnh sửa được
- Lưu local và backend hoạt động
- Kết nối Wi-Fi xử lý lỗi rõ ràng
- Bản final đủ ổn để demo

---

## Giai đoạn test theo sprint

### Sprint 1
- App chạy được
- CI/CD cơ bản chạy được
- `/api/health` trả về 200

### Sprint 2
- OCR đọc được text thô từ ảnh
- FE xử lý được JSON mẫu từ `/api/ai/validate`
- Camera/photo UI không vỡ

### Sprint 3
- Review screen hiển thị được kết quả OCR/AI
- Gợi ý SSID hoạt động
- Lưu local bước đầu thành công
- Mock API integration test bao phủ parse OCR + AI validate + fuzzy SSID

### Sprint 4
- Flow: scan → review → connect → save hoạt động
- API `/api/networks` lưu được
- Password được mã hóa trước khi lưu
- History hiển thị đúng

### Sprint 5
- Unit test + integration test ổn định
- OCR chính xác hơn
- UI/UX mượt hơn
- Bug giảm đáng kể

### Sprint 6
- End-to-end test cuối
- Pipeline CI/CD chạy
- APK build thành công
- Backend deploy thành công

---

## Test case chính

### HomeScreen
- Mở app vào Home thành công
- Thấy đủ 3 action chính
- Bottom nav hiển thị đúng

### QrScannerScreen
- Mở camera được
- Hiển thị scan frame đúng
- Không có overlay trắng đục che camera
- Không tràn viền phải
- Có xử lý permission denied
- QR thật được detect bằng ML Kit Barcode Scanning
- Scan frame có animation vạch trắng chạy lên/xuống

### ImageScanScreen
- Chọn ảnh được
- Chụp ảnh được
- Hiển thị trạng thái loading OCR
- Sang Review được
- CameraX preview hiển thị trong app
- Capture lấy bitmap từ preview và sang OCR Result
- Scan frame có animation vạch trắng chạy lên/xuống

### OCR Result
- Hiển thị SSID/password
- Sửa tay được
- Ẩn/hiện password được
- Nút connect/save hoạt động đúng luồng
- Hiển thị AI validation card khi có kết quả
- Có choices Auto connect / Review thủ công / OCR lại
- Có thể áp dụng SSID/password normalized từ AI
- Hiển thị fuzzy SSID suggestion
- Hiển thị Wi-Fi xung quanh nếu Android cấp quyền Wi-Fi/location
- Sóng Wi-Fi hiển thị bằng vòng cung, không dùng cột mobile

### HistoryScreen
- Lưu local xong thì thấy item
- Empty state đúng khi chưa có dữ liệu
- Mở detail được
- Tab History đọc danh sách từ SQLite thật

### API
- `/api/health` trả status 200
- `/api/ai/validate` trả JSON hợp lệ
- `/api/v1/ocr/parse` trả JSON parse Wi-Fi hợp lệ
- `/api/v1/ssid/fuzzy-match` trả fuzzy best match hợp lệ
- `/api/networks` lưu được dữ liệu hợp lệ
- Không chấp nhận payload sai format

---

## Bug log
- [x] Scanner overlay QR/OCR đã có animation và không che camera
- [/] Permission flow camera đã ổn; Wi-Fi/location cần test thêm trên thiết bị thật
- [ ] OCR chưa có bộ ảnh test chuẩn
- [ ] Build release chưa kiểm tra

---

## Kết quả test đã chạy

### 2026-04-21
- `:app:compileDebugKotlin` passed
- `:app:compileDebugAndroidTestKotlin` passed
- `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.smartwificonnect.MainViewModelMockApiIntegrationTest` passed
- `MainViewModelMockApiIntegrationTest` đã bao phủ mock API parse OCR + AI validate + fuzzy SSID
