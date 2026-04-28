# API Contract - SmartWiFi-Connect

## Mục tiêu
Mô tả các API backend mà app Android sẽ gọi.

---

## 1. GET /api/health
### Mục đích
Kiểm tra server còn hoạt động.

### Response mẫu
```json
{
  "status": "ok"
}
```

---

## 2. POST /api/v1/ocr/parse
### Mục đích
FE gửi text OCR để BE parse ra thông tin Wi-Fi.

### Request mẫu
```json
{
  "ocrText": "WIFI:S:Home_Cloud_5G;T:WPA;P:secret123;;"
}
```

### Response mẫu
```json
{
  "ok": true,
  "data": {
    "ssid": "Home_Cloud_5G",
    "password": "secret123",
    "security": "WPA",
    "sourceFormat": "qr",
    "confidence": 0.98
  }
}
```

---

## 3. POST /api/ai/validate
### Mục đích
FE gửi kết quả OCR/parse để BE AI validate, normalize và đưa recommendation.

### Request mẫu
```json
{
  "ssid": "Home_Cloud_5G",
  "password": "secret123",
  "ocrText": "WIFI:S:Home_Cloud_5G;T:WPA;P:secret123;;"
}
```

### Response mẫu
```json
{
  "ok": true,
  "data": {
    "validated": true,
    "confidence": 0.94,
    "suggestion": "Thông tin Wi-Fi hợp lệ.",
    "flags": [],
    "parseRecommendation": "connect",
    "shouldAutoConnect": true,
    "normalizedSsid": "Home_Cloud_5G",
    "normalizedPassword": "secret123"
  }
}
```

---

## 4. POST /api/v1/ssid/fuzzy-match
### Mục đích
FE gửi SSID đọc từ OCR và danh sách Wi-Fi xung quanh để BE fuzzy match.

### Request mẫu
```json
{
  "ocrSsid": "Home Cloud 5G",
  "nearbyNetworks": [
    {
      "ssid": "Home_Cloud_5G",
      "signalLevel": 4
    },
    {
      "ssid": "Guest_WiFi",
      "signalLevel": 2
    }
  ]
}
```

### Response mẫu
```json
{
  "ok": true,
  "data": {
    "bestMatch": "Home_Cloud_5G",
    "score": 0.91,
    "matches": [
      {
        "ssid": "Home_Cloud_5G",
        "signalLevel": 4,
        "score": 0.91
      }
    ]
  }
}
```
