const express = require("express");
const cors = require("cors");
const { parseOcrWifiData } = require("./ocrParser");

require("dotenv").config();

const app = express();
const port = Number(process.env.PORT || 8080);
const allowedOrigin = process.env.ALLOWED_ORIGIN || "*";

app.use(cors({ origin: allowedOrigin }));
app.use(express.json({ limit: "1mb" }));

app.get("/health", (_req, res) => {
  res.status(200).json({
    ok: true,
    service: "smartwificonnect-server",
    uptimeSeconds: Math.round(process.uptime()),
    timestamp: new Date().toISOString(),
  });
});

app.post("/api/v1/ocr/parse", (req, res) => {
  const { ocrText } = req.body || {};

  if (typeof ocrText !== "string") {
    return res.status(400).json({
      ok: false,
      error: "Field 'ocrText' must be a string",
    });
  }

  const result = parseOcrWifiData(ocrText);
  if (!result.ok) {
    return res.status(422).json(result);
  }

  return res.status(200).json(result);
});

app.post("/api/v1/wifi/connect-intent", (req, res) => {
  const { ssid, password } = req.body || {};

  if (!ssid && !password) {
    return res.status(400).json({
      ok: false,
      error: "At least one of 'ssid' or 'password' is required",
    });
  }

  const payload = {
    ssid: ssid || null,
    password: password || null,
    androidHint: {
      note: "Use WifiNetworkSpecifier on Android 10+ for in-app connection flow.",
      qrFormat: `WIFI:T:WPA;S:${ssid || ""};P:${password || ""};;`,
    },
  };

  return res.status(200).json({ ok: true, data: payload });
});

app.get("/api/health", (_req, res) => {
  res.status(200).json({
    ok: true,
    status: "healthy",
    service: "smartwificonnect-server",
    uptimeSeconds: Math.round(process.uptime()),
    timestamp: new Date().toISOString(),
  });
});

app.post("/api/ai/validate", (req, res) => {
  const { ssid, password, ocrText } = req.body || {};

  return res.status(200).json({
    ok: true,
    dummy: true,
    validated: true,
    input: {
      ssid: ssid || null,
      password: password || null,
      ocrText: ocrText || null,
    },
    result: {
      confidence: 0.95,
      suggestion: "Credentials look valid (dummy response)",
      flags: [],
    },
    timestamp: new Date().toISOString(),
  });
});

app.use((_req, res) => {
  res.status(404).json({ ok: false, error: "Endpoint not found" });
});

app.listen(port, () => {
  console.log(`[SmartWiFiConnect] API running on http://localhost:${port}`);
});
