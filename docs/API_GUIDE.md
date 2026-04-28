# SmartWiFi-Connect API Implementation Guide

Complete guide for understanding, using, and testing the SmartWiFi-Connect APIs.

---

## 📋 Quick Reference

### Running the Application

**Backend:**
```bash
cd server
npm install
npm run dev        # Development mode
npm start          # Production mode
npm test           # Run tests
npm run test:smoke # Smoke tests
```

**Frontend:**
```bash
cd web
npm install
npm run dev     # Development
npm run build   # Production build
```

---

## 🔌 API Endpoints Overview

### 1. Health Check
```
GET /health
```
- **Purpose:** Verify backend is running
- **Response Time:** < 50ms
- **Use Case:** Connection validation in Settings screen

### 2. OCR Parse
```
POST /api/v1/ocr/parse
```
- **Purpose:** Parse OCR text and Wi-Fi QR codes
- **Input:** `ocrText` (string)
- **Output:** SSID, password, security, confidence, source format
- **Response Time:** < 200ms
- **Use Case:** Parse scanned images or QR codes

### 3. AI Validate
```
POST /api/ai/validate
```
- **Purpose:** Validate Wi-Fi data quality and confidence
- **Input:** `ssid`, `password`, `ocrText` (all optional, one required)
- **Output:** Validation result, confidence, flags, recommendation
- **Response Time:** < 100ms
- **Use Case:** Score data before auto-connecting or suggest user review

---

## 📚 Documentation Files

| File | Purpose |
|------|---------|
| [api-contract.md](./api-contract.md) | Complete API specification |
| [DUMMY_DATA.md](./DUMMY_DATA.md) | Test data and examples |
| [TESTING_GUIDE.md](./TESTING_GUIDE.md) | Testing procedures |
| [../server/README.md](../server/README.md) | Backend setup guide |
| [architecture.md](./architecture.md) | System architecture |

---

## 🏗️ Architecture

```
Frontend (React)           Backend (Node.js)
├── Pages                  ├── Health endpoint
│   ├── HomeScreen         ├── OCR Parser
│   ├── ScanImage    ---→  ├── AI Validator
│   ├── QrScanner          ├── Error handlers
│   ├── ManualEntry        └── CORS middleware
│   ├── Review
│   ├── History
│   └── Settings
├── Context (AppState) --- wifiRepository --- API calls
└── localStorage           └── 3 main endpoints
```

### Data Flow

```
User Input → Page Component → AppState Action → wifiRepository
     ↓                            ↓
  Edit SSID         1. parseOcr()
  Edit Password     2. validateWifi()
  Validate          3. connectCurrent()
                         ↓
                    Backend API
                         ↓
                    Response → UI Update
```

---

## 🔄 Full User Flow

### Scenario: Scan Wi-Fi QR Code

1. **User scans QR code on café sign**
   - OCR extracts: `WIFI:T:WPA;S:CafeGuest;P:cafemomo123;;`

2. **Frontend calls Parse API**
   ```javascript
   POST /api/v1/ocr/parse
   {"ocrText": "WIFI:T:WPA;S:CafeGuest;P:cafemomo123;;"}
   ```

3. **Backend responds**
   ```json
   {
     "ok": true,
     "data": {
       "ssid": "CafeGuest",
       "password": "cafemomo123",
       "confidence": 0.98,
       "sourceFormat": "wifi_qr"
     }
   }
   ```

4. **Frontend shows Review Screen**
   - User can see and edit SSID/password
   - Checkbox to save password

5. **User clicks Save Network**
   - Data saved to localStorage (browser)
   - Optional: Call validate API for confidence score

6. **Frontend calls Validate API (optional)**
   ```javascript
   POST /api/ai/validate
   {
     "ssid": "CafeGuest",
     "password": "cafemomo123",
     "ocrText": "WIFI:T:WPA;S:CafeGuest;P:cafemomo123;;"
   }
   ```

7. **Backend responds with validation**
   ```json
   {
     "ok": true,
     "data": {
       "validated": true,
       "confidence": 0.88,
       "parseRecommendation": "connect",
       "shouldAutoConnect": true
     }
   }
   ```

8. **Network saved to History**
   - User can see in History screen
   - Can delete or re-use later

---

## 🧪 Testing Workflows

### Quick Manual Test

1. **Start both services:**
   ```bash
   # Terminal 1
   cd server && npm run dev
   
   # Terminal 2
   cd web && npm run dev
   ```

2. **Open http://localhost:5173**

3. **Test OCR Parse:**
   - Go to "Paste OCR text" screen
   - Click "Use sample text"
   - Click "Parse OCR text"
   - Should show parsed result

4. **Test QR Parse:**
   - Go to "Parse Wi-Fi QR payload" screen
   - Click "Use sample QR"
   - Click "Parse QR payload"
   - Should show parsed result

5. **Test Review & Save:**
   - Click "Save network" on Review screen
   - Check History screen
   - Network should appear in saved list

### Programmatic Testing

```javascript
// Test in browser console

// 1. Test health
fetch('http://localhost:8080/health').then(r => r.json()).then(d => console.log(d))

// 2. Test parse
fetch('http://localhost:8080/api/v1/ocr/parse', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ ocrText: 'WIFI:T:WPA;S:Test;P:pass;;' })
}).then(r => r.json()).then(d => console.log(d))

// 3. Test validate
fetch('http://localhost:8080/api/ai/validate', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ ssid: 'Test', password: 'pass' })
}).then(r => r.json()).then(d => console.log(d))
```

---

## ⚙️ Configuration

