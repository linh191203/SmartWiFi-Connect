# API Contract - SmartWiFi-Connect

## Mục tiêu
Mô tả các API backend mà app frontend sẽ gọi.

---

## 1. GET /health
### Mục đích
Kiểm tra server còn hoạt động.

### Request
```
GET /health
```

### Response (200 OK)
```json
{
  "ok": true,
  "service": "smartwificonnect-server",
  "uptimeSeconds": 123,
  "timestamp": "2026-04-28T10:30:45.123Z"
}
```

---

## 2. POST /api/v1/ocr/parse
### Mục đích
Parse OCR text hoặc Wi-Fi QR payload để trích xuất SSID, password, và thông tin bảo mật.

### Request
```
POST /api/v1/ocr/parse
Content-Type: application/json

{
  "ocrText": "WIFI:T:WPA;S:MyHome;P:12345678;;"
}
```

### Response (200 OK)
```json
{
  "ok": true,
  "data": {
    "ssid": "MyHome",
    "password": "12345678",
    "security": "WPA",
    "sourceFormat": "wifi_qr",
    "confidence": 0.98,
    "passwordOnly": false
  }
}
```

### Response (400 Bad Request)
```json
{
  "ok": false,
  "error": "Field 'ocrText' must be a string"
}
```

### Response (422 Unprocessable Entity)
```json
{
  "ok": false,
  "error": "Unable to extract WiFi data from OCR text"
}
```

### Examples

#### Wi-Fi QR Format
```json
{
  "ocrText": "WIFI:T:WPA;S:Office-5G;P:secure12345;;"
}
```

#### Labeled Text Format
```json
{
  "ocrText": "SSID: Cafe-Guest\nPassword: cafemomo123"
}
```

#### Vietnamese Labels
```json
{
  "ocrText": "Tên WiFi: HomeNet\nMật khẩu: abc123def456"
}
```

---

## 3. POST /api/ai/validate
### Mục đích
Xác nhận chất lượng dữ liệu Wi-Fi, đánh giá độ tin cây, và đưa ra khuyến nghị hành động (kết nối tự động, xem lại, hoặc thử lại OCR).

### Request
```
POST /api/ai/validate
Content-Type: application/json

{
  "ssid": "OfficeNet",
  "password": "A1b2c3d4",
  "ocrText": "Ten WiFi: OfficeNet\nMat khau: A1b2c3d4"
}
```

Ghi chú:
- Tất cả các trường là tùy chọn nhưng ít nhất một phải có
- `ssid` và `password` là dữ liệu người dùng nhập hoặc chỉnh sửa
- `ocrText` là kết quả từ OCR hoặc QR parse

### Response (200 OK)
```json
{
  "ok": true,
  "input": {
    "ssid": "OfficeNet",
    "password": "A1b2c3d4",
    "ocrText": "Ten WiFi: OfficeNet\nMat khau: A1b2c3d4"
  },
  "data": {
    "validated": true,
    "confidence": 0.88,
    "suggestion": "Du lieu WiFi co do tin cay tot, co the uu tien tu dong ket noi.",
    "flags": [],
    "normalizedSsid": "OfficeNet",
    "normalizedPassword": "A1b2c3d4",
    "parseRecommendation": "connect",
    "shouldAutoConnect": true
  },
  "timestamp": "2026-04-28T10:30:45.123Z"
}
```

### Response (400 Bad Request)
```json
{
  "ok": false,
  "error": "At least one of 'ssid', 'password', or 'ocrText' is required"
}
```

### Flags (khả năng của API)
- `missing_ssid` - Không có SSID
- `missing_password` - Không có password
- `ssid_too_long` - SSID > 32 ký tự
- `password_too_long` - Password > 63 ký tự
- `password_too_short` - Password < 8 ký tự (nhưng có thể hợp lệ)
- `password_contains_spaces` - Password có khoảng trắng
- `ssid_mismatch_with_ocr` - SSID nhập khác OCR
- `password_mismatch_with_ocr` - Password nhập khác OCR
- `ocr_ambiguous_characters` - Ký tự mơ hồ (ví dụ: I, l, 1, |)
- `ocr_parse_failed` - OCR parse thất bại

### Parse Recommendations
- `connect` - Độ tin cây cao, tự động kết nối
- `review` - Độ tin cây trung bình, xem lại trước kết nối
- `retry_ocr` - Độ tin cây thấp, thử OCR lại

---

## Error Handling

### 500 Internal Server Error
```json
{
  "ok": false,
  "error": "Internal server error"
}
```

### 404 Not Found
```json
{
  "ok": false,
  "error": "Endpoint not found"
}
```

---

## CORS Configuration

Server hỗ trợ CORS được cấu hình qua biến môi trường:
- `ALLOWED_ORIGINS` - Danh sách origin được phép, cách nhau bằng dấu phẩy
- `ALLOWED_ORIGIN` - Biến cũ, vẫn được hỗ trợ để tương thích ngược

### Ví dụ
```bash
ALLOWED_ORIGINS=http://localhost:5173,https://app.example.com
```

Nếu không cấu hình, mặc định cho phép tất cả origin (*)

---

## Timing & Limits

- Request timeout: 30 giây
- Max body size: 1 MB (cho OCR text và QR payloads)
- Response time: Thường < 100 ms

---

## Examples

### Full Flow: OCR Text → Parse → Validate → Save

1. **User scans OCR image**
   ```
   POST /api/v1/ocr/parse
   {"ocrText": "Ten WiFi: CafeGuest\nMat khau: 123456789"}
   ```
   Response:
   ```json
   {"ok": true, "data": {"ssid": "CafeGuest", "password": "123456789", ...}}
   ```

2. **Frontend shows data and user reviews/edits**
   ```
   POST /api/ai/validate
   {"ssid": "CafeGuest", "password": "123456789", "ocrText": "Ten WiFi: CafeGuest\nMat khau: 123456789"}
   ```
   Response:
   ```json
   {
     "ok": true,
     "data": {
       "validated": true,
       "confidence": 0.92,
       "parseRecommendation": "connect",
       "shouldAutoConnect": true
     }
   }
   ```

3. **Frontend saves to localStorage** (Web) or native store (Android/iOS)
   ```
   Data saved locally (no backend persistence needed)
   ```

---

## Tương lai (chưa implement)

Các endpoint sau có thể được thêm trong các phiên bản sau:
- `POST /api/networks` - Lưu mạng Wi-Fi lên backend
- `GET /api/networks` - Lấy danh sách mạng đã lưu
- `DELETE /api/networks/{id}` - Xóa mạng đã lưu
- `POST /api/user/register` - Đăng ký tài khoản
- `POST /api/user/login` - Đăng nhập tài khoản
