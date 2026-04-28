# Dummy Data & Test Examples - SmartWiFi-Connect

This document provides mock data and examples for testing the SmartWiFi-Connect APIs.

---

## Test Data Sets

### 1. Health Check Examples

#### Request
```bash
curl -X GET http://localhost:8080/health
```

#### Dummy Response
```json
{
  "ok": true,
  "service": "smartwificonnect-server",
  "uptimeSeconds": 3600,
  "timestamp": "2026-04-28T10:30:45.123Z"
}
```

---

## 2. OCR Parse Test Cases

### Test Case 1: Wi-Fi QR Format (Most Reliable)

**Input:**
```json
{
  "ocrText": "WIFI:T:WPA;S:Office-5G;P:secure12345;;"
}
```

**Expected Response:**
```json
{
  "ok": true,
  "data": {
    "ssid": "Office-5G",
    "password": "secure12345",
    "security": "WPA",
    "sourceFormat": "wifi_qr",
    "confidence": 0.98,
    "passwordOnly": false
  }
}
```

---

### Test Case 2: Labeled Text Format (Vietnamese)

**Input:**
```json
{
  "ocrText": "Tên WiFi: CafeGuest\nMật khẩu: cafemomo123"
}
```

**Expected Response:**
```json
{
  "ok": true,
  "data": {
    "ssid": "CafeGuest",
    "password": "cafemomo123",
    "sourceFormat": "labeled_text",
    "confidence": 0.85,
    "passwordOnly": false
  }
}
```

---

### Test Case 3: Two-Line Simple Format

**Input:**
```json
{
  "ocrText": "HomeNetwork\n12345678"
}
```

**Expected Response:**
```json
{
  "ok": true,
  "data": {
    "ssid": "HomeNetwork",
    "password": "12345678",
    "sourceFormat": "two_line_ssid_password",
    "confidence": 0.93,
    "passwordOnly": false
  }
}
```

---

### Test Case 4: Password Only (Single Line with Length >= 8)

**Input:**
```json
{
  "ocrText": "secure_password_123"
}
```

**Expected Response:**
```json
{
  "ok": true,
  "data": {
    "ssid": null,
    "password": "secure_password_123",
    "sourceFormat": "single_line_password",
    "confidence": 0.9,
    "passwordOnly": true
  }
}
```

---

### Test Case 5: English Labels

**Input:**
```json
{
  "ocrText": "SSID: MyNetwork\nPassword: MyPass1234"
}
```

**Expected Response:**
```json
{
  "ok": true,
  "data": {
    "ssid": "MyNetwork",
    "password": "MyPass1234",
    "sourceFormat": "labeled_text",
    "confidence": 0.85,
    "passwordOnly": false
  }
}
```

---

### Test Case 6: OCR Ambiguous Characters

**Input:**
```json
{
  "ocrText": "WIFI:T:WPA;S:Off|ce5G;P:secure|2345;;"
}
```

**Expected Response:**
```json
{
  "ok": true,
  "data": {
    "ssid": "Office5G",
    "password": "secure2345",
    "security": "WPA",
    "sourceFormat": "wifi_qr",
    "confidence": 0.95,
    "passwordOnly": false
  }
}
```
Note: The parser automatically converts `|` to `I`

---

### Test Case 7: Empty/Invalid Input

**Input:**
```json
{
  "ocrText": "   "
}
```

**Expected Response:**
```json
{
  "ok": false,
  "error": "Field 'ocrText' must not be empty"
}
```

---

## 3. AI Validate Test Cases

### Test Case 1: High Confidence - Should Auto Connect

**Input:**
```json
{
  "ssid": "OfficeNet",
  "password": "ComplexPass123!@#",
  "ocrText": "WIFI:T:WPA;S:OfficeNet;P:ComplexPass123!@#;;"
}
```

**Expected Response:**
```json
{
  "ok": true,
  "input": {
    "ssid": "OfficeNet",
    "password": "ComplexPass123!@#",
    "ocrText": "WIFI:T:WPA;S:OfficeNet;P:ComplexPass123!@#;;"
  },
  "data": {
    "validated": true,
    "confidence": 0.88,
    "suggestion": "Du lieu WiFi co do tin cay tot, co the uu tien tu dong ket noi.",
    "flags": [],
    "normalizedSsid": "OfficeNet",
    "normalizedPassword": "ComplexPass123!@#",
    "parseRecommendation": "connect",
    "shouldAutoConnect": true
  },
  "timestamp": "2026-04-28T10:30:45.123Z"
}
```

---

### Test Case 2: Medium Confidence - Should Review

**Input:**
```json
{
  "ssid": "CafeGuest",
  "password": "password123",
  "ocrText": "Ten: CafeGuest\nPass: pass123"
}
```

