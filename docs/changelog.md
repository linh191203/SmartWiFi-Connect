# Architecture - SmartWiFi-Connect

## Kiến trúc tổng thể
- UI Layer
- Domain Layer
- Data Layer

## Package structure
com.smartwificonnect
- ui
- ui.theme
- navigation
- feature.home
- feature.scanqr
- feature.scanimage
- feature.review
- feature.history
- feature.settings
- data
- domain
- core

## Vai trò từng package
### ui
Component dùng chung:
- button
- card
- loading
- empty state
- error state

### ui.theme
- color
- typography
- theme
- shape

### navigation
- routes
- nav graph
- app nav host

### feature.home
- HomeScreen
- HomeUiState
- HomePreviewData

### feature.scanqr
- QrScannerScreen
- QrScannerUiState

### feature.scanimage
- ImageScanScreen
- ImageScanUiState

### feature.review
- ReviewScreen
- ReviewUiState

### feature.history
- HistoryScreen
- HistoryDetailScreen

### feature.settings
- SettingsScreen

### data
- repository impl
- local db
- dao
- entity

### domain
- repository interface
- model
- use case
- parser

### core
- constants
- utils
- extensions

## Luồng dữ liệu cơ bản
User action
→ Screen
→ ViewModel
→ UseCase
→ Repository
→ Data source
→ Result
→ UI state

## Quy tắc tách file
- Mỗi màn hình 1 file chính
- Không để business logic trong composable
- Không để ViewModel gọi UI component
- Không để parser nằm trong UI layer

## 2026-03-27
- Thêm Splash frame 1 (SmartWiFi-Connect) trong `ui/SplashActivity.kt` và `activity_splash.xml`
- Thêm `Theme.SmartWiFiConnect.Splash` + resource animation/drawable cho Splash
- Chuyển `MainActivity` sang Compose host: `SmartWifiAppTheme` + `AppNavHost`
- Tạo skeleton theo docs:
  - `ui/theme/Color.kt`, `Type.kt`, `Theme.kt`
  - `navigation/Routes.kt`, `navigation/AppNavHost.kt`
  - `feature/home/HomeUiState.kt`, `HomePreviewData.kt`, `HomeScreen.kt`
- Build `:app:assembleDebug` thành công

## 2026-03-28
- Tạo `OnboardingScreen` bằng Compose theo thiết kế screen 2
- Tạo `OnboardingUiState` + `OnboardingPreviewData`
- Cập nhật navigation route: thêm `ONBOARDING`, start destination từ onboarding
- Nối luồng đúng docs: Splash -> Onboarding -> Home
- Thêm chuỗi onboarding vào `strings.xml`
- Build + install debug thành công
- Chuyển phần minh họa onboarding sang đúng style HTML design:
  - top visual area
  - floating icon góc phải
  - headline, dots, CTA, footer
- Sửa viền ngoài onboarding về trắng theo feedback UI
- Bỏ chữ `Bỏ qua` góc phải theo feedback UI
- Tối ưu loading bar logic splash để animation mượt và không báo đỏ trong IDE
- **[BE]** Init unit test backend với `jest` + `supertest`
- **[BE]** `GET /api/health` → 200 OK, 2 test cases passed ✅
- **[BE]** Export `app` từ `index.js` để hỗ trợ test (tách `app.listen`)

## 2026-04-01
- Tạo màn `LoginScreen` bằng Jetpack Compose:
  - file mới: `feature/home/LoginScreen.kt`
  - state model: `feature/home/LoginUiState.kt`
- Nối điều hướng:
  - thêm `Routes.LOGIN`
  - `Onboarding -> Login`
  - `Login -> Home`
- Thêm chuỗi cho Login vào `app/src/main/res/values/strings.xml`
- Thêm drawable icon cho UI login:
  - `ic_login_arrow_back.xml`
  - `ic_login_mail.xml`
  - `ic_login_lock.xml`
  - `ic_login_visibility.xml`
  - `ic_login_discord.xml`
- Cập nhật docs:
  - `docs/ui-implementation.md`
  - `docs/ui-flow.md`
  - `docs/tasks.md`
  - `docs/memory.md`
