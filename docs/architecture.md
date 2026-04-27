# Architecture - SmartWiFi-Connect

## Kiến trúc tổng thể
- Web Frontend (`web/`) — React + Vite
- Backend (`server/`) — Node.js + Express

## Cấu trúc frontend (`web/src/`)
```
pages/        — màn hình chính (HomeScreen, HistoryScreen, ...)
components/   — component dùng chung (NetworkCard, EmptyState)
context/      — global state (AppState)
lib/          — logic phụ trợ (api.js, format.js, storage.js, wifiRepository.js)
```

## Cấu trúc backend (`server/src/`)
```
index.js       — Express app, routes
oiParser.js    — parse kết quả OCR
aiValidator.js — validate SSID/password
```

## Luồng dữ liệu
User action → Page component → lib/api.js → Backend API → Response → UI update

## Quy tắc
- Mỗi màn hình 1 file trong `pages/`
- Logic gọi API tập trung trong `lib/api.js`
- State lịch sử và mạng đã lưu qua `localStorage` (wifiRepository.js)
- Không để business logic trực tiếp trong JSX component