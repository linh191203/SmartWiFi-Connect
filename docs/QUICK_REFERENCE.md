# SmartWiFi-Connect API - Quick Reference Card

**Last Updated:** 2026-04-28

---

## ⚡ Quick Start

```bash
# Terminal 1: Backend
cd server && npm install && npm run dev
# Runs on http://localhost:8080

# Terminal 2: Frontend  
cd web && npm install && npm run dev
# Runs on http://localhost:5173
```

---

## 🔌 API Endpoints

| Method | Endpoint | Purpose | Status |
|--------|----------|---------|--------|
| GET | `/health` | Check server | ✅ |
| POST | `/api/v1/ocr/parse` | Parse OCR text | ✅ |
| POST | `/api/ai/validate` | Validate Wi-Fi data | ✅ |

---

## 📤 Request/Response Examples

### Health Check
```bash
curl http://localhost:8080/health
```
**Response:** `{"ok":true,"service":"smartwificonnect-server","uptimeSeconds":123,"timestamp":"..."}`

### Parse OCR
```bash
curl -X POST http://localhost:8080/api/v1/ocr/parse \
  -H "Content-Type: application/json" \
  -d '{"ocrText":"WIFI:T:WPA;S:MyNet;P:pass123;;"}'
```
**Response:** `{"ok":true,"data":{"ssid":"MyNet","password":"pass123","confidence":0.98,...}}`

### Validate WiFi
```bash
curl -X POST http://localhost:8080/api/ai/validate \
  -H "Content-Type: application/json" \
  -d '{"ssid":"MyNet","password":"pass123"}'
```
**Response:** `{"ok":true,"data":{"validated":true,"confidence":0.88,"parseRecommendation":"connect",...}}`

---

## 🧪 Quick Tests

| Test | Command |
|------|---------|
| Health | `curl http://localhost:8080/health` |
| Parse (QR) | `curl -X POST ... -d '{"ocrText":"WIFI:T:WPA;S:Test;P:pass;;"}' ...` |
| Parse (Text) | `curl -X POST ... -d '{"ocrText":"WiFi: Test\nPassword: pass"}' ...` |
| Validate | `curl -X POST ... -d '{"ssid":"Test","password":"pass"}' ...` |

---

## 📚 Documentation Files

| File | Purpose |
|------|---------|
| **API_GUIDE.md** | 📌 Start here - Complete guide |
| **api-contract.md** | Full API specification |
| **DUMMY_DATA.md** | Test data & examples |
| **TESTING_GUIDE.md** | Testing procedures |

---

## 🎯 Common Code Snippets

### Browser Console Test
```javascript
// Test health
fetch('http://localhost:8080/health').then(r=>r.json()).then(console.log)

// Test parse
fetch('http://localhost:8080/api/v1/ocr/parse',{
  method:'POST',
  headers:{'Content-Type':'application/json'},
  body:JSON.stringify({ocrText:'WIFI:T:WPA;S:Test;P:pass;;'})
}).then(r=>r.json()).then(console.log)

// Test validate
fetch('http://localhost:8080/api/ai/validate',{
  method:'POST',
  headers:{'Content-Type':'application/json'},
  body:JSON.stringify({ssid:'Test',password:'pass'})
}).then(r=>r.json()).then(console.log)
```

### Frontend Integration
```javascript
// Import repository
import { createWifiRepository } from './lib/wifiRepository'

// Create instance
const repo = createWifiRepository()

// Use endpoints
const health = await repo.checkHealth('http://localhost:8080')
const parsed = await repo.parseOcr('http://localhost:8080', 'WIFI:T:WPA;S:Test;P:pass;;')
const validated = await repo.validateWifi('http://localhost:8080', {
  ssid: 'Test',
  password: 'pass'
})
```

---

## ✅ Test Checklist

- [ ] Backend running on :8080
- [ ] Frontend running on :5173
- [ ] Health check returns 200
- [ ] Parse OCR returns data
- [ ] Validate returns confidence
- [ ] Frontend can parse QR
- [ ] Frontend can parse OCR text
- [ ] Networks save to history
- [ ] Settings shows backend status

---

## 🐛 Troubleshooting

| Issue | Solution |
|-------|----------|
| 404 Not Found | Check endpoint path, restart server |
| CORS error | Add URL to `ALLOWED_ORIGINS` in .env |
| No data returned | Check OCR text format, try sample data |
| Low confidence | Check SSID ≤32 chars, password 8-63 chars |
| Can't connect | Verify API base URL in Settings |

---

## 📊 Response Codes

- `200 OK` - Success
- `400 Bad Request` - Invalid input
- `404 Not Found` - Endpoint missing
- `500 Error` - Server error

---

## ⚙️ Configuration

**Backend `.env`:**
```
PORT=8080
ALLOWED_ORIGINS=http://localhost:5173
```

**Frontend `.env`:**
```
VITE_API_BASE_URL=http://localhost:8080
```

---

## 🚀 Deployment

**Backend:**
```bash
cd server
npm install --production
NODE_ENV=production npm start
```

**Frontend:**
```bash
cd web
npm run build
# Upload dist/ to hosting (Vercel, Netlify, Firebase, etc.)
```

---

## 📞 Resources

- Full Guide: [API_GUIDE.md](../docs/API_GUIDE.md)
- API Spec: [api-contract.md](../docs/api-contract.md)
- Test Data: [DUMMY_DATA.md](../docs/DUMMY_DATA.md)
- Testing: [TESTING_GUIDE.md](../docs/TESTING_GUIDE.md)
- Project: [architecture.md](../docs/architecture.md)

---

## 💡 Pro Tips

1. Use `npm run dev` for development with hot reload
2. Check DevTools Network tab for API responses
3. Copy sample data from DUMMY_DATA.md for testing
4. Add `?debug` to frontend URL for verbose logging
5. Use `curl -v` to see full HTTP headers
6. Test APIs in browser console before frontend
7. Check `npm test` in server for unit tests
8. Use mock responses in api.js for offline development

---

## 📝 API Endpoints Summary

```
GET  /health                    → Check server status
POST /api/v1/ocr/parse         → Parse OCR text & QR codes
POST /api/ai/validate          → Validate & score Wi-Fi data
```

**All endpoints return JSON with `ok` and `data` fields**

---

**For detailed information, see the full documentation files!**