- Verify:
  - `:app:assembleDebug` ✅
  - `:app:installDebug` ✅

## 2026-04-01 (update)
- Sửa lại toàn bộ UI `LoginScreen` để khớp HTML design:
  - top app bar, logo section, form card, soft input, CTA gradient, divider, social row
  - background decorative elements theo style HTML
- Đồng bộ font chữ màn login về Inter:
  - thêm `app/src/main/res/font/inter.xml`
  - áp dụng `FontFamily(Font(R.font.inter))` cho text trong màn login
- Verify lại:
  - `:app:assembleDebug` ✅
  - `:app:installDebug` ✅

## 2026-04-01 (fix login navigation/runtime)
- Xử lý rủi ro crash khi mở Login do font provider:
  - bỏ `res/font/inter.xml` (downloadable font provider)
  - thêm font local `res/font/inter_regular.ttf` (download từ `fonts.gstatic`)
  - cập nhật `LoginScreen` dùng `FontFamily(Font(R.font.inter_regular))`
- Verify:
  - `:app:assembleDebug` ✅
  - `adb devices -l` hiện không có thiết bị/emulator kết nối

## 2026-04-03
- Cập nhật UI polish theo feedback:
  - bỏ icon góc phải trong Onboarding
  - bỏ back button trong Login top bar
  - chỉnh title app tránh xuống dòng
- Nâng cấp social login icon trong Login:
  - thêm drawable mới: `ic_social_google.xml`, `ic_social_discord_mark.xml`, `ic_social_apple_mark.xml`, `ic_social_apple_black.xml`
  - chỉnh kích thước icon Google/Discord/Apple cho cân đối
- Cập nhật typography rounded style:
  - thêm local fonts: `nunito_regular.ttf`, `nunito_semibold.ttf`, `nunito_bold.ttf`, `nunito_extrabold.ttf`
  - cập nhật `ui/theme/Type.kt`, `ui/theme/Theme.kt`
- Verify:
  - `:app:assembleDebug` ✅

## 2026-04-04
- Tạo màn Register theo design:
  - file mới: `feature/home/RegisterUiState.kt`
  - file mới: `feature/home/RegisterScreen.kt`
- Cập nhật navigation:
  - thêm `Routes.REGISTER`
  - Login `Đăng ký ngay` -> Register
  - Register `Đăng nhập ngay`/back -> Login
- Thêm strings cho Register trong `app/src/main/res/values/strings.xml`
- Thêm drawable form icon:
  - `ic_login_person.xml`
  - `ic_login_lock_reset.xml`
- Verify:
  - `:app:assembleDebug` ✅

## 2026-04-04 (home redesign)
- Refactor `feature/home/HomeScreen.kt` để bám sát bản HTML/mock tuần 2:
  - top bar mềm, welcome block, hero card `Kết nối nhanh`
  - card `Quét bằng Máy ảnh`
  - 2 shortcut card `Quét mã QR` / `Quét ảnh`
  - section `Mạng gần đây` với item card bo tròn
  - stats card `Đã lưu` / `Tổng dữ liệu`
  - bottom navigation theo style mềm giống iOS mới
- Mở rộng `HomeUiState.kt` + `HomePreviewData.kt` để hỗ trợ dữ liệu UI mới
- Thêm dependency `material-icons-extended` cho icon Compose
- Verify:
  - `:app:compileDebugKotlin` ✅

## 2026-04-07
- Tạo flow OCR mock cho FE:
  - `scan image` hoặc `image picker` sẽ điều hướng sang màn `OCR Result`
  - giả lập loading OCR trước khi trả text mẫu
  - cho phép chỉnh sửa `ocrText` và parse mock local ra dữ liệu Wi-Fi
- Thêm màn mới `feature/scanimage/OcrResultScreen.kt`
- Mở rộng `MainViewModel` với state `security`, `scanSource` và event mock OCR/parse
- Cập nhật `Routes.kt`, `AppNavHost.kt`, `MainActivity.kt` để dùng chung `MainViewModel`
- Verify:
  - `:app:compileDebugKotlin` ✅

