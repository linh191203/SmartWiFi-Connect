const express = require("express");
const cors = require("cors");
const crypto = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");
const { parseOcrWifiData } = require("./ocrParser");
const { validateWifiCandidate } = require("./aiValidator");

require("dotenv").config();

const app = express();
const port = Number(process.env.PORT || 8080);
const allowedOrigins = parseAllowedOrigins(process.env.ALLOWED_ORIGINS || process.env.ALLOWED_ORIGIN);
const networksFile = process.env.NETWORKS_FILE
  ? path.resolve(process.env.NETWORKS_FILE)
  : path.join(__dirname, "..", "data", "networks.json");
const savedNetworks = loadSavedNetworks();
const otpStore = new Map();
const apiAuthToken = normalizeOptionalString(process.env.SMARTWIFI_API_AUTH_TOKEN || process.env.API_AUTH_TOKEN);

app.set("trust proxy", parseTrustProxy(process.env.TRUST_PROXY));
app.use(addSecurityHeaders);
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

app.post("/api/v1/ocr/parse", requireApiAuth, (req, res) => {
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

app.post("/api/v1/ssid/fuzzy-match", requireApiAuth, (req, res) => {
  try {
    const { ocrSsid, nearbyNetworks } = req.body || {};
    const normalizedOcrSsid = normalizeOptionalString(ocrSsid);

    if (!normalizedOcrSsid) {
      return res.status(400).json({
        ok: false,
        error: "Field 'ocrSsid' must be a non-empty string",
      });
    }

    if (!Array.isArray(nearbyNetworks)) {
      return res.status(400).json({
        ok: false,
        error: "Field 'nearbyNetworks' must be an array",
      });
    }

    const matches = nearbyNetworks
      .map((network) => {
        const ssid = normalizeOptionalString(network?.ssid);
        if (!ssid) return null;
        return {
          ssid,
          signalLevel: normalizeSignalLevel(network?.signalLevel),
          score: scoreSsidSimilarity(normalizedOcrSsid, ssid),
        };
      })
      .filter(Boolean)
      .sort((a, b) => {
        if (b.score !== a.score) return b.score - a.score;
        return (b.signalLevel || 0) - (a.signalLevel || 0);
      });

    const best = matches[0] || null;
    return res.status(200).json({
      ok: true,
      data: {
        ocrSsid: normalizedOcrSsid,
        bestMatch: best?.ssid || null,
        score: best?.score || null,
        matches,
      },
    });
  } catch (error) {
    return nextError(res, error);
  }
});

app.post("/api/networks", requireApiAuth, (req, res) => {
  try {
    const validation = validateNetworkRequest(req.body || {});
    if (!validation.ok) {
      return res.status(400).json({
        ok: false,
        error: validation.error,
      });
    }

    const now = Date.now();
    const request = validation.data;
    const record = {
      id: createRecordId(),
      ssid: request.ssid,
      passwordHash: request.password ? hashPassword(request.password) : null,
      security: request.security,
      sourceFormat: request.sourceFormat,
      confidence: request.confidence,
      connectedAtEpochMs: request.connectedAtEpochMs || now,
      savedAtEpochMs: now,
    };

    savedNetworks.push(record);
    saveNetworksToDisk(savedNetworks);

    return res.status(201).json({
      ok: true,
      data: sanitizeNetworkRecord(record),
    });
  } catch (error) {
    return nextError(res, error);
  }
});

app.get("/api/networks", requireApiAuth, (req, res) => {
  try {
    const page = normalizePositiveInteger(req.query.page, 1, 1, 10_000);
    const limit = normalizePositiveInteger(req.query.limit, 20, 1, 100);
    const start = (page - 1) * limit;
    const records = savedNetworks.slice(start, start + limit).map(sanitizeNetworkRecord);

    return res.status(200).json({
      ok: true,
      data: {
        records,
        total: savedNetworks.length,
        page,
        limit,
      },
    });
  } catch (error) {
    return nextError(res, error);
  }
});

app.post("/api/auth/request-otp", requireApiAuth, (req, res) => {
  try {
    const email = normalizeOptionalString(req.body?.email);
    if (!email || !email.includes("@")) {
      return res.status(400).json({
        ok: false,
        error: "Field 'email' must be a valid email address",
      });
    }

    const code = process.env.OTP_DEV_CODE || "123456";
    otpStore.set(email.toLowerCase(), {
      code,
      expiresAtEpochMs: Date.now() + 10 * 60 * 1000,
    });

    return res.status(200).json({
      ok: true,
      data: null,
    });
  } catch (error) {
    return nextError(res, error);
  }
});

app.post("/api/auth/verify-otp", requireApiAuth, (req, res) => {
  try {
    const email = normalizeOptionalString(req.body?.email);
    const code = normalizeOptionalString(req.body?.code);

    if (!email || !code) {
      return res.status(400).json({
        ok: false,
        error: "Fields 'email' and 'code' are required",
      });
    }

    const stored = otpStore.get(email.toLowerCase());
    const devCode = process.env.OTP_DEV_CODE || "123456";
    const valid =
      (stored && stored.expiresAtEpochMs >= Date.now() && stored.code === code) ||
      (process.env.NODE_ENV !== "production" && code === devCode);

    if (!valid) {
      return res.status(400).json({
        ok: false,
        error: "Invalid or expired OTP code",
      });
    }

    otpStore.delete(email.toLowerCase());
    return res.status(200).json({
      ok: true,
      data: null,
    });
  } catch (error) {
    return nextError(res, error);
  }
});

app.post("/api/ai/validate", requireApiAuth, (req, res) => {
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

if (require.main === module) {
  app.listen(port, () => {
    console.log(`[SmartWiFiConnect] API running on http://localhost:${port}`);
  });
}

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

function addSecurityHeaders(_req, res, next) {
  res.setHeader("X-Content-Type-Options", "nosniff");
  res.setHeader("X-Frame-Options", "DENY");
  res.setHeader("Referrer-Policy", "no-referrer");
  if (process.env.NODE_ENV === "production") {
    res.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
  }
  next();
}

function requireApiAuth(req, res, next) {
  if (!apiAuthToken) return next();

  const header = req.headers.authorization || "";
  const token = header.startsWith("Bearer ") ? header.slice("Bearer ".length).trim() : "";
  if (!token || !timingSafeEqual(token, apiAuthToken)) {
    return res.status(401).json({ ok: false, error: "Unauthorized" });
  }

  return next();
}

function timingSafeEqual(left, right) {
  const leftBuffer = Buffer.from(left);
  const rightBuffer = Buffer.from(right);
  if (leftBuffer.length !== rightBuffer.length) return false;
  return crypto.timingSafeEqual(leftBuffer, rightBuffer);
}

function parseTrustProxy(rawValue) {
  const value = normalizeOptionalString(rawValue);
  if (!value || value.toLowerCase() === "false") return false;
  if (value.toLowerCase() === "true") return true;
  if (/^\d+$/.test(value)) return Number(value);
  return value.split(",").map((entry) => entry.trim()).filter(Boolean);
}

function loadSavedNetworks() {
  try {
    if (!fs.existsSync(networksFile)) return [];
    const raw = fs.readFileSync(networksFile, "utf8");
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch (error) {
    console.warn("[SmartWiFiConnect] Could not load saved networks:", error.message);
    return [];
  }
}

function saveNetworksToDisk(records) {
  try {
    fs.mkdirSync(path.dirname(networksFile), { recursive: true });
    fs.writeFileSync(networksFile, JSON.stringify(records, null, 2), "utf8");
  } catch (error) {
    console.warn("[SmartWiFiConnect] Could not persist saved networks:", error.message);
  }
}

function validateNetworkRequest(body) {
  const ssid = normalizeOptionalString(body.ssid);
  if (!ssid) return { ok: false, error: "Field 'ssid' must be a non-empty string" };
  if (ssid.length > 64) return { ok: false, error: "Field 'ssid' must be 64 characters or less" };

  if (body.password !== undefined && body.password !== null && typeof body.password !== "string") {
    return { ok: false, error: "Field 'password' must be a string when provided" };
  }

  const password = normalizeNullableString(body.password);
  if (password && password.length > 128) {
    return { ok: false, error: "Field 'password' must be 128 characters or less" };
  }

  const connectedAtEpochMs = body.connectedAtEpochMs;
  if (
    connectedAtEpochMs !== undefined &&
    connectedAtEpochMs !== null &&
    (!Number.isFinite(connectedAtEpochMs) || connectedAtEpochMs <= 0)
  ) {
    return { ok: false, error: "Field 'connectedAtEpochMs' must be a positive number" };
  }

  const confidence = body.confidence;
  if (
    confidence !== undefined &&
    confidence !== null &&
    (!Number.isFinite(confidence) || confidence < 0 || confidence > 1)
  ) {
    return { ok: false, error: "Field 'confidence' must be between 0 and 1" };
  }

  return {
    ok: true,
    data: {
      ssid,
      password,
      security: normalizeNullableString(body.security),
      sourceFormat: normalizeNullableString(body.sourceFormat),
      confidence: confidence ?? null,
      connectedAtEpochMs: connectedAtEpochMs ?? null,
    },
  };
}

function normalizeNullableString(value) {
  if (value === undefined || value === null) return null;
  if (typeof value !== "string") return null;
  return value.trim() || null;
}

function normalizePositiveInteger(rawValue, fallback, min, max) {
  const parsed = Number.parseInt(rawValue, 10);
  if (!Number.isFinite(parsed)) return fallback;
  return Math.min(Math.max(parsed, min), max);
}

function normalizeSignalLevel(value) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return 0;
  return Math.min(Math.max(Math.round(parsed), 0), 4);
}

function sanitizeNetworkRecord(record) {
  return {
    id: record.id,
    ssid: record.ssid,
    security: record.security || null,
    sourceFormat: record.sourceFormat || null,
    confidence: record.confidence ?? null,
    connectedAtEpochMs: record.connectedAtEpochMs,
    savedAtEpochMs: record.savedAtEpochMs,
  };
}

function createRecordId() {
  return `${Date.now().toString(36)}${crypto.randomBytes(3).toString("hex")}`;
}

function hashPassword(password) {
  const pepper = process.env.PASSWORD_HASH_PEPPER || "smartwifi-local";
  const salt = crypto.randomBytes(16).toString("hex");
  const digest = crypto.createHash("sha256").update(`${salt}:${pepper}:${password}`).digest("hex");
  return `sha256:${salt}:${digest}`;
}

function scoreSsidSimilarity(left, right) {
  const a = normalizeForFuzzy(left);
  const b = normalizeForFuzzy(right);
  if (!a || !b) return 0;
  if (a === b) return 1;
  if (a.includes(b) || b.includes(a)) return 0.92;

  const distance = levenshteinDistance(a, b);
  const longest = Math.max(a.length, b.length);
  return Number(Math.max(0, 1 - distance / longest).toFixed(2));
}

function normalizeForFuzzy(value) {
  return String(value || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "");
}

function levenshteinDistance(left, right) {
  const previous = Array.from({ length: right.length + 1 }, (_, index) => index);

  for (let i = 0; i < left.length; i += 1) {
    let last = i;
    previous[0] = i + 1;

    for (let j = 0; j < right.length; j += 1) {
      const old = previous[j + 1];
      const cost = left[i] === right[j] ? 0 : 1;
      previous[j + 1] = Math.min(previous[j + 1] + 1, previous[j] + 1, last + cost);
      last = old;
    }
  }

  return previous[right.length];
}

function nextError(res, error) {
  console.error("[SmartWiFiConnect] Unexpected error:", error);
  return res.status(500).json({
    ok: false,
    error: "Internal server error",
  });
}

module.exports = {
  app,
  savedNetworks,
  parseTrustProxy,
  scoreSsidSimilarity,
};
