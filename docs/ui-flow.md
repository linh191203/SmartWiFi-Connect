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

Từ Home có 3 nhánh:
1. Quét mã QR
2. Quét ảnh Wi-Fi / OCR
3. Nhập tay

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
- Instruction text
- Bottom actions

### ImageScanScreen
- Camera hoặc chọn ảnh
- Nếu bấm camera: kiểm tra quyền camera trước khi mở capture
- OCR loading
- Ảnh được OCR bằng ML Kit và điều hướng sang `OCR Result`

### OCR Result
- Hiển thị text OCR nhận diện được
- Cho phép user chỉnh sửa text OCR
- Gọi backend parse (`/api/v1/ocr/parse`) để lấy SSID/password/confidence
- Hiển thị loading/error/success theo `statusMessage`

### ManualEntryScreen
- SSID
- Password
- Security type

### ReviewScreen
- Wi-Fi info card
- Edit form
- Confidence chip
- Action buttons

### HistoryScreen
- List
- Search
- Filter

### HistoryDetailScreen
- Full detail

### SettingsScreen
- App settings

## Shared states
- Loading
- Empty
- Error
- Permission denied
