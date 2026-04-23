# Memory - SmartWiFi-Connect

## Trạng thái hiện tại
- Đã khởi tạo project Android
- Đã tạo package structure
- Đã tạo bộ docs `.md`
- Đang chuẩn hóa package `com.smartwificonnect`
- Đã có AppTheme + Navigation skeleton + HomeScreen
- Đã có SplashScreen (Compose) + OnboardingScreen (Compose)
- Đã có LoginScreen (Compose)
- Đã có RegisterScreen (Compose)
- Đã redesign HomeScreen theo style mềm/bo tròn, bám bản HTML W2
- Luồng hiện tại chạy: Splash -> Onboarding -> (Login hoặc Home) -> Register -> Home
- Đã có OCR flow thực tế: Gallery/Camera -> ML Kit OCR -> OCR Result -> Parse qua BE
- Đã fix crash runtime permission camera khi bấm chụp ở OCR flow
- Đã có bộ Mock API integration test cho FE parse OCR

---

## Đã chốt
- Stack:
    - Kotlin
    - Jetpack Compose
    - Material 3
    - CameraX
    - ML Kit
    - Room
    - Navigation Compose
- Flow chính:
  Splash → Onboarding → Home → (QR / OCR / Manual) → Review → Save/Connect → History
- QR scanner là màn camera thật, không phải mock trắng đục
- OCR screen là màn riêng
- Review screen là điểm hợp nhất dữ liệu
- Không làm crack / brute force / bypass Wi-Fi

---

## Điều chỉnh từ plan cũ
- Không dùng React Native
- Không dùng AsyncStorage
- Không dùng wifi-reborn làm trung tâm
- Chuyển toàn bộ theo native Android + Room + Wi-Fi API phù hợp

---

## Đang làm dở
- [ ] Sửa package name
- [x] Tạo `ui/components`
- [x] Tạo AppTheme
- [x] Tạo Navigation skeleton
- [x] Tạo HomeScreen đầu tiên
- [x] Tạo OnboardingScreen
- [x] Tạo LoginScreen
- [x] Tạo RegisterScreen

---

## Next step ngay sau khi mở máy
1. Hoàn thiện package `com.smartwificonnect`
2. Tạo `ui/components`
3. Tạo `Theme.kt`, `Color.kt`, `Type.kt`
4. Tạo `Routes.kt`
5. Tạo `AppNavHost.kt`
6. Tạo `HomeScreen.kt`

---

## Rủi ro cần nhớ
- Dễ code lan man nếu chưa khóa từng screen
- Scanner dễ lỗi overlay / background / tràn viền
- OCR cần test với ảnh thật
- Wi-Fi connect phụ thuộc Android version và quyền hệ thống

---

## Quy tắc chống miên man
- Mỗi phiên chỉ làm 1 màn hoặc 1 module
- Không polish animation trước khi xong flow chính
- Không chuyển sang backend khi UI skeleton chưa xong
- Code xong phải cập nhật file này

---

## Nhật ký ngắn
### 2026-03-25
- Đã tạo docs
- Đã tách package cơ bản
- Đang chuẩn hóa tên package và cấu trúc project

### 2026-03-27
- Đã thêm Splash frame 1 (SmartWiFi-Connect) và launcher flow Splash -> Main
- Đã tạo `ui/theme` gồm `Color.kt`, `Type.kt`, `Theme.kt`
- Đã tạo `navigation/Routes.kt` và `navigation/AppNavHost.kt`
- Đã tạo `feature/home/HomeUiState.kt`, `HomePreviewData.kt`, `HomeScreen.kt`
- Build debug thành công
- **[BE]** Khởi tạo backend test: cài `jest` + `supertest`
- **[BE]** Viết và chạy unit test `GET /api/health` → **2 passed** ✅

### 2026-03-28
- Đã tạo screen 2 `Onboarding` bằng Compose theo flow docs
- Đã nối navigation: `Splash -> Onboarding -> Home`
- Đã thêm `OnboardingUiState` + preview data
- Đã đưa text onboarding vào `strings.xml`
- Build + install debug thành công trên emulator
- Đã chỉnh lại Onboarding để bám sát design HTML:
  - viền trắng ngoài card
  - top visual dùng đúng illustration URL từ design
  - bỏ nút `Bỏ qua` theo feedback

### 2026-04-01
- Đã tạo `feature/home/LoginUiState.kt` + `feature/home/LoginScreen.kt` theo design tuần 2 task 2
- Đã thêm route `LOGIN` vào nav graph và nối luồng:
  - Onboarding `Đăng nhập` -> Login
  - Login `Đăng nhập` -> Home
