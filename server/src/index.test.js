const request = require("supertest");
const { app, savedNetworks } = require("./index");

// Clear saved networks between tests to ensure isolation
beforeEach(() => {
  savedNetworks.length = 0;
});

describe("POST /api/networks", () => {
  describe("Valid requests", () => {
    test("should save a network with SSID and password", async () => {
      const response = await request(app)
        .post("/api/networks")
        .send({ ssid: "HomeWiFi", password: "SecurePass123!" })
        .expect(201);

      expect(response.body.ok).toBe(true);
      expect(response.body.data.ssid).toBe("HomeWiFi");
      expect(response.body.data).toHaveProperty("id");
      expect(response.body.data).toHaveProperty("savedAtEpochMs");
      // password must NOT be returned
      expect(response.body.data.password).toBeUndefined();
      expect(response.body.data.passwordHash).toBeUndefined();
    });

    test("should save network with SSID only (no password)", async () => {
      const response = await request(app)
        .post("/api/networks")
        .send({ ssid: "OpenNetwork" })
        .expect(201);

      expect(response.body.ok).toBe(true);
      expect(response.body.data.ssid).toBe("OpenNetwork");
    });

    test("should trim whitespace from SSID", async () => {
      const response = await request(app)
        .post("/api/networks")
        .send({ ssid: "  TrimmedSSID  " })
        .expect(201);

      expect(response.body.data.ssid).toBe("TrimmedSSID");
    });

    test("should accept optional fields: security, sourceFormat, confidence", async () => {
      const response = await request(app)
        .post("/api/networks")
        .send({
          ssid: "MyWiFi",
          password: "Pass1234!",
          security: "WPA2",
          sourceFormat: "qr",
          confidence: 0.95,
          connectedAtEpochMs: Date.now(),
        })
        .expect(201);

      expect(response.body.data.security).toBe("WPA2");
      expect(response.body.data.sourceFormat).toBe("qr");
      expect(response.body.data.confidence).toBe(0.95);
    });

    test("should persist network to savedNetworks array", async () => {
      await request(app)
        .post("/api/networks")
        .send({ ssid: "PersistTest" })
        .expect(201);

      expect(savedNetworks.length).toBe(1);
      expect(savedNetworks[0].ssid).toBe("PersistTest");
    });

    test("should hash password before storing", async () => {
      await request(app)
        .post("/api/networks")
        .send({ ssid: "SecureWiFi", password: "MyPlainPassword" })
        .expect(201);

      expect(savedNetworks[0].passwordHash).not.toBe("MyPlainPassword");
      expect(savedNetworks[0].passwordHash).toBeTruthy();
    });
  });

  describe("Input validation errors", () => {
    test("should return 400 when SSID is missing", async () => {
      const response = await request(app)
        .post("/api/networks")
        .send({ password: "SomePass" })
        .expect(400);

      expect(response.body.ok).toBe(false);
      expect(response.body.error).toContain("ssid");
    });

    test("should return 400 when SSID is empty string", async () => {
      const response = await request(app)
        .post("/api/networks")
        .send({ ssid: "   " })
        .expect(400);

      expect(response.body.ok).toBe(false);
    });

    test("should return 400 when SSID exceeds 64 characters", async () => {
      const response = await request(app)
        .post("/api/networks")
        .send({ ssid: "A".repeat(65) })
        .expect(400);

      expect(response.body.ok).toBe(false);
    });

    test("should return 400 when SSID is not a string", async () => {
      const response = await request(app)
        .post("/api/networks")
        .send({ ssid: 12345 })
        .expect(400);

      expect(response.body.ok).toBe(false);
    });

    test("should return 400 when password exceeds 128 characters", async () => {
      const response = await request(app)
        .post("/api/networks")
        .send({ ssid: "ValidSSID", password: "A".repeat(129) })
        .expect(400);

      expect(response.body.ok).toBe(false);
    });

    test("should return 400 when password is not a string", async () => {
      const response = await request(app)
        .post("/api/networks")
        .send({ ssid: "ValidSSID", password: 123456 })
        .expect(400);

      expect(response.body.ok).toBe(false);
    });

    test("should return 400 when connectedAtEpochMs is not a number", async () => {
      const response = await request(app)
        .post("/api/networks")
        .send({ ssid: "ValidSSID", connectedAtEpochMs: "not-a-number" })
        .expect(400);

      expect(response.body.ok).toBe(false);
    });
  });
});

describe("GET /api/networks", () => {
  test("should return empty list when no networks saved", async () => {
    const response = await request(app).get("/api/networks").expect(200);

    expect(response.body.ok).toBe(true);
    expect(response.body.data.records).toHaveLength(0);
    expect(response.body.data.total).toBe(0);
  });

  test("should return saved networks", async () => {
    await request(app).post("/api/networks").send({ ssid: "Network1" });
    await request(app).post("/api/networks").send({ ssid: "Network2" });

    const response = await request(app).get("/api/networks").expect(200);

    expect(response.body.data.records).toHaveLength(2);
    expect(response.body.data.total).toBe(2);
  });

  test("should not expose passwordHash in GET response", async () => {
    await request(app)
      .post("/api/networks")
      .send({ ssid: "SecureNet", password: "HiddenPassword" });

    const response = await request(app).get("/api/networks").expect(200);
    const record = response.body.data.records[0];

    expect(record.passwordHash).toBeUndefined();
    expect(record.password).toBeUndefined();
  });

  test("should support pagination", async () => {
    // Add 5 networks
    for (let i = 0; i < 5; i++) {
      await request(app).post("/api/networks").send({ ssid: `Network${i}` });
    }

    const response = await request(app)
      .get("/api/networks?page=1&limit=3")
      .expect(200);

    expect(response.body.data.records).toHaveLength(3);
    expect(response.body.data.total).toBe(5);
    expect(response.body.data.page).toBe(1);
    expect(response.body.data.limit).toBe(3);
  });
});

describe("POST /api/ai/validate", () => {
  test("should validate WiFi data and return result", async () => {
    const response = await request(app)
      .post("/api/ai/validate")
      .send({ ssid: "TestWiFi", password: "SecurePass123!" })
      .expect(200);

    expect(response.body.ok).toBe(true);
    expect(response.body.data).toHaveProperty("validated");
    expect(response.body.data).toHaveProperty("confidence");
    expect(response.body.data).toHaveProperty("suggestion");
    expect(response.body.data).toHaveProperty("flags");
    expect(response.body.data).toHaveProperty("shouldAutoConnect");
  });

  test("should return 400 when no input provided", async () => {
    const response = await request(app)
      .post("/api/ai/validate")
      .send({})
      .expect(400);

    expect(response.body.ok).toBe(false);
  });

  test("should return 400 for non-string ssid", async () => {
    const response = await request(app)
      .post("/api/ai/validate")
      .send({ ssid: 123 })
      .expect(400);

    expect(response.body.ok).toBe(false);
  });
});

describe("GET /health", () => {
  test("should return healthy status", async () => {
    const response = await request(app).get("/health").expect(200);

    expect(response.body.ok).toBe(true);
    expect(response.body.service).toBe("smartwificonnect-server");
    expect(response.body).toHaveProperty("uptimeSeconds");
    expect(response.body).toHaveProperty("timestamp");
  });
});

describe("404 handler", () => {
  test("should return 404 for unknown endpoints", async () => {
    const response = await request(app)
      .get("/api/unknown-endpoint-xyz")
      .expect(404);

    expect(response.body.ok).toBe(false);
  });
});
