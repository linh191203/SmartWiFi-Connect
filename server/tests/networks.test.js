const request = require("supertest");
const path = require("path");
const os = require("os");
const app = require("../src/app");

// Use a temporary directory so tests never touch the real data/networks.db
process.env.DB_DIR = path.join(os.tmpdir(), `smartwifi-test-${Date.now()}`);

const { closeDb } = require("../src/networkDb");

afterAll(() => {
  closeDb();
});

describe("GET /api/networks", () => {
  test("returns 200 with empty data by default", async () => {
    const res = await request(app).get("/api/networks");
    expect(res.status).toBe(200);
    expect(res.body.ok).toBe(true);
    expect(Array.isArray(res.body.data)).toBe(true);
  });

  test("respects X-User-Id header", async () => {
    const res = await request(app)
      .get("/api/networks")
      .set("X-User-Id", "user-list-test");
    expect(res.status).toBe(200);
    expect(Array.isArray(res.body.data)).toBe(true);
  });
});

describe("POST /api/networks", () => {
  test("saves a network and returns 201", async () => {
    const res = await request(app)
      .post("/api/networks")
      .set("X-User-Id", "user-save")
      .send({ ssid: "TestNet", password: "pass1234", security: "WPA2", sourceFormat: "wifi_qr", passwordSaved: true });
    expect(res.status).toBe(201);
    expect(res.body.ok).toBe(true);
    expect(res.body.data.ssid).toBe("TestNet");
    expect(res.body.data.passwordSaved).toBe(true);
    expect(typeof res.body.data.id).toBe("number");
  });

  test("upserts on duplicate SSID for same user", async () => {
    const userId = "user-upsert";
    await request(app)
      .post("/api/networks")
      .set("X-User-Id", userId)
      .send({ ssid: "DupNet", password: "first1234", passwordSaved: true });
    await request(app)
      .post("/api/networks")
      .set("X-User-Id", userId)
      .send({ ssid: "DupNet", password: "second5678", passwordSaved: true });

    const listRes = await request(app)
      .get("/api/networks")
      .set("X-User-Id", userId);
    const nets = listRes.body.data.filter((n) => n.ssid === "DupNet");
    expect(nets.length).toBe(1);
    expect(nets[0].password).toBe("second5678");
  });

  test("saves without password when not provided", async () => {
    const res = await request(app)
      .post("/api/networks")
      .set("X-User-Id", "user-nopass")
      .send({ ssid: "OpenNet", passwordSaved: false });
    expect(res.status).toBe(201);
    expect(res.body.data.password).toBeNull();
    expect(res.body.data.passwordSaved).toBe(false);
  });

  test("returns 400 when ssid is missing", async () => {
    const res = await request(app).post("/api/networks").send({ password: "pass1234" });
    expect(res.status).toBe(400);
    expect(res.body.ok).toBe(false);
  });

  test("returns 400 when ssid is empty string", async () => {
    const res = await request(app).post("/api/networks").send({ ssid: "   " });
    expect(res.status).toBe(400);
    expect(res.body.ok).toBe(false);
  });

  test("returns 400 when ssid exceeds 32 characters", async () => {
    const res = await request(app)
      .post("/api/networks")
      .send({ ssid: "a".repeat(33) });
    expect(res.status).toBe(400);
    expect(res.body.ok).toBe(false);
  });

  test("returns 400 when password is too short", async () => {
    const res = await request(app)
      .post("/api/networks")
      .send({ ssid: "Net", password: "short" });
    expect(res.status).toBe(400);
    expect(res.body.ok).toBe(false);
  });

  test("returns 400 when password is not a string", async () => {
    const res = await request(app)
      .post("/api/networks")
      .send({ ssid: "Net", password: 12345 });
    expect(res.status).toBe(400);
    expect(res.body.ok).toBe(false);
  });
});

describe("DELETE /api/networks/:id", () => {
  test("deletes a saved network and returns 200", async () => {
    const userId = "user-delete";
    const saveRes = await request(app)
      .post("/api/networks")
      .set("X-User-Id", userId)
      .send({ ssid: "ToDelete", password: "deletepass1" });
    const id = saveRes.body.data.id;

    const delRes = await request(app)
      .delete(`/api/networks/${id}`)
      .set("X-User-Id", userId);
    expect(delRes.status).toBe(200);
    expect(delRes.body.ok).toBe(true);

    const listRes = await request(app).get("/api/networks").set("X-User-Id", userId);
    expect(listRes.body.data.find((n) => n.id === id)).toBeUndefined();
  });

  test("returns 404 for unknown id", async () => {
    const res = await request(app)
      .delete("/api/networks/999999")
      .set("X-User-Id", "user-del-miss");
    expect(res.status).toBe(404);
    expect(res.body.ok).toBe(false);
  });

  test("returns 400 for invalid id", async () => {
    const res = await request(app).delete("/api/networks/abc");
    expect(res.status).toBe(400);
    expect(res.body.ok).toBe(false);
  });
});

describe("DELETE /api/networks", () => {
  test("clears all networks for user", async () => {
    const userId = "user-clear";
    await request(app)
      .post("/api/networks")
      .set("X-User-Id", userId)
      .send({ ssid: "Net1", password: "netpass123" });
    await request(app)
      .post("/api/networks")
      .set("X-User-Id", userId)
      .send({ ssid: "Net2", password: "netpass456" });

    const clearRes = await request(app)
      .delete("/api/networks")
      .set("X-User-Id", userId);
    expect(clearRes.status).toBe(200);
    expect(clearRes.body.deleted).toBe(2);

    const listRes = await request(app).get("/api/networks").set("X-User-Id", userId);
    expect(listRes.body.data.length).toBe(0);
  });

  test("does not affect other users networks", async () => {
    const userA = "user-clear-A";
    const userB = "user-clear-B";
    await request(app)
      .post("/api/networks")
      .set("X-User-Id", userA)
      .send({ ssid: "ANet", password: "apassword1" });
    await request(app)
      .post("/api/networks")
      .set("X-User-Id", userB)
      .send({ ssid: "BNet", password: "bpassword1" });

    await request(app).delete("/api/networks").set("X-User-Id", userA);

    const listB = await request(app).get("/api/networks").set("X-User-Id", userB);
    expect(listB.body.data.length).toBe(1);
    expect(listB.body.data[0].ssid).toBe("BNet");
  });
});