- Đã thêm login strings vào `app/src/main/res/values/strings.xml`
- Đã thêm icon drawable cho login form:
  - `ic_login_arrow_back.xml`
  - `ic_login_mail.xml`
  - `ic_login_lock.xml`
  - `ic_login_visibility.xml`
  - `ic_login_discord.xml`
- Build + install debug thành công trên emulator
- Đã refactor lại `LoginScreen` để bám sát HTML mới:
  - layout top bar + card + input + divider + social theo đúng tỷ lệ
  - background decoration đổi về style bản HTML
  - đồng bộ font text màn login về Inter (`res/font/inter.xml`)

### 2026-04-03
- Đã tinh chỉnh UI theo feedback:
  - bỏ icon xanh góc phải của Onboarding
  - bỏ nút back trong Login top bar
  - chỉnh lại tiêu đề app để không xuống dòng
- Đã nâng chất lượng social icon Login:
  - Google/Discord/Apple icon mới theo design
  - cân lại kích thước icon theo từng feedback
- Đã áp typography rounded style (Nunito local fonts) ở theme-level
- Build `:app:assembleDebug` thành công

### 2026-04-04
- Đã tạo màn Register:
  - `feature/home/RegisterUiState.kt`
  - `feature/home/RegisterScreen.kt`
- Đã thêm route `REGISTER` + nối luồng:
  - `Login -> Register`
  - `Register -> Login/Home`
- Đã thêm string cho Register vào `strings.xml`
- Đã thêm icon cho form Register:
  - `ic_login_person.xml`
  - `ic_login_lock_reset.xml`
- Build `:app:assembleDebug` thành công
- Đã redesign lại `HomeScreen` để gần với mock W2:
  - top bar + welcome + quick connect hero
  - card camera scan
  - shortcut QR / image
  - list mạng gần đây
  - stats card + bottom nav mềm kiểu iOS
- Đã mở rộng `HomeUiState` và `HomePreviewData` để nuôi UI Home mới
- Build `:app:compileDebugKotlin` thành công

### 2026-04-07
- Đã nối OCR mock flow để FE demo trước khi tích hợp ML Kit thật:
  - `ImageScanScreen` chụp giả lập -> `OCR Result`
  - `ImagePickerScreen` chọn ảnh giả lập -> `OCR Result`
  - cho phép edit `ocrText` rồi `Parse mock` ra `SSID`, `Password`, `Security`, `Confidence`
- Đã mở rộng `MainViewModel` để quản lý loading mock OCR + parse mock local
- Build `:app:compileDebugKotlin` thành công

### 2026-04-07 (production OCR)
- Đã chuyển OCR từ mock sang thực tế:
  - mở ảnh thật bằng system picker
  - chụp ảnh thật bằng camera preview contract
  - nhận diện text bằng `WifiOcrProcessor` (ML Kit)
- Đã nối parse thật qua backend từ màn `OCR Result`
- Đã cleanup state để khi sửa text OCR thì parse result cũ tự reset
- Build `:app:compileDebugKotlin` thành công

### 2026-04-07 (stability fix)
- Đã bắt log crash thật trên emulator:
  - `SecurityException` với quyền `android.permission.CAMERA` bị revoke khi mở camera intent
- Đã bổ sung xin quyền runtime camera trong `CameraPermissionScreen`
- Đã thêm kiểm tra quyền trước khi launch camera ở `AppNavHost`
- Đã cài bản debug mới lên emulator và launch lại app thành công

### 2026-04-08 (mock API integration test)
- Đã thêm test instrumentation `MainViewModelMockApiIntegrationTest` dùng `MockWebServer`
- Bao phủ 3 case chính của FE parse OCR:
  - success (200 + data)
  - business error (200 + ok=false)
  - network error (không kết nối được server)
- Đã chạy test trên emulator:
  - 3 tests passed ✅

### 2026-04-10 (Fuzzy SSID Match UI)
- Đã tạo `SsidSuggestionCard.kt` component với 3 state UI:
  - Loading (shimmer + progress)
  - Found (gradient header, OCR chip ⚠️ → match chip ✅, confidence bar, 2 CTA)
  - NotFound (gentle message + nút giữ nguyên)
- Đã thêm danh sách mạng gần đây expand/collapse (tap để chọn SSID)
- Đã mở rộng `MainUiState` với `SsidSuggestionState` sealed class + `NearbyNetwork`
- Đã bổ sung `MainViewModel` mock fuzzy match (Levenshtein similarity)
  - Tự động trigger sau parse thành công
  - Sau này chỉ cần đổi `triggerFuzzyMatch()` sang gọi BE Fuse.js endpoint
- Build `:app:compileDebugKotlin` thành công
