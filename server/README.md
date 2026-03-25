# SmartWiFiConnect Server (Basic)

Backend API co ban de nhan OCR text tu app Android va parse thong tin WiFi (SSID/password).

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

### POST `/api/v1/wifi/connect-intent`
Tao payload goi y cho Android khi can tao QR hoac intent ket noi.

Request body:

```json
{
  "ssid": "MyHome",
  "password": "12345678"
}
```

## 4) Bien moi truong

Tao file `.env` tu `.env.example` neu can:

- `PORT`: cong server (mac dinh 8080)
- `ALLOWED_ORIGIN`: CORS origin (mac dinh `*`)

## 5) Test nhanh voi curl

```bash
curl -X POST http://localhost:8080/api/v1/ocr/parse \
  -H "Content-Type: application/json" \
  -d '{"ocrText":"Ten WiFi: OfficeNet\nMat khau: A1b2c3d4"}'
```
