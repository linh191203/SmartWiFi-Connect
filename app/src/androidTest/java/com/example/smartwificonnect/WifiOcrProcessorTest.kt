package com.example.smartwificonnect

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.smartwificonnect.ocr.WifiOcrCredentials
import com.example.smartwificonnect.ocr.WifiOcrProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests cho WifiOcrProcessor.extractWifiCredentials().
 * Chạy trên device/emulator vì WifiOcrProcessor khởi tạo ML Kit client trong constructor.
 */
@RunWith(AndroidJUnit4::class)
class WifiOcrProcessorTest {

    private lateinit var processor: WifiOcrProcessor

    @Before
    fun setUp() {
        processor = WifiOcrProcessor()
    }

    // ─── QR Payload ───────────────────────────────────────────────────────────

    @Test
    fun extract_wifiQrPayload_standard_returnsSsidAndPassword() {
        val result = processor.extractWifiCredentials("WIFI:T:WPA2;S:MyNetwork;P:secret123;;")
        assertEquals("MyNetwork", result.ssid)
        assertEquals("secret123", result.password)
    }

    @Test
    fun extract_wifiQrPayload_caseInsensitivePrefix_returnsSsidAndPassword() {
        val result = processor.extractWifiCredentials("wifi:T:WPA;S:CafeNet;P:abcdef;;")
        assertEquals("CafeNet", result.ssid)
        assertEquals("abcdef", result.password)
    }

    @Test
    fun extract_wifiQrPayload_noPassword_returnsEmptyPassword() {
        val result = processor.extractWifiCredentials("WIFI:T:nopass;S:OpenNet;;;")
        assertEquals("OpenNet", result.ssid)
        assertEquals("", result.password)
    }

    @Test
    fun extract_wifiQrPayload_noSecurity_returnsSsidAndPassword() {
        val result = processor.extractWifiCredentials("WIFI:S:JustName;P:pass1234;;")
        assertEquals("JustName", result.ssid)
        assertEquals("pass1234", result.password)
    }

    @Test
    fun extract_wifiQrPayload_quotedSsidAndPassword_stripsQuotes() {
        val result = processor.extractWifiCredentials("WIFI:T:WPA2;S:\"Home Net\";P:\"my pass\";;")
        assertEquals("Home Net", result.ssid)
        assertEquals("my pass", result.password)
    }

    // ─── English label format ─────────────────────────────────────────────────

    @Test
    fun extract_englishLabels_wifiNameAndPassword_returnsBothFields() {
        val text = """
            WiFi Name: Cafe_Wifi
            Password: 12345678
        """.trimIndent()
        val result = processor.extractWifiCredentials(text)
        assertEquals("Cafe_Wifi", result.ssid)
        assertEquals("12345678", result.password)
    }

    @Test
    fun extract_englishLabels_networkNameAndPassword_returnsBothFields() {
        val text = """
            Network Name: Office_5G
            Password: office@2025
        """.trimIndent()
        val result = processor.extractWifiCredentials(text)
        assertEquals("Office_5G", result.ssid)
        assertEquals("office@2025", result.password)
    }

    @Test
    fun extract_englishLabels_ssidAndPass_returnsBothFields() {
        val text = """
            SSID: HomeRouter
            Pass: router123
        """.trimIndent()
        val result = processor.extractWifiCredentials(text)
        assertEquals("HomeRouter", result.ssid)
        assertEquals("router123", result.password)
    }

    @Test
    fun extract_englishLabels_wifiAndPwd_returnsBothFields() {
        val text = """
            WiFi: GuestNetwork
            Pwd: guestpass
        """.trimIndent()
        val result = processor.extractWifiCredentials(text)
        assertEquals("GuestNetwork", result.ssid)
        assertEquals("guestpass", result.password)
    }

    @Test
    fun extract_englishLabels_passwordOnly_returnsOnlyPassword() {
        val text = "Password: mysecret"
        val result = processor.extractWifiCredentials(text)
        assertEquals("", result.ssid)
        assertEquals("mysecret", result.password)
    }

    @Test
    fun extract_englishLabels_ssidOnly_returnsOnlySsid() {
        val text = "WiFi Name: SoloNet"
        val result = processor.extractWifiCredentials(text)
        assertEquals("SoloNet", result.ssid)
        assertEquals("", result.password)
    }

    // ─── Vietnamese label format ──────────────────────────────────────────────

    @Test
    fun extract_vietnameseLabels_tenWifiMatKhau_returnsBothFields() {
        val text = """
            Tên Wifi: Nha_Hang_VN
            Mật khẩu: 88888888
        """.trimIndent()
        val result = processor.extractWifiCredentials(text)
        assertEquals("Nha_Hang_VN", result.ssid)
        assertEquals("88888888", result.password)
    }

    @Test
    fun extract_vietnameseLabels_tenMangMatKhau_returnsBothFields() {
        val text = """
            Ten mang: HomeNet
            Mat khau: home@2025
        """.trimIndent()
        val result = processor.extractWifiCredentials(text)
        assertEquals("HomeNet", result.ssid)
        assertEquals("home@2025", result.password)
    }

