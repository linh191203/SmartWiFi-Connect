# UI Implementation

## SplashScreen
- file: `ui/components/SplashScreen.kt`
- launcher: `ui/SplashActivity.kt`
- visual:
  - gradient background
  - pulse rings + wifi icon
  - animated loading bar
- state:
  - loading animation
  - timeout -> navigate onboarding

## HomeScreen
- file: feature/home/HomeScreen.kt
- component dùng lại:
    - HeroCard
    - QuickActionCard
    - SectionHeader
- state:
    - normal
    - loading
    - empty recent history

## QrScannerScreen
- file: feature/scanqr/QrScannerScreen.kt
- component dùng lại:
    - ScannerTopBar
    - ScannerFrame
    - InfoCard
    - ActionButtonRow
- state:
    - permission denied
    - scanning
    - detected
    - error

## OnboardingScreen
- file: `feature/home/OnboardingScreen.kt`
- state model: `feature/home/OnboardingUiState.kt`
- component chính:
  - top visual card (illustration + floating action icon)
  - headline + subtitle
  - pager dots
  - CTA button + login prompt
  - branding footer
- state:
  - page indicator selected
  - start action
  - login action

## LoginScreen
- file: `feature/home/LoginScreen.kt`
- state model: `feature/home/LoginUiState.kt`
- component chính:
  - top bar title
  - logo + brand subtitle
  - form card (email + password + forgot password)
  - primary CTA button
  - social login row (Google/Discord/Apple icon)
  - sign-up footer text
- state:
  - email input
  - password input
  - toggle hiển thị mật khẩu

## RegisterScreen
- file: `feature/home/RegisterScreen.kt`
- state model: `feature/home/RegisterUiState.kt`
- component chính:
  - top bar (back + title)
  - logo + subtitle
  - form card (họ tên, email, mật khẩu, xác nhận mật khẩu)
  - CTA đăng ký
  - social row
  - footer link quay lại đăng nhập
- state:
  - fullName input
  - email input
  - password input
  - confirmPassword input
  - toggle hiển thị mật khẩu / xác nhận mật khẩu

## OcrResultScreen
- file: `feature/scanimage/OcrResultScreen.kt`
- component chính:
  - top bar (back + title)
  - info banner (status message)
  - source chip (nguồn OCR)
  - OCR text editor card (editable text + parse button)
  - parsed wifi card (SSID/password/security/confidence)
  - **SsidSuggestionCard** (fuzzy match gợi ý)
- state: dùng chung `MainUiState`

## SsidSuggestionCard
- file: `feature/scanimage/SsidSuggestionCard.kt`
- component chính:
  - gradient header "Gợi ý mạng Wi-Fi"
  - 3 state UI: Loading / Found / NotFound
  - Found: OCR chip (amber) → match chip (green) + confidence bar + 2 CTA
  - NotFound: gentle message + nút "Giữ nguyên"
  - danh sách mạng gần đây expand/collapse
- state:
  - SsidSuggestionState (Hidden/Loading/Found/NotFound)
  - nearbyNetworks list
  - isNearbyExpanded toggle
