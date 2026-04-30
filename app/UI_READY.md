# UI Ready Notes

Project da duoc chuan bi de them giao dien moi (Activity/Fragment/Compose) ma khong can sua logic backend:

- `MainViewModel` quan ly state va action (health check, parse OCR).
- `WifiRepository` tach logic data khoi UI.
- `DefaultWifiRepository` xu ly goi API den backend.
- `WifiOcrProcessor` xu ly OCR on-device.
- Toan bo text giao dien da dua vao `res/values/strings.xml` de de i18n va scale UI.

Flow hien tai:

1. Camera -> OCR text
2. OCR text -> API parse
3. Ket qua -> hien thi va cho phep sua tay SSID/password

Khi them UI moi:

- Tai su dung `MainViewModel` va `WifiRepository`.
- Hoac tao ViewModel moi nhung van goi qua `WifiRepository`.
- Giu base URL co the sua trong UI de test Emulator/LAN.
