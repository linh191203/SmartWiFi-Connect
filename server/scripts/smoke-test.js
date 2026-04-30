const assert = require("node:assert/strict");
const { parseOcrWifiData } = require("../src/ocrParser");
const { validateWifiCandidate } = require("../src/aiValidator");

function runCase(input, expected) {
  const result = parseOcrWifiData(input);
  assert.equal(result.ok, true, `Expected ok=true for input: ${input}`);

  for (const [key, value] of Object.entries(expected)) {
    assert.equal(
      result.data[key],
      value,
      `Expected ${key}=${value} but got ${result.data[key]} for input: ${input}`,
    );
  }
}

runCase("MyWifi\npassword: 12345678", {
  ssid: "MyWifi",
  password: "12345678",
  sourceFormat: "two_line_ssid_password",
  passwordOnly: false,
});

runCase("MyWifi\n12345678", {
  ssid: "MyWifi",
  password: "12345678",
  sourceFormat: "two_line_ssid_password",
  passwordOnly: false,
});

runCase("Wi-Fi\nbephaba\nbabaloveu", {
  ssid: "bephaba",
  password: "babaloveu",
  sourceFormat: "two_line_ssid_password",
  passwordOnly: false,
});

runCase("password: abcdef12", {
  ssid: null,
  password: "abcdef12",
  sourceFormat: "labeled_text",
  passwordOnly: true,
});

runCase("abcdef12", {
  ssid: null,
  password: "abcdef12",
  sourceFormat: "single_line_password",
  passwordOnly: true,
});

runCase(
  "Chao mung den voi Cafe ABC\nTen WiFi: Cafe_5G\nMat khau: Abc12345\nVui long khong chia se mat khau",
  {
    ssid: "Cafe_5G",
    password: "Abc12345",
    sourceFormat: "labeled_text",
    passwordOnly: false,
  },
);

runCase("WiFi Name:\nMyHomeNet\nPassword:\nA1b2c3d4", {
  ssid: "MyHomeNet",
  password: "A1b2c3d4",
  sourceFormat: "two_line_ssid_password",
  passwordOnly: false,
});

runCase(
  "Thong tin truy cap mang\nVui long luu lai:\nWIFI:T:WPA;S:MyOffice;P:Qwerty123;;\nCam on",
  {
    ssid: "MyOffice",
    password: "Qwerty123",
    sourceFormat: "wifi_qr",
    passwordOnly: false,
  },
);

runCase(
  "án dẫn!\nnói!?\nHỦ TIẾU BÒ KHO\nMÌ GÓI BÒ KHO\nTHÔNG TIN\nWIFI: Chú Mập\nPass: xincamon\nOPEN: 4 PM-12AM",
  {
    ssid: "Chú Mập",
    password: "xincamon",
    sourceFormat: "labeled_text",
    passwordOnly: false,
  },
);

runCase("WIFI: Chú Mập\nPass: xincamon", {
  ssid: "Chú Mập",
  password: "xincamon",
  sourceFormat: "two_line_ssid_password",
  passwordOnly: false,
});

const validAiReview = validateWifiCandidate({
  ssid: "OfficeNet",
  password: "A1b2c3d4",
  ocrText: "Ten WiFi: OfficeNet\nMat khau: A1b2c3d4",
});
assert.equal(validAiReview.validated, true);
assert.equal(validAiReview.shouldAutoConnect, true);
assert.equal(validAiReview.parseRecommendation, "connect");
assert.equal(validAiReview.normalizedSsid, "OfficeNet");

const incompleteAiReview = validateWifiCandidate({
  ocrText: "Ten WiFi: OfficeNet",
});
assert.equal(incompleteAiReview.validated, true);
assert.equal(incompleteAiReview.shouldAutoConnect, false);
assert.equal(incompleteAiReview.parseRecommendation, "retry_ocr");
assert.ok(incompleteAiReview.flags.includes("missing_password"));

const tooLongSsid = validateWifiCandidate({
  ssid: "A".repeat(33),
  password: "12345678",
});
assert.equal(tooLongSsid.validated, true);
assert.equal(tooLongSsid.shouldAutoConnect, false);
assert.ok(tooLongSsid.flags.includes("ssid_too_long"));

console.log("Server smoke tests passed.");
