# Roadmap - SmartWiFi-Connect

## Mục tiêu tổng thể
Hoàn thiện ứng dụng Android hỗ trợ:
- quét mã QR Wi-Fi
- quét ảnh Wi-Fi bằng OCR
- review thông tin trước khi kết nối
- kết nối Wi-Fi hợp lệ trên Android
- lưu lịch sử cục bộ và backend
- kiểm thử, build APK, demo hoàn chỉnh

---

## Sprint 1 (22/03–27/03)
### Mục tiêu
Khởi tạo hệ thống, repo, CI/CD cơ bản, thiết kế khung UI.

### FE
- Tạo project Android Studio + Git repo
- Thiết lập cấu trúc package
- Cấu hình CI/CD cơ bản
- Cài thư viện chính:
    - CameraX
    - ML Kit
    - Room
    - Navigation Compose
- Thiết kế wireframe UI
- Viết test đầu tiên

### BE
- Thiết lập server cơ bản
- Khởi tạo DB
- Tạo endpoint dummy:
    - `/api/health`
    - `/api/ai/validate`

### Deliverable
- Skeleton app chạy được
- Repo Git + GitHub
- API skeleton
- Wireframe cơ bản
- CI/CD bước đầu

---

## Sprint 2 (29/03–03/04)
### Mục tiêu
Quét Wi-Fi, OCR thử nghiệm, mock API hoạt động.

### FE
- UI danh sách mạng / màn hình chính
- Permission flow
- UI camera / chọn ảnh
- Tích hợp OCR on-device bằng ML Kit
- Kiểm thử tích hợp với mock API

### BE
- Endpoint dummy `/api/ai/validate`
- Trả JSON mẫu
- Unit test backend

### Deliverable
- App đọc được text từ ảnh
- FE xử lý được response JSON mẫu
- Backend trả được response giả lập

---

## Sprint 3 (05/04–10/04)
### Mục tiêu
Hiển thị kết quả OCR/AI, fuzzy match SSID, lưu tạm cục bộ.

### FE
- Gợi ý SSID gần đúng
- Gọi API `/api/ai/validate`
- Hiển thị Review Result
- Lưu tạm local bằng Room/SQLite
- Kiểm thử tích hợp

### BE
- Tích hợp AI backend thực tế hoặc mock nâng cao
- Unit test `/api/ai/validate`
- Sửa lỗi và tinh chỉnh tích hợp

### Deliverable
- Review screen hoàn chỉnh
- API validate hoạt động
- Lưu local bước đầu

---

## Sprint 4 (12/04–17/04)
### Mục tiêu
Kết nối Wi-Fi thật, lưu lịch sử local + backend, hoàn thiện flow chính.

### FE
- Implement kết nối Wi-Fi thật trên Android bằng API phù hợp
- Xử lý trạng thái kết nối:
    - loading
    - thành công
    - sai mật khẩu
    - timeout
- Gọi API lưu network sau khi kết nối thành công
- Lưu cục bộ bằng Room/SQLite
- Xây màn History

### BE
- Tạo endpoint `POST /api/networks`
- Lưu SSID/password vào DB
- Mã hóa mật khẩu trước khi lưu
- Viết unit test cho API save network

### Deliverable
- Flow hoàn chỉnh:
  scan / OCR / review / connect / save
- History local
- Save network backend
- Module kết nối ổn định

---

## Sprint 5 (19/04–24/04)
### Mục tiêu
Tăng độ ổn định, test, tối ưu OCR, tối ưu UI/UX.

### FE
- Unit test cho xử lý kết nối và lưu local
- Integration/E2E test
- Tối ưu OCR:
    - tiền xử lý ảnh
    - chỉnh sáng / crop / nén
- Tinh chỉnh UI/UX
- Sửa lỗi từ test

### BE
- Validate dữ liệu đầu vào
- Tối ưu backend AI:
    - timeout
    - fallback
- Refactor backend
- Cập nhật docs API

### Deliverable
- Bộ test ổn định
- OCR chính xác hơn
- UI mượt hơn
- Build ít lỗi hơn

---

## Sprint 6 (26/04–01/05)
### Mục tiêu
Đóng gói sản phẩm, CI/CD, build APK, deploy backend, demo và release.

### FE
- Hoàn thiện CI/CD
- Build APK release
- Cấu hình gradle/version
- Chuẩn bị dữ liệu demo
- Viết hướng dẫn sử dụng
- Fix bug cuối

### BE
- Deploy backend
- Kiểm tra endpoint demo
- Viết Swagger/Postman
- Fix bug cuối

### Cả FE + BE
- Test end-to-end lần cuối
- Kiểm tra pipeline build/test/deploy
- Final testing & release

### Deliverable
- APK sẵn sàng demo/release
- Backend chạy ổn định
- Tài liệu đầy đủ
- Bản cuối của dự án