## 2026-04-07 (production OCR)
- Chuyển `MainViewModel` sang OCR thật bằng `WifiOcrProcessor`:
  - thêm `startOcrFromGallery(uri)` và `startOcrFromCamera(bitmap)`
  - decode ảnh từ `Uri` bằng `ImageDecoder`
  - giải phóng OCR recognizer trong `onCleared`
- Cập nhật `AppNavHost`:
  - dùng `ActivityResultContracts.GetContent` để lấy ảnh thật từ gallery
  - dùng `ActivityResultContracts.TakePicturePreview` để chụp ảnh thật
  - điều hướng sang `OCR_RESULT` sau khi nhận ảnh
- Cập nhật `OcrResultScreen`:
  - đổi action từ `parse mock` sang parse server
  - giữ khả năng edit OCR text trước khi parse BE
- Verify:
  - `:app:compileDebugKotlin` ✅

## 2026-04-07 (camera permission crash fix)
- Điều tra crash thực tế bằng `adb logcat` và xác định nguyên nhân:
  - `SecurityException`: mở `IMAGE_CAPTURE` khi chưa có quyền `android.permission.CAMERA`
- Cập nhật `feature/permission/CameraPermissionScreen.kt`:
  - xin quyền camera runtime bằng `ActivityResultContracts.RequestPermission`
  - chỉ điều hướng tiếp khi quyền đã được cấp
- Cập nhật `navigation/AppNavHost.kt`:
  - kiểm tra `ContextCompat.checkSelfPermission` trước khi gọi camera launcher
  - nếu chưa có quyền thì điều hướng về luồng permission
- Verify:
  - `:app:compileDebugKotlin` ✅
  - `:app:installDebug` ✅
  - launch app trên emulator thành công ✅

## 2026-04-08 (Mock API integration test FE)
- Thêm dependency test:
  - `androidTestImplementation(libs.okhttp.mockwebserver)`
- Thêm bộ test mới:
  - `app/src/androidTest/java/com/example/smartwificonnect/MainViewModelMockApiIntegrationTest.kt`
- Nội dung test bao phủ flow FE `parseCurrentText()` với mock API:
  - success response: cập nhật đúng `ssid/password/security/confidence`
  - business error response: hiển thị đúng thông báo backend
  - network error: hiển thị đúng lỗi parse
- Verify:
  - `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.smartwificonnect.MainViewModelMockApiIntegrationTest` ✅
  - Kết quả: `3 tests passed`

## 2026-04-10 (Fuzzy SSID Match UI)
- Tạo component mới `SsidSuggestionCard.kt`:
  - 3 trạng thái: Loading (shimmer), Found (gradient header + OCR/match chip + confidence bar), NotFound (gentle message)
  - Danh sách mạng gần đây có thể expand/collapse, tap chọn mạng
  - `@Preview` cho cả 3 state
- Mở rộng `MainUiState`:
  - thêm `SsidSuggestionState` sealed class (Hidden/Loading/Found/NotFound)
  - thêm `NearbyNetwork` data class
  - thêm fields: `ssidSuggestion`, `nearbyNetworks`, `isNearbyExpanded`
- Mở rộng `MainViewModel`:
  - `triggerFuzzyMatch()` — mock fuzzy match bằng Levenshtein similarity (placeholder cho BE Fuse.js)
  - `acceptSsidSuggestion()` — cập nhật SSID khi user chấp nhận gợi ý
  - `dismissSsidSuggestion()` — ẩn suggestion card
  - `toggleNearbyExpanded()` — expand/collapse danh sách mạng
  - `selectNearbyNetwork(ssid)` — chọn mạng từ danh sách
  - Tự động trigger fuzzy match sau khi parse thành công
- Cập nhật `OcrResultScreen`:
  - thêm `SsidSuggestionCard` bên dưới `ParsedWifiCard`
  - thêm 4 callback mới: accept, dismiss, toggle, select
- Cập nhật `AppNavHost`:
  - truyền 4 callback mới cho `OcrResultScreen`
- Verify:
  - `:app:compileDebugKotlin` ✅