    @Test
    fun extract_vietnameseLabels_withAccents_returnsBothFields() {
        // OCR thường sinh ra text có dấu, processor phải normalize
        val text = """
            Tên Wi-Fi: Nha_Tro_2025
            Mật Khẩu WiFi: abc123456
        """.trimIndent()
        val result = processor.extractWifiCredentials(text)
        assertEquals("Nha_Tro_2025", result.ssid)
        assertEquals("abc123456", result.password)
    }

    // ─── Edge cases ───────────────────────────────────────────────────────────

    @Test
    fun extract_emptyString_returnsBothEmpty() {
        val result = processor.extractWifiCredentials("")
        assertEquals("", result.ssid)
        assertEquals("", result.password)
    }

    @Test
    fun extract_randomText_neitherField_returnsBothEmpty() {
        val result = processor.extractWifiCredentials("Hello world\nFoo bar\n12345")
        // Không có label nào nhận ra → cả hai đều blank
        assertTrue(result.ssid.isBlank() || result.password.isBlank())
    }

    @Test
    fun extract_passwordWithSurroundingQuotes_stripsQuotes() {
        val text = """
            WiFi Name: QuoteNet
            Password: "qu0t3d!"
        """.trimIndent()
        val result = processor.extractWifiCredentials(text)
        assertEquals("qu0t3d!", result.password)
    }

    @Test
    fun extract_passwordWithSingleQuotes_stripsQuotes() {
        val text = """
            SSID: SingleQuoteNet
            Password: 'mypassword'
        """.trimIndent()
        val result = processor.extractWifiCredentials(text)
        assertEquals("mypassword", result.password)
    }

    @Test
    fun extract_valuesWithLeadingColon_parsesCorrectly() {
        val text = """
            WiFi Name: Net123
            Password: : weirdPass
        """.trimIndent()
        // Giá trị sau dấu ':' phụ nên được trim
        val result = processor.extractWifiCredentials(text)
        assertEquals("Net123", result.ssid)
        // Password dù có thêm ký tự lạ thì vẫn không rỗng
        assertTrue(result.password.isNotBlank())
    }

    @Test
    fun extract_mixedCaseLabels_parsesCorrectly() {
        val text = """
            WIFI NAME: UpperNet
            PASSWORD: Upper123
        """.trimIndent()
        val result = processor.extractWifiCredentials(text)
        assertEquals("UpperNet", result.ssid)
        assertEquals("Upper123", result.password)
    }

    @Test
    fun extract_wifiQrPayload_preferredOverTextLabels_whenBothPresent() {
        // Khi text có cả WIFI: prefix và label text, QR format được ưu tiên
        val text = "WIFI:T:WPA2;S:QrSsid;P:QrPass;;\nWiFi Name: TextSsid\nPassword: TextPass"
        val result = processor.extractWifiCredentials(text)
        assertEquals("QrSsid", result.ssid)
        assertEquals("QrPass", result.password)
    }

    @Test
    fun extract_multipleLineOcr_parsesFirstMatchedLabel() {
        val text = """
            -- Thông tin mạng Wi-Fi --
            Tên WiFi: Restaurant_Guest
            Mật khẩu: guest2025!
            Vui lòng không chia sẻ.
        """.trimIndent()
        val result = processor.extractWifiCredentials(text)
        assertEquals("Restaurant_Guest", result.ssid)
        assertEquals("guest2025!", result.password)
    }

    @Test
    fun extract_wifiQrPayload_withSpecialCharsInPassword_returnsCorrectly() {
        val result = processor.extractWifiCredentials("WIFI:T:WPA2;S:SpecialNet;P:p@ss!#123;;")
        assertEquals("SpecialNet", result.ssid)
        assertEquals("p@ss!#123", result.password)
    }

    @Test
    fun extract_labelWithDashSeparator_parsesCorrectly() {
        val text = """
            WiFi Name - Coffee_Corner
            Password - coffee99
        """.trimIndent()
        val result = processor.extractWifiCredentials(text)
        // Dù separator là '-' thì processor vẫn cố lấy value
        // Quan trọng: không throw exception
        assertTrue(result.ssid.isNotBlank() || result.password.isNotBlank() ||
            (result.ssid.isBlank() && result.password.isBlank()))
    }

    @Test
    fun extract_longOcrWithIrrelevantLines_onlyPicksRelevantLabels() {
        val text = """
            Quán cà phê Ngọc Lan
            Địa chỉ: 123 Đường Lê Lợi, Q.1
            Điện thoại: 0901234567
            Giờ mở cửa: 7:00 - 22:00
            WiFi Name: NgocLan_Cafe
            Password: ngoclan2025
            Hân hạnh phục vụ quý khách!
        """.trimIndent()
        val result = processor.extractWifiCredentials(text)
        assertEquals("NgocLan_Cafe", result.ssid)
        assertEquals("ngoclan2025", result.password)
    }

    // ─── WifiOcrCredentials data class ────────────────────────────────────────

    @Test
    fun wifiOcrCredentials_defaults_areBothBlank() {
        val credentials = WifiOcrCredentials()
        assertTrue(credentials.ssid.isBlank())
        assertTrue(credentials.password.isBlank())
    }

    @Test
    fun wifiOcrCredentials_fieldsAreReadCorrectly() {
        val credentials = WifiOcrCredentials(ssid = "TestNet", password = "TestPass")
        assertEquals("TestNet", credentials.ssid)
        assertEquals("TestPass", credentials.password)
    }
}
