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