**Expected Response:**
```json
{
  "ok": true,
  "input": {
    "ssid": "CafeGuest",
    "password": "password123",
    "ocrText": "Ten: CafeGuest\nPass: pass123"
  },
  "data": {
    "validated": true,
    "confidence": 0.68,
    "suggestion": "Da co du lieu WiFi, nhung nen xac nhan lai truoc khi ket noi.",
    "flags": ["password_mismatch_with_ocr"],
    "normalizedSsid": "CafeGuest",
    "normalizedPassword": "password123",
    "parseRecommendation": "review",
    "shouldAutoConnect": false
  },
  "timestamp": "2026-04-28T10:30:45.123Z"
}
```

---

### Test Case 3: Low Confidence - Should Retry OCR

**Input:**
```json
{
  "ssid": "Net",
  "password": "abc",
  "ocrText": ""
}
```

**Expected Response:**
```json
{
  "ok": true,
  "input": {
    "ssid": "Net",
    "password": "abc",
    "ocrText": ""
  },
  "data": {
    "validated": true,
    "confidence": 0.35,
    "suggestion": "Da tim thay mat khau nhung chua co SSID. Nen kiem tra lai ten mang WiFi.",
    "flags": ["password_too_short"],
    "normalizedSsid": "Net",
    "normalizedPassword": "abc",
    "parseRecommendation": "retry_ocr",
    "shouldAutoConnect": false
  },
  "timestamp": "2026-04-28T10:30:45.123Z"
}
```

---

### Test Case 4: Missing Data - No SSID

**Input:**
```json
{
  "password": "12345678",
  "ocrText": ""
}
```

**Expected Response:**
```json
{
  "ok": true,
  "input": {
    "ssid": null,
    "password": "12345678",
    "ocrText": ""
  },
  "data": {
    "validated": true,
    "confidence": 0.35,
    "suggestion": "Da tim thay mat khau nhung chua co SSID. Nen kiem tra lai ten mang WiFi.",
    "flags": ["missing_ssid"],
    "normalizedSsid": null,
    "normalizedPassword": "12345678",
    "parseRecommendation": "retry_ocr",
    "shouldAutoConnect": false
  },
  "timestamp": "2026-04-28T10:30:45.123Z"
}
```

---

### Test Case 5: SSID Too Long

**Input:**
```json
{
  "ssid": "ThisIsAVeryLongWiFiNetworkNameThatExceedsThirtyTwoCharacterLimit",
  "password": "ValidPass123"
}
```

**Expected Response:**
```json
{
  "ok": true,
  "input": {
    "ssid": "ThisIsAVeryLongWiFiNetworkNameThatExceedsThirtyTwoCharacterLimit",
    "password": "ValidPass123",
    "ocrText": null
  },
  "data": {
    "validated": false,
    "confidence": 0.05,
    "suggestion": "Khong du du lieu WiFi. Nen OCR lai hoac nhap tay.",
    "flags": ["ssid_too_long"],
    "normalizedSsid": "ThisIsAVeryLongWiFiNetworkNameThatExceedsThirtyTwoCharacterLimit",
    "normalizedPassword": "ValidPass123",
    "parseRecommendation": "retry_ocr",
    "shouldAutoConnect": false
  },
  "timestamp": "2026-04-28T10:30:45.123Z"
}
```

---

### Test Case 6: All Flags Triggered

**Input:**
```json
{
  "ssid": "VeryLongNetworkNameThatIsWayTooLongForWiFiStandard",
  "password": "short",
  "ocrText": "WIFI:T:WPA;S:Different|Network;P:other|pass;;"
}
```

**Expected Response:**
```json
{
  "ok": true,
  "input": {
    "ssid": "VeryLongNetworkNameThatIsWayTooLongForWiFiStandard",
    "password": "short",
    "ocrText": "WIFI:T:WPA;S:Different|Network;P:other|pass;;"
  },
  "data": {
    "validated": false,
    "confidence": 0.01,
    "suggestion": "Khong du du lieu WiFi. Nen OCR lai hoac nhap tay.",
    "flags": [
      "ssid_too_long",
      "password_too_short",
      "ssid_mismatch_with_ocr",
      "password_mismatch_with_ocr",
      "ocr_ambiguous_characters"
    ],
    "normalizedSsid": "VeryLongNetworkNameThatIsWayTooLongForWiFiStandard",
    "normalizedPassword": "short",
    "parseRecommendation": "retry_ocr",
    "shouldAutoConnect": false
  },
  "timestamp": "2026-04-28T10:30:45.123Z"
}
```

---

### Test Case 7: Only OCR Text Provided

**Input:**
```json
{
  "ocrText": "WIFI:T:WPA2;S:HomeNetwork;P:secure_pass_123;;"
}
```

