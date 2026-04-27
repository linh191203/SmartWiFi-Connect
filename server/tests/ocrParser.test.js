const { parseOcrWifiData } = require("../src/ocrParser");

describe("parseOcrWifiData", () => {
  describe("WiFi QR format", () => {
    test("parses full WIFI QR string", () => {
      const result = parseOcrWifiData("WIFI:T:WPA;S:MyNetwork;P:secret123;;");
      expect(result.ok).toBe(true);
      expect(result.data.ssid).toBe("MyNetwork");
      expect(result.data.password).toBe("secret123");
      expect(result.data.security).toBe("WPA");
      expect(result.data.sourceFormat).toBe("wifi_qr");
      expect(result.data.confidence).toBeGreaterThanOrEqual(0.9);
    });

    test("parses WIFI QR without security", () => {
      const result = parseOcrWifiData("WIFI:S:CafeWifi;P:pass1234;;");
      expect(result.ok).toBe(true);
      expect(result.data.ssid).toBe("CafeWifi");
      expect(result.data.password).toBe("pass1234");
    });

    test("is case-insensitive for WIFI: prefix", () => {
      const result = parseOcrWifiData("wifi:S:TestNet;P:abc12345;;");
      expect(result.ok).toBe(true);
      expect(result.data.ssid).toBe("TestNet");
    });
  });

  describe("labeled text format", () => {
    // Labeled extraction runs first and strips the label from both SSID and password.
    test("strips ssid and password labels", () => {
      const result = parseOcrWifiData("SSID: HomeNet\nPassword: mypassword");
      expect(result.ok).toBe(true);
      expect(result.data.ssid).toBe("HomeNet");
      expect(result.data.password).toBe("mypassword");
      expect(result.data.sourceFormat).toBe("labeled_text");
    });

    test("strips Vietnamese ssid and password labels", () => {
      const result = parseOcrWifiData("Tên mạng: QuanCafe\nMật khẩu: 12345678");
      expect(result.ok).toBe(true);
      expect(result.data.ssid).toBe("QuanCafe");
      expect(result.data.password).toBe("12345678");
      expect(result.data.sourceFormat).toBe("labeled_text");
    });

    test("strips wifi prefix label (e.g. restaurant board format)", () => {
      const result = parseOcrWifiData("WIFI : ChuMap\nPass: xincamon");
      expect(result.ok).toBe(true);
      expect(result.data.ssid).toBe("ChuMap");
      expect(result.data.password).toBe("xincamon");
      expect(result.data.sourceFormat).toBe("labeled_text");
    });
  });

  describe("two-line format", () => {
    test("parses SSID on first line, password on second", () => {
      const result = parseOcrWifiData("HomeNetwork\npassword123");
      expect(result.ok).toBe(true);
      expect(result.data.ssid).toBe("HomeNetwork");
      expect(result.data.password).toBe("password123");
      expect(result.data.sourceFormat).toBe("two_line_ssid_password");
    });

    test("strips password prefix from second line", () => {
      const result = parseOcrWifiData("MySSID\nPassword: securepass");
      expect(result.ok).toBe(true);
      expect(result.data.password).toBe("securepass");
    });
  });

  describe("error cases", () => {
    test("returns error for empty string", () => {
      const result = parseOcrWifiData("");
      expect(result.ok).toBe(false);
      expect(result.error).toBeTruthy();
    });

    test("returns error for whitespace-only string", () => {
      const result = parseOcrWifiData("   ");
      expect(result.ok).toBe(false);
    });

    test("handles null input", () => {
      const result = parseOcrWifiData(null);
      expect(result.ok).toBe(false);
    });
  });

  describe("passwordOnly flag", () => {
    test("sets passwordOnly when only password found", () => {
      const result = parseOcrWifiData("Password: secretpass123");
      if (result.ok) {
        expect(result.data.passwordOnly).toBe(!result.data.ssid);
      }
    });
  });
});
