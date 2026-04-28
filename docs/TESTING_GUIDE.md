# Testing Guide - SmartWiFi-Connect APIs

Complete testing guide for SmartWiFi-Connect backend APIs and frontend integration.

---

## Quick Start Testing

### 1. Start Backend Server
```bash
cd server
npm install
npm run dev
```

Server will run at `http://localhost:8080`

---

### 2. Start Frontend App
```bash
cd web
npm install
npm run dev
```

Frontend will run at `http://localhost:5173`

---

### 3. Test Health Check
```bash
curl http://localhost:8080/health
```

Expected output:
```json
{
  "ok": true,
  "service": "smartwificonnect-server",
  "uptimeSeconds": 0,
  "timestamp": "2026-04-28T10:30:45.123Z"
}
```

---

## Manual Testing with cURL

### Test OCR Parse Endpoint

#### Test 1: Valid Wi-Fi QR Format
```bash
curl -X POST http://localhost:8080/api/v1/ocr/parse \
  -H "Content-Type: application/json" \
  -d '{"ocrText":"WIFI:T:WPA;S:Office-5G;P:secure12345;;"}'
```

#### Test 2: Labeled Text Format
```bash
curl -X POST http://localhost:8080/api/v1/ocr/parse \
  -H "Content-Type: application/json" \
  -d '{"ocrText":"Tên WiFi: CafeGuest\nMật khẩu: cafemomo123"}'
```

#### Test 3: Empty Input (Error Case)
```bash
curl -X POST http://localhost:8080/api/v1/ocr/parse \
  -H "Content-Type: application/json" \
  -d '{"ocrText":""}'
```

---

### Test AI Validate Endpoint

#### Test 1: Full Data with OCR
```bash
curl -X POST http://localhost:8080/api/ai/validate \
  -H "Content-Type: application/json" \
  -d '{
    "ssid": "OfficeNet",
    "password": "ComplexPass123!@#",
    "ocrText": "WIFI:T:WPA;S:OfficeNet;P:ComplexPass123!@#;;"
  }'
```

#### Test 2: Only SSID and Password
```bash
curl -X POST http://localhost:8080/api/ai/validate \
  -H "Content-Type: application/json" \
  -d '{
    "ssid": "MyNetwork",
    "password": "password123"
  }'
```

#### Test 3: Missing SSID (Error)
```bash
curl -X POST http://localhost:8080/api/ai/validate \
  -H "Content-Type: application/json" \
  -d '{
    "password": "12345678"
  }'
```

---

## Browser Console Testing

Open DevTools (F12) in your browser and run these JavaScript commands:

### Test 1: Health Check
```javascript
fetch('http://localhost:8080/health')
  .then(r => r.json())
  .then(d => console.log('Health:', d))
  .catch(e => console.error('Error:', e))
```

### Test 2: Parse OCR Text
```javascript
fetch('http://localhost:8080/api/v1/ocr/parse', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ 
    ocrText: 'WIFI:T:WPA;S:TestNet;P:pass123;;' 
  })
})
  .then(r => r.json())
  .then(d => console.log('Parse:', d))
  .catch(e => console.error('Error:', e))
```

### Test 3: Validate WiFi
```javascript
fetch('http://localhost:8080/api/ai/validate', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    ssid: 'TestNet',
    password: 'pass123456',
    ocrText: 'WIFI:T:WPA;S:TestNet;P:pass123456;;'
  })
})
  .then(r => r.json())
  .then(d => console.log('Validate:', d))
  .catch(e => console.error('Error:', e))
```

---

## Unit Tests (Backend)

Run backend tests:
```bash
cd server
npm test
```

View test files:
- `server/tests/aiValidator.test.js`
- `server/tests/api.test.js`
- `server/tests/ocrParser.test.js`

---

## Smoke Test

Quick smoke test to verify endpoints are working:
```bash
cd server
npm run test:smoke
```

This runs `scripts/smoke-test.js` which tests:
1. Health check
2. OCR parse with various inputs
3. AI validate with different scenarios

---

## Frontend Integration Testing

### In React App (http://localhost:5173)

#### Test 1: Check Backend Health
1. Open Settings screen
2. Verify "Backend" field shows the configured base URL
3. Check the status indicator (should be green if connected)

#### Test 2: Parse OCR Text
1. Go to "Paste OCR text" screen
2. Click "Use sample text" button
3. Click "Parse OCR text"
4. Verify parsed result shows in Review screen

#### Test 3: Parse QR Payload
1. Go to "Parse Wi-Fi QR payload" screen  
2. Click "Use sample QR"
3. Click "Parse QR payload"
4. Verify parsed result shows in Review screen

#### Test 4: Manual Entry + Validation
1. Go to "Manual entry" screen
2. Enter SSID and password manually
3. Click "Continue"
4. On Review screen, click "Validate" (if implemented)
5. Verify validation result

#### Test 5: Save Network
1. After parsing/validating, click "Save network" on Review screen
2. Verify message shows "Network saved to browser history"
3. Go to History screen
4. Verify network appears in saved networks list

