const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
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

runCase("Wifi: Nguyễn Hoàng Bakery 2.4g\nPASS WIFI: 2014bakery", {
  ssid: "Nguyễn Hoàng Bakery 2.4g",
  password: "2014bakery",
  sourceFormat: "labeled_text",
  passwordOnly: false,
});

runCase("CAFE61\n61616161", {
  ssid: "CAFE61",
  password: "61616161",
  sourceFormat: "two_line_ssid_password",
  passwordOnly: false,
});

runCase("99hoanghoatham", {
  ssid: null,
  password: "99hoanghoatham",
  sourceFormat: "single_line_password",
  passwordOnly: true,
});

runCase("68 x4", {
  ssid: null,
  password: "68686868",
  sourceFormat: "single_line_password",
  passwordOnly: true,
});

runCase("W1F1 \uFF1A The Monday Coffee\nPA55 \uFF1A TheMondayCoffee", {
  ssid: "The Monday Coffee",
  password: "TheMondayCoffee",
  passwordOnly: false,
});

runCase("wifi\nName : Password :\nCAFE M\u1ED8C    Cf222222", {
  ssid: "CAFE MỘC",
  password: "Cf222222",
  sourceFormat: "split_row",
  passwordOnly: false,
});

runCase("Wi-Fi\nID\nbepbaha\nbabaloveu", {
  ssid: "bepbaha",
  password: "babaloveu",
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
  sourceFormat: "labeled_text",
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

process.env.NETWORKS_FILE = path.join(
  fs.mkdtempSync(path.join(os.tmpdir(), "smartwifi-smoke-")),
  "networks.json",
);
const { app } = require("../src/index");

async function requestJson(baseUrl, route, options = {}) {
  const response = await fetch(`${baseUrl}${route}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {}),
    },
  });
  const body = await response.json();
  return { response, body };
}

async function runEndpointSmokeTests() {
  const server = app.listen(0);
  const address = server.address();
  const baseUrl = `http://127.0.0.1:${address.port}`;

  try {
    const health = await requestJson(baseUrl, "/health");
    assert.equal(health.response.status, 200);
    assert.equal(health.body.ok, true);
    assert.equal(health.response.headers.get("x-content-type-options"), "nosniff");

    const fuzzy = await requestJson(baseUrl, "/api/v1/ssid/fuzzy-match", {
      method: "POST",
      body: JSON.stringify({
        ocrSsid: "Home Cloud 5G",
        nearbyNetworks: [
          { ssid: "Guest_WiFi", signalLevel: 2 },
          { ssid: "Home_Cloud_5G", signalLevel: 4 },
        ],
      }),
    });
    assert.equal(fuzzy.response.status, 200);
    assert.equal(fuzzy.body.ok, true);
    assert.equal(fuzzy.body.data.bestMatch, "Home_Cloud_5G");

    const saved = await requestJson(baseUrl, "/api/networks", {
      method: "POST",
      body: JSON.stringify({
        ssid: "Home_Cloud_5G",
        password: "secret123",
        security: "WPA2",
        connectedAtEpochMs: Date.now(),
      }),
    });
    assert.equal(saved.response.status, 201);
    assert.equal(saved.body.ok, true);
    assert.equal(saved.body.data.ssid, "Home_Cloud_5G");
    assert.equal(saved.body.data.password, undefined);
    assert.equal(saved.body.data.passwordHash, undefined);
    const persistedRecords = JSON.parse(fs.readFileSync(process.env.NETWORKS_FILE, "utf8"));
    assert.equal(persistedRecords.length, 1);
    assert.match(persistedRecords[0].passwordHash, /^sha256:[0-9a-f]{32}:[0-9a-f]{64}$/);

    const list = await requestJson(baseUrl, "/api/networks");
    assert.equal(list.response.status, 200);
    assert.equal(list.body.data.total, 1);
    assert.equal(list.body.data.records[0].ssid, "Home_Cloud_5G");

    const otp = await requestJson(baseUrl, "/api/auth/request-otp", {
      method: "POST",
      body: JSON.stringify({ email: "user@example.com" }),
    });
    assert.equal(otp.response.status, 200);
    assert.equal(otp.body.ok, true);

    const verified = await requestJson(baseUrl, "/api/auth/verify-otp", {
      method: "POST",
      body: JSON.stringify({ email: "user@example.com", code: "123456" }),
    });
    assert.equal(verified.response.status, 200);
    assert.equal(verified.body.ok, true);
  } finally {
    await new Promise((resolve, reject) => {
      server.close((error) => (error ? reject(error) : resolve()));
    });
  }
}

async function runAuthSmokeTests() {
  const originalToken = process.env.API_AUTH_TOKEN;
  const originalNetworksFile = process.env.NETWORKS_FILE;
  process.env.API_AUTH_TOKEN = "smoke-token";
  process.env.NETWORKS_FILE = path.join(
    fs.mkdtempSync(path.join(os.tmpdir(), "smartwifi-auth-smoke-")),
    "networks.json",
  );
  delete require.cache[require.resolve("../src/index")];
  const { app: authedApp } = require("../src/index");
  const server = authedApp.listen(0);
  const address = server.address();
  const baseUrl = `http://127.0.0.1:${address.port}`;

  try {
    const rejected = await requestJson(baseUrl, "/api/networks");
    assert.equal(rejected.response.status, 401);

    const accepted = await requestJson(baseUrl, "/api/networks", {
      headers: { Authorization: "Bearer smoke-token" },
    });
    assert.equal(accepted.response.status, 200);
    assert.equal(accepted.body.ok, true);
  } finally {
    await new Promise((resolve, reject) => {
      server.close((error) => (error ? reject(error) : resolve()));
    });
    if (originalToken === undefined) {
      delete process.env.API_AUTH_TOKEN;
    } else {
      process.env.API_AUTH_TOKEN = originalToken;
    }
    if (originalNetworksFile === undefined) {
      delete process.env.NETWORKS_FILE;
    } else {
      process.env.NETWORKS_FILE = originalNetworksFile;
    }
    delete require.cache[require.resolve("../src/index")];
  }
}

runEndpointSmokeTests()
  .then(runAuthSmokeTests)
  .then(() => {
    console.log("Server smoke tests passed.");
  })
  .catch((error) => {
    console.error(error);
    process.exitCode = 1;
  });
