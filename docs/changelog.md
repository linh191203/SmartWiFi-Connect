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

## 2026-04-21 (W3 FE - AI giả lập + lưu lịch sử + ổn định)
- Cập nhật API/data layer:
  - thêm model `AiValidateRequest`, `AiValidateData`
  - thêm model fuzzy SSID request/response
  - thêm repository methods `validateAi()` và `fuzzyMatchSsid()`
  - gọi `POST /api/ai/validate` từ FE sau khi parse OCR thành công
  - gọi fuzzy SSID endpoint khi có danh sách mạng xung quanh
- Cập nhật local persistence:
  - mở rộng `WifiHistoryDbHelper` để lưu AI/fuzzy metadata
  - thêm `getAll()` để đọc danh sách history
  - thêm `getSavedWifiHistory()` trong repository
  - cập nhật `MainUiState.historyRecords`
- Cập nhật `MainViewModel`:
  - thêm state `AiValidationState`
  - resolve AI validation và fuzzy suggestion sau parse
  - fallback fuzzy local nếu BE chưa sẵn sàng
  - refresh history sau khi save SQLite
  - refresh Wi-Fi xung quanh từ Android `WifiManager.scanResults`
- Cập nhật OCR Result UI:
  - hiển thị AI validation card với confidence/suggestion/recommendation
  - thêm action dùng SSID/password normalized từ AI
  - hiển thị danh sách Wi-Fi xung quanh
  - đổi signal indicator từ cột mobile sang vòng cung Wi-Fi bằng Canvas
- Cập nhật camera/scan:
  - thêm `feature/camera/CameraPreview.kt` dùng CameraX
  - `ImageScanScreen` dùng embedded CameraX preview để chụp OCR
  - `QrScannerScreen` dùng embedded CameraX preview và ML Kit Barcode Scanning
  - QR detect xong điều hướng sang OCR Result với raw text
  - permission camera chỉ hỏi khi chưa cấp
- Cập nhật UI:
  - thêm animation vạch scan trắng chạy lên/xuống trong khung QR
  - thêm animation vạch scan trắng chạy lên/xuống trong khung chụp ảnh OCR
  - tạo `feature/history/HistoryScreen.kt` theo mock lịch sử kết nối
  - route `Routes.HISTORY` không còn placeholder
- Cập nhật Android permission:
  - thêm quyền Wi-Fi/location để đọc Wi-Fi xung quanh khi thiết bị cho phép
- Cập nhật test:
  - mở rộng `MainViewModelMockApiIntegrationTest` với parse + AI validate + fuzzy match
- Chủ động không làm:
  - không bổ sung BE mock nâng cao trong FE branch để tránh conflict với code BE
- Verify:
  - `:app:compileDebugKotlin` ✅
  - `:app:compileDebugAndroidTestKotlin` ✅
  - `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.smartwificonnect.MainViewModelMockApiIntegrationTest` ✅

## 2026-04-22 (FE - Kết nối Wi-Fi thật phần cơ bản)
- Thêm file mới `app/src/main/java/com/example/smartwificonnect/wifi/WifiConnector.kt`
  - dùng `WifiNetworkSpecifier` + `ConnectivityManager.requestNetwork`
  - trả về `WifiConnectResult` (Success/Failed)
  - chuẩn hóa failure reason: permission/input/auth_or_unavailable/timeout/unknown
- Cập nhật `MainViewModel`:
  - thêm `wifiConnector`
  - thêm `connectToParsedWifi()`
  - thêm `onWifiConnectionPermissionDenied()`
  - thêm state `WifiConnectionState` trong `MainUiState`
  - reset connection state khi dữ liệu SSID/password thay đổi
- Cập nhật `OcrResultScreen`:
  - `ParsedWifiCard` có nút `Ket noi Wi-Fi that`
  - hiển thị trạng thái kết nối `Connecting/Connected/Failed`
- Cập nhật `AppNavHost`:
  - thêm launcher xin quyền trước khi gọi connect Wi-Fi
  - nối callback `onConnectWifi` từ UI xuống ViewModel
- Cập nhật manifest:
  - thêm quyền `android.permission.CHANGE_NETWORK_STATE`
- Verify:
  - `:app:compileDebugKotlin` ✅

## 2026-04-22 (FE - Save network API sau connect)
- Cập nhật data layer để hỗ trợ save network backend:
  - thêm model `SaveNetworkRequest`
  - thêm API `POST /api/networks` trong `WifiApiService`
  - thêm repository method `saveConnectedNetwork(...)`
- Cập nhật `MainViewModel.connectToParsedWifi()`:
  - khi `WifiConnectResult.Success` thì gọi API save network ngay sau connect
  - xử lý theo kiểu best-effort: lỗi API không làm fail trạng thái kết nối
  - cập nhật `statusMessage` theo kết quả save server
- Verify:
  - `:app:compileDebugKotlin` ✅

## 2026-04-22 (FE - Lưu local history sau connect thành công)
- Cập nhật repository:
  - thêm method `saveConnectedNetworkLocal(...)` trong `WifiRepository`
  - implement trong `DefaultWifiRepository` để ghi SQLite trực tiếp
