const request = require("supertest");
const app = require("../src/index");

describe("Health API Endpoints", () => {
  it("GET /api/health should return a 200 OK and healthy status", async () => {
    const response = await request(app).get("/api/health");
    
    expect(response.status).toBe(200);
    expect(response.body).toHaveProperty("ok", true);
    expect(response.body).toHaveProperty("status", "healthy");
    expect(response.body).toHaveProperty("service", "smartwificonnect-server");
    expect(response.body).toHaveProperty("timestamp");
    expect(response.body).toHaveProperty("uptimeSeconds");
  });

  it("GET /health should return a 200 OK (legacy endpoint)", async () => {
    const response = await request(app).get("/health");
    
    expect(response.status).toBe(200);
    expect(response.body).toHaveProperty("ok", true);
    expect(response.body).toHaveProperty("service", "smartwificonnect-server");
  });
});
