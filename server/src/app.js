const express = require("express");
const cors = require("cors");
const { parseOcrWifiData } = require("./ocrParser");
const { validateWifiCandidate } = require("./aiValidator");

const allowedOrigins = parseAllowedOrigins(
  process.env.ALLOWED_ORIGINS || process.env.ALLOWED_ORIGIN,
);

const app = express();

app.use(cors(buildCorsOptions(allowedOrigins)));
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
  try {
    const { ocrText } = req.body || {};
    if (typeof ocrText !== "string") {
      return res.status(400).json({
        ok: false,
        error: "Field 'ocrText' must be a string",
      });
    }

    const normalizedOcrText = normalizeOptionalString(ocrText);
    if (!normalizedOcrText) {
      return res.status(400).json({
        ok: false,
        error: "Field 'ocrText' must not be empty",
      });
    }

    const result = parseOcrWifiData(normalizedOcrText);
    if (!result.ok) {
      return res.status(422).json(result);
    }

    return res.status(200).json(result);
  } catch (error) {
    return nextError(res, error);
  }
});

app.post("/api/ai/validate", (req, res) => {
  try {
    const { ssid, password, ocrText } = req.body || {};
    const invalidSsid = ssid !== undefined && typeof ssid !== "string";
    const invalidPassword = password !== undefined && typeof password !== "string";
    const invalidOcrText = ocrText !== undefined && typeof ocrText !== "string";

    if (invalidSsid || invalidPassword || invalidOcrText) {
      return res.status(400).json({
        ok: false,
        error: "Fields 'ssid', 'password', and 'ocrText' must be strings when provided",
      });
    }

    const normalizedInput = {
      ssid: normalizeOptionalString(ssid),
      password: normalizeOptionalString(password),
      ocrText: normalizeOptionalString(ocrText),
    };

    if (!Object.values(normalizedInput).some(Boolean)) {
      return res.status(400).json({
        ok: false,
        error: "At least one of 'ssid', 'password', or 'ocrText' is required",
      });
    }

    const result = validateWifiCandidate(normalizedInput);
    return res.status(200).json({
      ok: true,
      input: {
        ssid: normalizedInput.ssid,
        password: normalizedInput.password,
        ocrText: normalizedInput.ocrText,
      },
      data: result,
      timestamp: new Date().toISOString(),
    });
  } catch (error) {
    return nextError(res, error);
  }
});

app.use((_req, res) => {
  res.status(404).json({ ok: false, error: "Endpoint not found" });
});

app.use((err, _req, res, _next) => {
  return nextError(res, err);
});

function normalizeOptionalString(value) {
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

function parseAllowedOrigins(rawValue) {
  if (!rawValue) return [];
  return String(rawValue)
    .split(",")
    .map((origin) => origin.trim())
    .filter(Boolean);
}

function buildCorsOptions(origins) {
  if (origins.length === 0 || origins.includes("*")) {
    return { origin: true };
  }

  const allowSet = new Set(origins);
  return {
    origin(origin, callback) {
      if (!origin || allowSet.has(origin)) {
        return callback(null, true);
      }
      return callback(new Error("Origin not allowed by CORS"));
    },
  };
}

function nextError(res, error) {
  console.error("[SmartWiFiConnect] Unexpected error:", error);
  return res.status(500).json({
    ok: false,
    error: "Internal server error",
  });
}

module.exports = app;