### Backend Environment Variables

Create `.env` in `server/` directory:

```bash
# Server port (default: 8080)
PORT=8080

# CORS allowed origins (comma-separated)
ALLOWED_ORIGINS=http://localhost:5173,https://app.example.com

# For development (allows all origins)
ALLOWED_ORIGINS=*
```

### Frontend Environment Variables

Create `.env` in `web/` directory:

```bash
# Backend API base URL (default: http://localhost:8080)
VITE_API_BASE_URL=http://localhost:8080
```

---

## 🐛 Common Issues & Solutions

### Issue: API returns 404 Not Found
**Solution:**
- Verify backend is running (`npm run dev`)
- Check endpoint path is exactly correct
- Restart backend server

### Issue: CORS error in browser
**Solution:**
- Add frontend URL to `ALLOWED_ORIGINS` in `.env`
- Format: `http://localhost:5173` (no trailing slash)
- Restart backend after changing `.env`

### Issue: Cannot connect to API
**Solution:**
- Check API base URL in Settings screen
- Ensure backend is running on correct port
- Test with `curl http://localhost:8080/health`

### Issue: OCR parse returns empty data
**Solution:**
- Check OCR text format matches supported patterns
- Try sample text from DUMMY_DATA.md
- Ensure text has recognizable keywords (WiFi, SSID, Password, etc.)

### Issue: Validation returns low confidence
**Solution:**
- Check SSID is ≤ 32 characters
- Check password is 8-63 characters
- Remove ambiguous characters (I, l, 1, |)
- Ensure SSID/password match OCR text

---

## 📊 API Response Formats

### Success Response
```json
{
  "ok": true,
  "data": { /* specific endpoint data */ },
  "timestamp": "2026-04-28T10:30:45.123Z"
}
```

### Error Response
```json
{
  "ok": false,
  "error": "Descriptive error message"
}
```

### HTTP Status Codes
- `200 OK` - Request successful
- `400 Bad Request` - Invalid input data
- `404 Not Found` - Endpoint doesn't exist
- `500 Internal Server Error` - Server error

---

## 🎯 Performance Targets

| Operation | Target | Typical |
|-----------|--------|---------|
| Health check | < 50ms | 5-10ms |
| OCR parse | < 200ms | 50-100ms |
| AI validate | < 100ms | 20-50ms |
| API startup | < 2s | 1s |

---

## 📦 Dependencies

### Backend
```json
{
  "cors": "^2.8.5",
  "dotenv": "^16.4.7",
  "express": "^4.21.2",
  "jest": "^30.3.0",
  "nodemon": "^3.1.9",
  "supertest": "^7.2.2"
}
```

### Frontend
- React 18+
- Vite
- React Router
- localStorage API
- Fetch API

---

## 🚀 Deployment

### Backend Deployment
```bash
# Production build
cd server
npm install --production
NODE_ENV=production npm start

# Recommended ports: 8000, 8080 (from behind proxy)
# Use environment variables for configuration
```

### Frontend Deployment
```bash
# Production build
cd web
npm install
npm run build

# Upload dist/ to CDN (Vercel, Netlify, etc.)
# Or serve from static host (GitHub Pages, Firebase, etc.)
```

---

## 📞 API Usage Examples

### JavaScript/Frontend
```javascript
// Using wifiRepository (recommended)
const repo = createWifiRepository();

// Health check
const health = await repo.checkHealth('http://localhost:8080');

// Parse OCR
const parsed = await repo.parseOcr('http://localhost:8080', 'WIFI:T:WPA;S:Net;P:pass;;');

// Validate WiFi
const validated = await repo.validateWifi('http://localhost:8080', {
  ssid: 'Net',
  password: 'pass'
});
```

### cURL/CLI
```bash
# Health
curl http://localhost:8080/health

# Parse
curl -X POST http://localhost:8080/api/v1/ocr/parse \
  -H "Content-Type: application/json" \
  -d '{"ocrText":"WIFI:T:WPA;S:Net;P:pass;;"}'

# Validate
curl -X POST http://localhost:8080/api/ai/validate \
  -H "Content-Type: application/json" \
  -d '{"ssid":"Net","password":"pass"}'
```

### Python
```python
import requests

# Health
response = requests.get('http://localhost:8080/health')
print(response.json())

# Parse
response = requests.post('http://localhost:8080/api/v1/ocr/parse',
  json={'ocrText': 'WIFI:T:WPA;S:Net;P:pass;;'})
print(response.json())

# Validate
response = requests.post('http://localhost:8080/api/ai/validate',
  json={'ssid': 'Net', 'password': 'pass'})
print(response.json())
```

---

## 📋 Checklist for Implementation

- [x] Backend API endpoints implemented
- [x] Error handling added
- [x] CORS configured
- [x] Frontend integration complete
- [x] Mock responses created
- [x] Documentation written
- [x] Test guide created
- [x] Dummy data provided
- [x] Validation working
- [x] localStorage integration
- [ ] Database persistence (future)
- [ ] Authentication (future)
- [ ] Encryption (future)

---

## 📞 Support

For issues or questions:
1. Check [TESTING_GUIDE.md](./TESTING_GUIDE.md) for common issues
2. Review [DUMMY_DATA.md](./DUMMY_DATA.md) for examples
3. Run tests: `cd server && npm test`
4. Check server logs for errors
5. Use browser DevTools Network tab

---

## 📄 License & Credits

SmartWiFi-Connect - Web app for intelligent Wi-Fi connection
- Built with React + Node.js
- Supports Web, Android, and iOS via Capacitor
