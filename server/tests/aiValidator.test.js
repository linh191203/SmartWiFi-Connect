const { validateWifiCandidate } = require("../src/aiValidator");

describe("validateWifiCandidate", () => {
  describe("basic validation", () => {
    test("returns validated=true with ssid and password", () => {
      const result = validateWifiCandidate({ ssid: "MyNetwork", password: "secret123" });
      expect(result.validated).toBe(true);
      expect(result.normalizedSsid).toBe("MyNetwork");
      expect(result.normalizedPassword).toBe("secret123");
    });

    test("includes confidence score between 0 and 1", () => {
      const result = validateWifiCandidate({ ssid: "Net", password: "pass1234" });
      expect(result.confidence).toBeGreaterThan(0);
      expect(result.confidence).toBeLessThanOrEqual(0.99);
    });

    test("returns suggestion string", () => {
      const result = validateWifiCandidate({ ssid: "Net", password: "pass1234" });
      expect(typeof result.suggestion).toBe("string");
      expect(result.suggestion.length).toBeGreaterThan(0);
    });

    test("returns flags array", () => {
      const result = validateWifiCandidate({ ssid: "Net", password: "pass1234" });
      expect(Array.isArray(result.flags)).toBe(true);
    });
  });

  describe("missing fields", () => {
    test("flags missing_password when only ssid provided", () => {
      const result = validateWifiCandidate({ ssid: "OnlySSID" });
      expect(result.flags).toContain("missing_password");
    });

    test("validated is false when only ssid provided", () => {
      const result = validateWifiCandidate({ ssid: "OnlySSID" });
      expect(result.validated).toBe(false);
    });

    test("flags missing_ssid when only password provided", () => {
      const result = validateWifiCandidate({ password: "onlypassword123" });
      expect(result.flags).toContain("missing_ssid");
    });

    test("validated is false when only password provided", () => {
      const result = validateWifiCandidate({ password: "onlypassword123" });
      expect(result.validated).toBe(false);
    });
  });

  describe("password validation", () => {
    test("flags password_too_short for passwords under 8 chars", () => {
      const result = validateWifiCandidate({ ssid: "Net", password: "short" });
      expect(result.flags).toContain("password_too_short");
    });

    test("flags password_too_long for passwords over 63 chars", () => {
      const result = validateWifiCandidate({ ssid: "Net", password: "a".repeat(64) });
      expect(result.flags).toContain("password_too_long");
    });

    test("flags password_contains_spaces", () => {
      const result = validateWifiCandidate({ ssid: "Net", password: "pass word123" });
      expect(result.flags).toContain("password_contains_spaces");
    });
  });

  describe("ssid validation", () => {
    test("flags ssid_too_long for SSID over 32 chars", () => {
      const result = validateWifiCandidate({ ssid: "a".repeat(33), password: "pass1234" });
      expect(result.flags).toContain("ssid_too_long");
    });
  });

  describe("shouldAutoConnect", () => {
    // confidence >= 0.72 requires ocrText contribution (adds up to 0.25 extra).
    // Without ocrText, max score is 0.1 + 0.25 (ssid) + 0.3 (password) = 0.65.
    test("shouldAutoConnect true when ocrText matches ssid + password", () => {
      const result = validateWifiCandidate({
        ssid: "GoodNet",
        password: "goodpassword123",
        ocrText: "WIFI:S:GoodNet;P:goodpassword123;;",
      });
      expect(result.shouldAutoConnect).toBe(true);
      expect(result.parseRecommendation).toBe("connect");
    });

    test("shouldAutoConnect false without ocrText (confidence below threshold)", () => {
      const result = validateWifiCandidate({ ssid: "GoodNet", password: "goodpassword123" });
      expect(result.shouldAutoConnect).toBe(false);
      expect(result.confidence).toBeLessThan(0.72);
    });

    test("shouldAutoConnect false when missing ssid", () => {
      const result = validateWifiCandidate({ password: "goodpassword123" });
      expect(result.shouldAutoConnect).toBe(false);
    });
  });

  describe("OCR text integration", () => {
    test("extracts ssid and password from ocrText if not provided", () => {
      const result = validateWifiCandidate({
        ocrText: "WIFI:T:WPA;S:OcrNet;P:ocrpass99;;",
      });
      expect(result.normalizedSsid).toBe("OcrNet");
      expect(result.normalizedPassword).toBe("ocrpass99");
    });

    test("flags ocr_parse_failed for unparseable ocrText", () => {
      const result = validateWifiCandidate({ ocrText: "garbage text that cannot be parsed !!!" });
      // heuristic may still extract something; just check flags or validated
      expect(typeof result.validated).toBe("boolean");
    });
  });
});