- Cập nhật `MainViewModel.connectToParsedWifi()`:
  - khi connect thành công sẽ lưu local history trước (SQLite)
  - cập nhật `historyRecords` ngay để `HistoryScreen` phản ánh realtime
  - giữ API save server ở chế độ best-effort như cũ
- Verify:
  - `:app:compileDebugKotlin` ✅

## 2026-04-22 (FE - Gợi ý Wi-Fi chỉ dùng dữ liệu scan thật)
- Bỏ fallback mạng minh họa trong luồng gợi ý SSID/nearby Wi-Fi.
- `refreshNearbyWifiNetworks()`:
  - ưu tiên danh sách scan mới nhất từ thiết bị
  - nếu scan tạm thời không cập nhật được thì giữ danh sách scan gần nhất, không bơm dữ liệu giả
- `getAvailableNearbyNetworks()`:
  - chỉ trả về dữ liệu scan thật hoặc dữ liệu scan trước đó trong state
- Verify:
  - `:app:compileDebugKotlin` ✅

## 2026-04-25 (FE - SettingsScreen + dark mode app-wide)
- Tạo `feature/settings/SettingsScreen.kt` theo mock:
  - top bar, profile card, section `KẾT NỐI`
  - section `HỆ THỐNG & QUYỀN RIÊNG TƯ`
  - section `GIỚI THIỆU`
  - bottom nav fixed với tab `Cài đặt` active
- Cập nhật `AppNavHost`:
  - route `Routes.SETTINGS` không còn placeholder text
  - mọi nút/tab `Cài đặt` điều hướng sang `SettingsScreen`
- Thêm dark mode app-wide:
  - thêm `isDarkModeEnabled` trong `MainUiState`
  - thêm `MainViewModel.onDarkModeChanged(...)`
  - `MainActivity` bọc app bằng `SmartWifiAppTheme(darkTheme = mainState.isDarkModeEnabled)`
  - thêm `LocalAppDarkMode` trong `ui/theme/Theme.kt`
- Mở rộng palette sáng/tối cho các màn chính:
  - `HomeScreen`
  - `HistoryScreen`
  - `ManualEntryScreen`
  - `ConnectionFailedScreen`
  - `CameraPermissionScreen`
  - `QrScannerScreen`
  - `ImageScanScreen`
  - `ImagePickerScreen`
  - `OcrResultScreen`
  - `LoginScreen`
  - `RegisterScreen`
  - `OnboardingScreen`
- Verify:
  - `:app:compileDebugKotlin` ✅

## 2026-04-27 (FE - UI polish top bar avatar)
- Tinh chỉnh trục nhìn avatar góc phải:
  - `SettingsScreen`: hạ avatar top bar xuống nhẹ để cân thị giác
  - `HomeScreen`: hạ avatar góc phải xuống rõ hơn để đồng bộ với màn Cài đặt
- Mục tiêu:
  - cân hàng với title/menu bên trái
  - bớt cảm giác avatar bị nhô cao trong top bar
- Verify:
  - `:app:compileDebugKotlin` ✅

## 2026-04-27 (FE - Share Wi-Fi screen)
- Tạo `feature/share/ShareWifiScreen.kt` theo mock `Chia sẻ Wi‑Fi`:
  - top bar back + title
  - radar tìm thiết bị ở gần
  - headline/trạng thái chia sẻ
  - device cards với CTA `Chia sẻ` / `Chấp nhận`
  - bottom nav fixed với tab `Chia sẻ` active
- Thêm route `Routes.SHARE`.
- Cập nhật `AppNavHost`:
  - mọi `onShareClick` trong Home/History/Settings/QR/ImageScan/ConnectionFailed điều hướng sang `Routes.SHARE`
  - `Routes.SHARE` nhận dữ liệu SSID hiện tại hoặc SSID gần nhất đã lưu để quyết định có thể chia sẻ hay không
- Cập nhật `HomeScreen`:
  - thêm callback `onShareClick`
  - tab `Chia sẻ` không còn đi nhầm sang `ManualEntry`
- Cập nhật docs:
  - `docs/ui-flow.md`
- Verify:
  - `:app:compileDebugKotlin` ✅

## 2026-04-28 (FE - Network detail screen)
- Thêm route `Routes.NETWORK_DETAIL` và màn mới `feature/networkdetail/NetworkDetailScreen.kt`
- Nối click từ `HomeScreen` và `HistoryScreen` sang màn chi tiết mạng
- `Home` giờ ưu tiên lấy danh sách mạng gần đây từ `historyRecords` thật; fallback về preview data nếu chưa có lịch sử
- `MainViewModel` thêm selected detail state + live telemetry:
  - `selectedNetworkDetail`
  - `selectedNetworkTelemetry`
  - mở chi tiết từ Home/History
  - refresh telemetry bằng `WifiManager.connectionInfo`
- Màn chi tiết mạng hiển thị:
  - giao thức
  - tần số
  - đánh giá sóng tốt/yếu + dBm
  - link speed / RX / TX realtime nếu mạng đang kết nối
  - usage chart mock ổn định theo SSID để giữ cảm giác landing/product
  - CTA `Kết nối ngay` + `Xóa mạng này`
- Thêm xóa lịch sử mạng trong SQLite:
  - `WifiHistoryDbHelper.deleteById`
  - `WifiRepository.deleteSavedWifiRecord`
- Verify:
  - `:app:compileDebugKotlin` ✅
