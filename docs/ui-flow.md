# UI Flow - SmartWiFi-Connect

## Main flow
Splash
→ Onboarding
→ (Login hoặc Home)

Từ Login:
- Đăng nhập -> Home
- Đăng ký ngay -> Register

Từ Register:
- Đăng ký -> Home
- Đã có tài khoản? Đăng nhập ngay -> Login

Từ Home có 4 nhánh:
1. Quét mã QR
2. Quét ảnh Wi-Fi / OCR
3. Nhập tay
4. Chia sẻ Wi-Fi cho thiết bị ở gần

Cả 3 nhánh đều đi đến:
→ Review Result

Sau Review Result:
- Kết nối
- Lưu lịch sử
- Mở Wi-Fi
- Sao chép mật khẩu

## Danh sách màn hình

### Splash
- Logo
- Tên app
- Điều hướng sang Onboarding hoặc Home

### Onboarding
- Giới thiệu lợi ích app
- Nút Bắt đầu

### Login
- Nhập email + mật khẩu
- CTA Đăng nhập
- Link sang Register
- Social login button (UI)

### Register
- Back về Login
- Nhập full name + email + password + confirm password
- CTA Đăng ký
- Social register button (UI)
- Link quay lại Login

### Home
- Hero card
- 3 action chính
- Recent history
- Bottom navigation

### Camera Permission
- Xin quyền camera
- Cho phép / mở cài đặt
- Sau khi được cấp quyền mới điều hướng sang màn scan

### QrScannerScreen
- Camera preview
- Overlay tối nhẹ
- Scan frame trong suốt
- Scan line animation trong khung QR
- Detect QR bằng ML Kit Barcode Scanning
- Instruction text
- Bottom actions
- Khi detect QR thành công -> đưa raw QR text sang `OCR Result`

### ImageScanScreen
- Camera hoặc chọn ảnh
- Nếu bấm camera: kiểm tra quyền camera trước khi mở capture
- Dùng embedded CameraX preview
- Scan line animation trong khung chụp ảnh
- OCR loading
- Ảnh được OCR bằng ML Kit và điều hướng sang `OCR Result`

### OCR Result
- Hiển thị text OCR nhận diện được
- Cho phép user chỉnh sửa text OCR
- Gọi backend parse (`/api/v1/ocr/parse`) để lấy SSID/password/confidence
- Gọi AI validate (`/api/ai/validate`) để hiển thị confidence/suggestion/recommendation
- Gọi fuzzy SSID (`/api/v1/ssid/fuzzy-match`) nếu có SSID và danh sách Wi-Fi xung quanh
- Hiển thị Wi-Fi xung quanh nếu Android cấp quyền Wi-Fi/location
- Hiển thị loading/error/success theo `statusMessage`

### ManualEntryScreen
- SSID
- Password
- Security type

### ShareWifiScreen
- Chỉ hữu ích khi thiết bị đã có Wi-Fi để chia sẻ
- Radar tìm thiết bị ở gần
- Danh sách thiết bị phát hiện được
- CTA `Chia sẻ` / `Chấp nhận`
- Bottom nav fixed với tab Chia sẻ active

### NetworkDetailScreen
- Mở khi user chạm vào 1 mạng ở `Home` hoặc `History`
- Hiển thị:
  - SSID
  - lần kết nối gần nhất
  - giao thức bảo mật
  - tần số
  - chất lượng sóng + dBm
  - usage chart
- Nếu mạng đang được kết nối trên máy:
  - hiển thị `link speed`, `RX`, `TX` realtime
  - refresh telemetry định kỳ từ `WifiManager.connectionInfo`
- CTA:
  - `Kết nối ngay`
  - `Xóa mạng này` (với mạng đã lưu trong SQLite)
- Bottom nav giữ active theo nguồn điều hướng gần nhất

### ReviewScreen
- Wi-Fi info card
- Edit form
- Confidence chip
- Action buttons

### HistoryScreen
- List lịch sử kết nối đọc từ SQLite
- Filter: Tất cả / Bảo mật / Công cộng
- Analytics card 30 ngày qua
- Bottom nav fixed với tab Lịch sử active

### HistoryDetailScreen
- Full detail

### SettingsScreen
- App settings

## Shared states
- Loading
- Empty
- Error
- Permission denied
