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
- Đã có W3 FE flow: AI validate, fuzzy SSID API/fallback, OCR/AI choices UI, lưu SQLite history
- Đã có CameraX preview thật cho QR và OCR capture, QR scan bằng ML Kit Barcode
- Đã có HistoryScreen đọc dữ liệu SQLite thật
- OCR Result đã ưu tiên hiển thị Wi-Fi xung quanh từ Android scanResults nếu đủ quyền
- Đã có `NetworkDetailScreen` mở từ Home/History
- Màn chi tiết mạng đã có trạng thái live cho mạng đang kết nối:
  - RSSI / dBm
  - frequency
  - link speed / RX / TX
- Đã có xóa 1 mạng khỏi SQLite history từ màn chi tiết

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
- Link speed realtime lấy từ `WifiManager.connectionInfo`, không phải đo throughput internet thật

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

### 2026-04-21 (W3 FE - AI giả lập + lưu lịch sử + ổn định)
- Đã nối FE gọi `POST /api/ai/validate` sau khi parse OCR thành công
- Đã mở rộng model/API/repository cho AI validate và fuzzy SSID match
- Đã lưu thêm metadata AI/fuzzy vào SQLite history:
  - AI confidence, suggestion, recommendation, flags
  - fuzzy best match, fuzzy score
- Đã cập nhật `OcrResultScreen`:
  - hiển thị OCR text editor
  - hiển thị parsed Wi-Fi result
  - hiển thị AI validation choices
  - cho phép dùng SSID/password AI normalize
- Đã cập nhật `SsidSuggestionCard`:
  - gọi fuzzy API khi có BE
  - fallback local nếu BE fuzzy chưa sẵn sàng
  - danh sách Wi-Fi xung quanh expand/collapse
  - đổi indicator sóng sang vòng cung Wi-Fi
- Đã thêm `CameraPreview.kt` dùng CameraX cho QR scanner và OCR capture
- Đã cập nhật QR scanner:
  - camera preview thật
  - ML Kit Barcode Scanning để tự nhận QR
  - route sang `OCR Result` khi nhận raw QR text
- Đã cập nhật camera permission:
  - nếu đã cấp quyền thì không hỏi lại
  - nếu chưa cấp mới đi qua `CameraPermissionScreen`
- Đã tạo `HistoryScreen.kt`:
  - UI theo mock lịch sử kết nối
  - bottom nav fixed
  - đọc danh sách record từ SQLite thật
- Đã thêm animation vạch scan trắng chạy lên/xuống trong khung QR và khung chụp ảnh
- Đã bổ sung quyền Android cho Wi-Fi scan:
  - `ACCESS_WIFI_STATE`
  - `CHANGE_WIFI_STATE`
  - `ACCESS_FINE_LOCATION`
  - `ACCESS_COARSE_LOCATION`
  - `NEARBY_WIFI_DEVICES`
- Không làm phần "BE mock nâng cao" để tránh conflict với nhánh BE
- Verify:
  - `:app:compileDebugKotlin` thành công
  - `:app:compileDebugAndroidTestKotlin` thành công
  - `MainViewModelMockApiIntegrationTest` với MockWebServer thành công

### 2026-04-22 (Wi-Fi connect thật - FE)
- Đã thêm `WifiConnector` dùng Android Wi-Fi API (`WifiNetworkSpecifier` + `ConnectivityManager.requestNetwork`)
- Đã nối flow connect vào `MainViewModel.connectToParsedWifi()`
- Đã thêm `WifiConnectionState` trong `MainUiState`:
  - `Idle`
  - `Connecting`
  - `Connected`
  - `Failed`
- Đã cập nhật `OcrResultScreen`:
  - thêm nút `Ket noi Wi-Fi that`
  - hiển thị trạng thái loading/success/error cơ bản ngay trong `ParsedWifiCard`
- Đã nối permission flow ở `AppNavHost`:
  - khi user bấm connect sẽ check quyền Wi-Fi/location trước
  - nếu chưa có quyền thì xin quyền, nếu user từ chối thì hiện lỗi rõ ràng
- Đã thêm quyền `CHANGE_NETWORK_STATE` vào manifest để ổn định flow request network
- Verify:
  - `:app:compileDebugKotlin` thành công

### 2026-04-22 (save network API sau khi connect thành công)
- Đã nối FE gọi `POST /api/networks` sau khi kết nối Wi-Fi thành công:
  - thêm `SaveNetworkRequest`
  - thêm `WifiApiService.saveNetwork(...)`
  - thêm `WifiRepository.saveConnectedNetwork(...)`
  - `MainViewModel` gọi save API ở nhánh `WifiConnectResult.Success`
- Cơ chế best-effort:
  - nếu BE chưa có endpoint hoặc server lỗi thì vẫn giữ trạng thái `Connected`
  - chỉ đổi `statusMessage` để báo "chưa lưu được lên server"