---

## Performance Testing

### Measure API Response Times

#### Using curl with time measurement:
```bash
curl -w "Total time: %{time_total}s\n" \
  http://localhost:8080/health
```

#### Expected Response Times:
- `/health` - < 50ms
- `/api/v1/ocr/parse` - < 200ms  
- `/api/ai/validate` - < 100ms

### Load Testing (Optional)
```bash
# Using Apache Bench (if installed)
ab -n 100 -c 10 http://localhost:8080/health

# Using wrk (if installed)
wrk -t12 -c400 -d30s http://localhost:8080/health
```

---

## Error Testing

### Test Invalid Requests

#### Test 1: Missing Required Field
```bash
curl -X POST http://localhost:8080/api/v1/ocr/parse \
  -H "Content-Type: application/json" \
  -d '{}'
```
Expected: 400 Bad Request

#### Test 2: Wrong Data Type
```bash
curl -X POST http://localhost:8080/api/v1/ocr/parse \
  -H "Content-Type: application/json" \
  -d '{"ocrText": 123}'
```
Expected: 400 Bad Request

#### Test 3: Invalid Endpoint
```bash
curl http://localhost:8080/api/unknown-endpoint
```
Expected: 404 Not Found

#### Test 4: Method Not Allowed
```bash
curl -X PUT http://localhost:8080/api/v1/ocr/parse
```
Expected: 404 Not Found (since PUT is not defined)

---

## CORS Testing

### Test CORS from Different Origin

#### Setup: Configure allowed origins in `.env`
```bash
# .env in server/
ALLOWED_ORIGINS=http://localhost:5173,http://localhost:3000
```

#### Test with curl (include Origin header):
```bash
curl -H "Origin: http://localhost:5173" \
  -H "Access-Control-Request-Method: POST" \
  -X OPTIONS http://localhost:8080/api/v1/ocr/parse -v
```

#### Test with curl from different origin (should fail):
```bash
curl -H "Origin: http://malicious-site.com" \
  -X POST http://localhost:8080/api/v1/ocr/parse \
  -H "Content-Type: application/json" \
  -d '{"ocrText":"test"}'
```

---

## Debugging Tips

### Enable Verbose Logging in Backend
```bash
# Add to .env
DEBUG=*
```

### View Frontend Network Requests
1. Open DevTools (F12)
2. Go to Network tab
3. Perform an action (parse, validate, etc.)
4. Click on network request to view details
5. Check request/response headers and body

### Check Browser Console for Errors
1. Open DevTools (F12)
2. Go to Console tab
3. Look for red error messages
4. Click on error to view stack trace

### Check Backend Server Logs
Look for console output in terminal where you ran `npm run dev`

---

## Test Checklist

Use this checklist to verify all functionality works:

### Backend API
- [ ] GET /health returns 200 OK
- [ ] POST /api/v1/ocr/parse with valid input returns 200 OK
- [ ] POST /api/v1/ocr/parse with invalid input returns 400/422
- [ ] POST /api/ai/validate with valid input returns 200 OK  
- [ ] POST /api/ai/validate with invalid input returns 400
- [ ] Invalid endpoints return 404
- [ ] CORS headers present in response
- [ ] Server handles errors gracefully

### Frontend Integration
- [ ] Health check shows backend status
- [ ] OCR parse screen parses text correctly
- [ ] QR parse screen parses QR payload correctly
- [ ] Manual entry screen allows custom data entry
- [ ] Review screen shows parsed data editable
- [ ] Save network button saves to localStorage
- [ ] History screen shows saved networks
- [ ] Settings screen allows API base URL change
- [ ] Error messages display properly
- [ ] Loading state shows during API calls

### Error Handling
- [ ] Empty OCR text shows error
- [ ] Invalid JSON returns 400 error
- [ ] Missing required fields show error
- [ ] Backend down shows connection error
- [ ] Invalid API URL shows error

---

## Troubleshooting

### API Returns 404 Not Found
- Verify server is running (`npm run dev`)
- Check if endpoint path is correct
- Verify CORS is not blocking request

### API Returns 400 Bad Request
- Check request body JSON is valid
- Verify all required fields are present
- Check data types match specification

### Frontend Can't Connect to Backend
- Verify backend is running on correct port
- Check `ALLOWED_ORIGINS` includes frontend URL
- Verify API base URL in settings is correct
- Check browser console for CORS errors

### OCR Parse Returns No Data
- Verify OCR text format is correct
- Check if text contains recognizable patterns (WIFI:, labels, etc.)
- Try with sample text from DUMMY_DATA.md

### Validation Returns Low Confidence
- Check SSID length (should be ≤ 32 chars)
- Check password length (should be 8-63 chars)
- Check for ambiguous characters (I, l, 1, |)
- Verify SSID and password match OCR text

---

## Resources

- [API Contract](./api-contract.md)
- [Dummy Data Examples](./DUMMY_DATA.md)
- [Architecture](./architecture.md)
- [Backend README](../server/README.md)
