# Roadmap - SmartWiFi-Connect

## Mục tiêu tổng thể
Hoàn thiện web app hỗ trợ:
- quét mã QR Wi-Fi
- quét ảnh Wi-Fi bằng OCR
- review thông tin trước khi kết nối
- lưu lịch sử cục bộ và backend
- kiểm thử, deploy, demo hoàn chỉnh

---

## Giai đoạn 1 — Hoàn thiện UI (✅ Xong)
- [x] Tạo project React + Vite
- [x] Các màn hình: Home, Onboarding, Login, Register, QR, OCR, Manual, Review, History, Settings
- [x] localStorage cho lịch sử và mạng đã lưu
- [x] Backend Node.js cơ bản: `/api/health`, `/api/ai/validate` dummy

---

## Giai đoạn 2 — Tích hợp API
- [ ] Gọi API `/api/ai/validate` thật từ ReviewScreen
- [ ] `POST /api/networks` — lưu mạng vào DB
- [ ] Mã hóa password server-side
- [ ] Unit test backend

---

## Giai đoạn 3 — Ổn định & Deploy
- [ ] Test end-to-end
- [ ] Tối ưu OCR parse
- [ ] Deploy backend
- [ ] Swagger / Postman docs
- [ ] Demo hoàn chỉnh