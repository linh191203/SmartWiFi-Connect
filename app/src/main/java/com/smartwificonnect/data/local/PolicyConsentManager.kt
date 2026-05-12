package com.smartwificonnect.data.local

import android.content.Context
import androidx.core.content.edit

/**
 * Lưu trữ trạng thái đồng ý chính sách bảo mật của người dùng.
 * Sử dụng SharedPreferences để ghi nhận lựa chọn chính sách của người dùng.
 */
class PolicyConsentManager(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Kiểm tra người dùng đã chấp nhận chính sách chưa. */
    fun hasConsented(): Boolean = prefs.getBoolean(KEY_CONSENT_GIVEN, false)

    /** Lưu trạng thái đã đồng ý. */
    fun saveConsent() {
        prefs.edit { putBoolean(KEY_CONSENT_GIVEN, true) }
    }

    companion object {
        private const val PREFS_NAME = "policy_consent_prefs"
        private const val KEY_CONSENT_GIVEN = "consent_given"

        const val PRIVACY_POLICY_TITLE = "Chính sách Bảo mật"
        const val PRIVACY_POLICY_TEXT =
            "SmartWiFi-Connect dùng camera hoặc ảnh do bạn chọn để quét QR/OCR thông tin Wi-Fi. " +
                "Ứng dụng có thể cần quyền Wi-Fi, thiết bị lân cận và vị trí gần đúng/chính xác vì Android yêu cầu các quyền này để quét danh sách Wi-Fi xung quanh.\n\n" +
                "Tên Wi-Fi, mật khẩu và nội dung OCR chỉ được dùng cho các thao tác bạn chủ động thực hiện như kết nối, chia sẻ hoặc lưu lịch sử trên thiết bị. " +
                "Mật khẩu trong lịch sử cục bộ được mã hóa. Khi bạn bật hoặc cấu hình server AI, văn bản OCR có thể được gửi để phân tích và bạn cần đảm bảo nội dung đó được phép xử lý.\n\n" +
                "Ứng dụng không bán dữ liệu Wi-Fi, không tự động thử mật khẩu với các mạng xung quanh khi bạn chưa chọn mạng, và bạn có thể xóa lịch sử kết nối trong Cài đặt."

        /** URL công khai của chính sách bảo mật. Cập nhật trước khi phát hành production. */
        const val PRIVACY_POLICY_URL = "https://smartwificonnect.app/privacy"
    }
}
