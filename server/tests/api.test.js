const request = require("supertest");
const app = require("../src/app");

describe("GET /health", () => {
  test("returns 200 with ok: true", async () => {
    const res = await request(app).get("/health");
    expect(res.status).toBe(200);
    expect(res.body.ok).toBe(true);
    expect(res.body.service).toBe("smartwificonnect-server");
    expect(typeof res.body.timestamp).toBe("string");
  });
});

describe("POST /api/v1/ocr/parse", () => {
  test("returns 200 with parsed wifi data for QR format", async () => {
    const res = await request(app)
      .post("/api/v1/ocr/parse")
      .send({ ocrText: "WIFI:T:WPA;S:TestNet;P:testpass1;;" });
    expect(res.status).toBe(200);
    expect(res.body.ok).toBe(true);
    expect(res.body.data.ssid).toBe("TestNet");
    expect(res.body.data.password).toBe("testpass1");
  });

  test("returns 200 for labeled text (ssid and password labels stripped)", async () => {
    const res = await request(app)
      .post("/api/v1/ocr/parse")
      .send({ ocrText: "SSID: HomeWifi\nPassword: home12345" });
    expect(res.status).toBe(200);
    expect(res.body.ok).toBe(true);
    expect(res.body.data.ssid).toBe("HomeWifi");
    expect(res.body.data.password).toBe("home12345");
    expect(res.body.data.sourceFormat).toBe("labeled_text");
  });

  test("returns 400 when ocrText is missing", async () => {
    const res = await request(app).post("/api/v1/ocr/parse").send({});
    expect(res.status).toBe(400);
    expect(res.body.ok).toBe(false);
  });

  test("returns 400 when ocrText is not a string", async () => {
    const res = await request(app).post("/api/v1/ocr/parse").send({ ocrText: 123 });
    expect(res.status).toBe(400);
    expect(res.body.ok).toBe(false);
  });

  test("returns 400 when ocrText is empty string", async () => {
    const res = await request(app).post("/api/v1/ocr/parse").send({ ocrText: "   " });
    expect(res.status).toBe(400);
    expect(res.body.ok).toBe(false);
  });
});

describe("POST /api/ai/validate", () => {
  test("returns 200 with validation result for ssid + password", async () => {
    const res = await request(app)
      .post("/api/ai/validate")
      .send({ ssid: "CafeNet", password: "cafepass123" });
    expect(res.status).toBe(200);
    expect(res.body.ok).toBe(true);
    expect(res.body.data.validated).toBe(true);
    expect(res.body.data.normalizedSsid).toBe("CafeNet");
    expect(typeof res.body.data.confidence).toBe("number");
  });

  test("returns 200 when only ocrText provided", async () => {
    const res = await request(app)
      .post("/api/ai/validate")
      .send({ ocrText: "WIFI:S:OcrOnly;P:ocrpass99;;" });
    expect(res.status).toBe(200);
    expect(res.body.ok).toBe(true);
  });

  test("returns 400 when no fields provided", async () => {
    const res = await request(app).post("/api/ai/validate").send({});
    expect(res.status).toBe(400);
    expect(res.body.ok).toBe(false);
  });

  test("returns 400 when ssid is not a string", async () => {
    const res = await request(app).post("/api/ai/validate").send({ ssid: 42 });
    expect(res.status).toBe(400);
    expect(res.body.ok).toBe(false);
  });

  test("returns 400 when all fields are empty strings", async () => {
    const res = await request(app)
      .post("/api/ai/validate")
      .send({ ssid: "  ", password: "  ", ocrText: "  " });
    expect(res.status).toBe(400);
    expect(res.body.ok).toBe(false);
  });

  test("response includes input echo", async () => {
    const res = await request(app)
      .post("/api/ai/validate")
      .send({ ssid: "EchoNet", password: "echo12345" });
    expect(res.body.input.ssid).toBe("EchoNet");
    expect(res.body.input.password).toBe("echo12345");
  });
});

describe("unknown routes", () => {
  test("returns 404 for unknown endpoint", async () => {
    const res = await request(app).get("/api/unknown");
    expect(res.status).toBe(404);
    expect(res.body.ok).toBe(false);
  });
});
