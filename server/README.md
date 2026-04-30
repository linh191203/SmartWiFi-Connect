# SmartWiFiConnect Server (Basic)

Neu ban moi setup du an lan dau, hay doc README goc truoc:

- ../README.md

Backend API toi gian de nhan OCR text tu app Android, parse thong tin WiFi (SSID/password), va validate du lieu ket noi.

## 1) Cai dat

```bash
cd server
npm install
```

## 2) Chay server

```bash
npm run dev
```

Hoac:

```bash
npm start
```

Mac dinh server chay o `http://localhost:8080`.

## 3) API

### GET `/health`
Kiem tra trang thai server.

### POST `/api/v1/ocr/parse`
Parse OCR text.

Request body:

```json
{
  "ocrText": "WIFI:T:WPA;S:MyHome;P:12345678;;"
}
```

Response (vi du):

```json
{
  "ok": true,
  "data": {
    "ssid": "MyHome",
    "password": "12345678",
    "sourceFormat": "wifi_qr",
    "confidence": 0.98,
    "passwordOnly": false
  }
}
```

### POST `/api/ai/validate`
Danh gia chat luong du lieu WiFi de app co the uu tien tu dong ket noi hoac yeu cau nguoi dung kiem tra lai.

Request body:

```json
{
  "ssid": "OfficeNet",
  "password": "A1b2c3d4",
  "ocrText": "Ten WiFi: OfficeNet\nMat khau: A1b2c3d4"
}
```

Response (vi du):

```json
{
  "ok": true,
  "data": {
    "validated": true,
    "confidence": 0.88,
    "suggestion": "Du lieu WiFi co do tin cay tot, co the uu tien tu dong ket noi.",
    "flags": [],
    "normalizedSsid": "OfficeNet",
    "normalizedPassword": "A1b2c3d4",
    "parseRecommendation": "connect",
    "shouldAutoConnect": true
  }
}
```

## 4) Bien moi truong

Tao file `.env` tu `.env.example` neu can:

- `PORT`: cong server (mac dinh 8080)
- `ALLOWED_ORIGINS`: danh sach origin cho phep, cach nhau boi dau phay (vi du `https://app.example.com,https://admin.example.com`)
- `ALLOWED_ORIGIN`: bien cu, van duoc ho tro tuong thich nguoc

## 5) Test nhanh voi curl

```bash
curl -X POST http://localhost:8080/api/v1/ocr/parse \
  -H "Content-Type: application/json" \
  -d '{"ocrText":"Ten WiFi: OfficeNet\nMat khau: A1b2c3d4"}'
```
