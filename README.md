# SmartWiFi-Connect

🎉 **Đã hỗ trợ Web, Android, iPhone!**

Web app hỗ trợ kết nối Wi-Fi thông minh bằng:
- Quét mã QR Wi-Fi
- Quét ảnh chứa tên Wi-Fi và mật khẩu bằng OCR
- Nhập tay thông tin Wi-Fi
- Xác nhận kết quả trước khi kết nối
- Lưu lịch sử

## 📱 Mobile Support (NEW!)

✅ **PWA** - Works on Android, iPhone, Web (instant, no installation)
✅ **Android APK** - Native app via Capacitor + Gradle
✅ **iOS IPA** - Native app via Capacitor + Xcode
✅ **Responsive Design** - Works on all screen sizes (360px - 2560px)
✅ **Offline Support** - Service Worker caching

### Quick Deploy
```bash
npm run build              # Build web app
# Upload dist/ to Vercel, Netlify, or Firebase
# Users open link → "Add to Home Screen" → Acts like native app
```

### For Android APK
See: [docs/MOBILE_SETUP.md](docs/MOBILE_SETUP.md)

Quick one-time setup (Windows):
```powershell
setx JAVA_HOME "C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot"
setx ANDROID_HOME "C:\Users\<YourUser>\AppData\Local\Android\Sdk"
setx ANDROID_SDK_ROOT "C:\Users\<YourUser>\AppData\Local\Android\Sdk"
```
Then open a new terminal and run:
```bash
npm run build:android
cd web/android
./gradlew.bat assembleDebug
```

### For iPhone App
See: [docs/DEPLOY_GUIDE.md](docs/DEPLOY_GUIDE.md)

---

## Công nghệ
- Web (thư mục `web/`):
	- React
	- Vite
	- React Router
	- localStorage cho lịch sử đã lưu
	- **PWA (offline support)**
	- **Capacitor (Android/iOS)**
- Backend (thư mục `server/`):
	- Node.js
	- Express

## 📚 Cấu trúc tài liệu
Xem thêm trong thư mục `docs/`:
- `docs/QUICK_REFERENCE.md` - Quick lookup
- `docs/API_GUIDE.md` - 📌 **START HERE** - Complete API implementation guide
- `docs/NETWORK_SAVING.md` - How networks are saved and managed
- `docs/USER_GUIDE_SAVE_NETWORKS.md` - User guide for saving networks
- `docs/api-contract.md` - API specification for all endpoints
- `docs/DUMMY_DATA.md` - Test data and examples
- `docs/TESTING_GUIDE.md` - Testing procedures and debugging
- `docs/product.md` - Product requirements
- `docs/architecture.md` - System architecture
- `docs/ui-flow.md`
- `docs/tasks.md`
- `docs/memory.md`

## Chạy app

### 1. Chạy backend

```bash
cd server
npm install
npm run dev
```

Mac dinh backend chay o `http://localhost:8080`.

Neu dung CORS whitelist, hay them `http://localhost:5173` vao `ALLOWED_ORIGINS`.

### 2. Chạy frontend

```bash
cd web
npm install
npm run dev
```

Frontend mặc định chạy ở `http://localhost:5173`.

### 3. Build production

```bash
cd web
npm run build
```