- Build verify:
  - `:app:compileDebugKotlin` thành công

### 2026-04-22 (local history sau connect thành công)
- Đã bổ sung lưu local SQLite ngay khi kết nối Wi-Fi thành công:
  - thêm `WifiRepository.saveConnectedNetworkLocal(...)`
  - implement `DefaultWifiRepository.saveConnectedNetworkLocal(...)`
  - `MainViewModel.connectToParsedWifi()` gọi save local trong nhánh success
- Đã cập nhật `historyRecords` tức thời sau khi save local để màn `History` thấy dữ liệu ngay
- Luồng tổng hiện tại:
  - connect success -> save local (SQLite) -> best-effort save server
- Build verify:
  - `:app:compileDebugKotlin` thành công

### 2026-04-22 (gợi ý Wi-Fi realtime, bỏ minh họa)
- Đã bỏ fallback `fallbackNearbyNetworks` trong `MainViewModel`.
- Luồng nearby/fuzzy hiện dùng:
  - scanResults realtime từ Android
  - hoặc danh sách scan gần nhất đã có trong state (khi refresh fail tạm thời)
- Không còn tự bơm danh sách Wi-Fi minh họa khi không scan được.
- Build verify:
  - `:app:compileDebugKotlin` thành công

### 2026-04-25 (Settings + dark mode toàn app)
- Đã tạo `SettingsScreen` theo mock mới:
  - top bar `Cài đặt`
  - profile card
  - các nhóm `KẾT NỐI`, `HỆ THỐNG & QUYỀN RIÊNG TƯ`, `GIỚI THIỆU`
  - bottom nav fixed, tab `Cài đặt` active
- Đã nối route `Routes.SETTINGS` thật trong `AppNavHost`, bỏ placeholder text.
- Đã thêm dark mode ở cấp app:
  - `MainUiState.isDarkModeEnabled`
  - `MainViewModel.onDarkModeChanged(...)`
  - `SmartWifiAppTheme(darkTheme = ...)` trong `MainActivity`
  - `LocalAppDarkMode` để các màn đọc trạng thái theme hiện tại
- Đã cập nhật palette sáng/tối cho các màn đang có trong luồng chính:
  - Onboarding / Login / Register
  - Home / History / Settings
  - QR Scan / Image Scan / Image Picker / OCR Result
  - Manual Entry / Camera Permission / Connection Failed
- Build `:app:compileDebugKotlin` thành công

### 2026-04-27 (avatar top bar polish)
- Đã chỉnh lại avatar góc phải cho cân thị giác hơn:
  - `SettingsScreen` avatar top bar hạ nhẹ xuống
  - `HomeScreen` avatar góc phải hạ xuống rõ hơn để đồng bộ
- Ghi nhớ:
  - nếu tiếp tục polish UI, nên rà các top bar còn lại để thống nhất baseline icon/avatar giữa các màn
- Build `:app:compileDebugKotlin` thành công

### 2026-04-27 (Share Wi-Fi screen)
- Đã tạo `ShareWifiScreen` cho luồng chia sẻ mạng sang thiết bị ở gần.
- Đã thêm route `Routes.SHARE` và nối toàn bộ tab/nút `Chia sẻ` sang route mới.
- Đã sửa lệch hành vi ở `HomeScreen`:
  - tab `Chia sẻ` trước đó đang map nhầm sang `ManualEntry`
  - hiện đã điều hướng đúng sang `ShareWifiScreen`
- Màn chia sẻ hiện hoạt động theo trạng thái FE:
  - nếu có SSID đang kết nối hoặc SSID gần nhất đã lưu thì hiện UI tìm thiết bị
  - nếu chưa có mạng để chia sẻ thì hiện empty state hướng dẫn kết nối trước
- Build `:app:compileDebugKotlin` thành công

### 2026-04-28 (Network detail screen)
- Đã thêm `NetworkDetailScreen` cho luồng xem chi tiết 1 mạng Wi‑Fi.
- Điểm vào hiện tại:
  - tap 1 mạng ở `HomeScreen`
  - tap 1 mạng ở `HistoryScreen`
- `Home` giờ ưu tiên dữ liệu recent network từ `historyRecords` thật, fallback về preview nếu chưa có lịch sử.
- `MainViewModel` giữ:
  - `selectedNetworkDetail`
  - `selectedNetworkTelemetry`
- Nếu SSID đang được kết nối trên máy:
  - màn detail refresh `RSSI`, `frequency`, `link speed`, `RX`, `TX` khoảng mỗi 1.5 giây
- Nếu chỉ là mạng đã lưu:
  - detail vẫn hiển thị quality + usage chart theo hướng UI landing/product
- Đã thêm khả năng xóa 1 mạng khỏi SQLite history từ detail screen.
- Ghi nhớ:
  - phần Mbps hiện là link speed do Android trả về, chưa phải đo throughput internet thực tế
- Build `:app:compileDebugKotlin` thành công