**Expected Response:**
```json
{
  "ok": true,
  "input": {
    "ssid": null,
    "password": null,
    "ocrText": "WIFI:T:WPA2;S:HomeNetwork;P:secure_pass_123;;"
  },
  "data": {
    "validated": true,
    "confidence": 0.75,
    "suggestion": "Da co du lieu WiFi, nhung nen xac nhan lai truoc khi ket noi.",
    "flags": [],
    "normalizedSsid": "HomeNetwork",
    "normalizedPassword": "secure_pass_123",
    "parseRecommendation": "review",
    "shouldAutoConnect": false
  },
  "timestamp": "2026-04-28T10:30:45.123Z"
}
```

---

## 4. Error Cases

### Missing Required Field

**Request:**
```bash
curl -X POST http://localhost:8080/api/v1/ocr/parse \
  -H "Content-Type: application/json" \
  -d '{"someOtherField": "value"}'
```

**Response (400):**
```json
{
  "ok": false,
  "error": "Field 'ocrText' must be a string"
}
```

---

### Invalid Data Type

**Request:**
```bash
curl -X POST http://localhost:8080/api/ai/validate \
  -H "Content-Type: application/json" \
  -d '{"ssid": 123, "password": null, "ocrText": "some text"}'
```

**Response (400):**
```json
{
  "ok": false,
  "error": "Fields 'ssid', 'password', and 'ocrText' must be strings when provided"
}
```

---

### All Fields Empty

**Request:**
```bash
curl -X POST http://localhost:8080/api/ai/validate \
  -H "Content-Type: application/json" \
  -d '{"ssid": "", "password": "", "ocrText": ""}'
```

**Response (400):**
```json
{
  "ok": false,
  "error": "At least one of 'ssid', 'password', or 'ocrText' is required"
}
```

---

### Endpoint Not Found

**Request:**
```bash
curl -X GET http://localhost:8080/api/unknown-endpoint
```

**Response (404):**
```json
{
  "ok": false,
  "error": "Endpoint not found"
}
```

---

## 5. Integration Test Scenario

### Full User Flow: OCR → Parse → Validate → Save

```
1. User takes picture of Wi-Fi sign with OCR data:
   "Tên WiFi: CoffeeShop
    Mật khẩu: bean123456"

2. Frontend sends:
   POST /api/v1/ocr/parse
   {"ocrText": "Tên WiFi: CoffeeShop\nMật khẩu: bean123456"}

3. Backend returns:
   {
     "ok": true,
     "data": {
       "ssid": "CoffeeShop",
       "password": "bean123456",
       "sourceFormat": "labeled_text",
       "confidence": 0.85
     }
   }

4. Frontend shows result to user, user can edit:
   - SSID: CoffeeShop (unchanged)
   - Password: bean123456 (user confirms it's correct)

5. Frontend sends to validate:
   POST /api/ai/validate
   {
     "ssid": "CoffeeShop",
     "password": "bean123456",
     "ocrText": "Tên WiFi: CoffeeShop\nMật khẩu: bean123456"
   }

6. Backend returns validation:
   {
     "ok": true,
     "data": {
       "validated": true,
       "confidence": 0.88,
       "parseRecommendation": "connect",
       "shouldAutoConnect": true
     }
   }

7. Frontend saves to localStorage (browser) or native storage (mobile)
   and shows success message: "Network saved to browser history"
```

---

## 6. Performance Benchmarks (Target)

| Endpoint | Target | Note |
|----------|--------|------|
| `/health` | < 50ms | Simple status check |
| `/api/v1/ocr/parse` | < 200ms | OCR text parsing with heuristics |
| `/api/ai/validate` | < 100ms | Validation scoring |
| Error responses | < 10ms | Quick validation failures |

---

## 7. Load Test Recommendations

```bash
# Using Apache Bench for simple load test
ab -n 1000 -c 10 http://localhost:8080/health

# Using curl for single request
curl -v http://localhost:8080/health
```

---

## 8. Browser Testing via DevTools Console

```javascript
// Test health check
fetch('http://localhost:8080/health')
  .then(r => r.json())
  .then(d => console.log(d))

// Test OCR parse
fetch('http://localhost:8080/api/v1/ocr/parse', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ ocrText: 'WIFI:T:WPA;S:Test;P:pass;' })
})
  .then(r => r.json())
  .then(d => console.log(d))

// Test AI validate
fetch('http://localhost:8080/api/ai/validate', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    ssid: 'TestNet',
    password: 'password123',
    ocrText: 'WIFI:T:WPA;S:TestNet;P:password123;;'
  })
})
  .then(r => r.json())
  .then(d => console.log(d))
```

---

## Resources

- API Contract: [api-contract.md](./api-contract.md)
- Testing Guide: [testing.md](./testing.md)
- Postman Collection: Available on request
