const express = require("express");
const cors = require("cors");
const crypto = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");
const rateLimit = require("express-rate-limit");
const { parseOcrWifiData } = require("./ocrParser");
const { validateWifiCandidate } = require("./aiValidator");

require("dotenv").config();

// ── File-backed network store ─────────────────────────────────────────────────
const DATA_DIR = path.join(__dirname, "..", "data");
const NETWORKS_FILE = path.join(DATA_DIR, "networks.json");

function loadNetworks() {
  try {
    if (!fs.existsSync(DATA_DIR)) fs.mkdirSync(DATA_DIR, { recursive: true });
    if (!fs.existsSync(NETWORKS_FILE)) return [];
    const raw = fs.readFileSync(NETWORKS_FILE, "utf8");
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function persistNetworks() {
  try {
    if (!fs.existsSync(DATA_DIR)) fs.mkdirSync(DATA_DIR, { recursive: true });
    fs.writeFileSync(NETWORKS_FILE, JSON.stringify(savedNetworks, null, 2), "utf8");
  } catch (e) {
    console.error("[SmartWiFiConnect] Failed to persist networks:", e);
  }
}

const savedNetworks = loadNetworks();
console.log(`[SmartWiFiConnect] Loaded ${savedNetworks.length} saved network(s) from disk.`);

const app = express();
const port = Number(process.env.PORT || 8080);
const allowedOrigins = parseAllowedOrigins(process.env.ALLOWED_ORIGINS || process.env.ALLOWED_ORIGIN);

app.set("trust proxy", 1);

// ── Security headers ─────────────────────────────────────────────────────────
app.use((_req, res, next) => {
  res.setHeader("X-Content-Type-Options", "nosniff");
  res.setHeader("X-Frame-Options", "DENY");
  if (process.env.NODE_ENV === "production") {
    res.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
  }
  next();
});

app.use(cors(buildCorsOptions(allowedOrigins)));
app.use(express.json({ limit: "64kb" }));

// ── Rate limiting ─────────────────────────────────────────────────────────────
const generalLimiter = rateLimit({
  windowMs: 60 * 1000,       // 1 minute
  max: 120,                   // 120 req/min per IP (generous for a local app)
  standardHeaders: "draft-8",
  legacyHeaders: false,
  message: { ok: false, error: "Too many requests, please try again later." },
});

const heavyLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 30,                    // OCR/AI/fuzzy are CPU-heavy — tighter cap
  standardHeaders: "draft-8",
  legacyHeaders: false,
  message: { ok: false, error: "Rate limit exceeded for this endpoint." },
});

app.use(generalLimiter);

// ── Optional Bearer-token auth ────────────────────────────────────────────────
// Set API_SECRET_TOKEN in .env to enable. Leave unset to skip (dev/local mode).
const SECRET_TOKEN = process.env.API_SECRET_TOKEN || "";
function requireAuth(req, res, next) {
  if (!SECRET_TOKEN) return next();              // disabled — dev/local mode
  const header = req.headers["authorization"] || "";
  const token = header.startsWith("Bearer ") ? header.slice(7).trim() : "";
  // Constant-time comparison to prevent timing attacks
  if (!token || !timingSafeEqual(token, SECRET_TOKEN)) {
    return res.status(401).json({ ok: false, error: "Unauthorized" });
  }
  next();
}

function timingSafeEqual(a, b) {
  try {
    return crypto.timingSafeEqual(Buffer.from(a), Buffer.from(b));
  } catch {
    return false;
  }
}

app.get("/health", (_req, res) => {
  res.status(200).json({
    ok: true,
    service: "smartwificonnect-server",
    uptimeSeconds: Math.round(process.uptime()),
    timestamp: new Date().toISOString(),
  });
});

app.post("/api/v1/ocr/parse", requireAuth, heavyLimiter, (req, res) => {
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

app.post("/api/ai/validate", requireAuth, heavyLimiter, (req, res) => {
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

// ── POST /api/v1/ssid/fuzzy-match ────────────────────────────────────────────
app.post("/api/v1/ssid/fuzzy-match", requireAuth, heavyLimiter, (req, res) => {
  try {
    const { ocrSsid, nearbyNetworks } = req.body || {};

    if (typeof ocrSsid !== "string" || !ocrSsid.trim()) {
      return res.status(400).json({ ok: false, error: "Field 'ocrSsid' must be a non-empty string" });
    }
    if (!Array.isArray(nearbyNetworks)) {
      return res.status(400).json({ ok: false, error: "Field 'nearbyNetworks' must be an array" });
    }

    const trimmedOcrSsid = ocrSsid.trim();
    if (trimmedOcrSsid.length > 64) {
      return res.status(400).json({ ok: false, error: "Field 'ocrSsid' is too long (max 64 chars)" });
    }

    const sanitizedNearby = nearbyNetworks.slice(0, 64);

    const normalizedOcrSsid = trimmedOcrSsid;
    const scored = sanitizedNearby
      .filter((n) => n && typeof n.ssid === "string" && n.ssid.trim() && n.ssid.trim().length <= 64)
      .map((n) => ({
        ssid: n.ssid.trim(),
        signalLevel: typeof n.signalLevel === "number" ? n.signalLevel : 0,
        score: Number(fuzzyScore(normalizedOcrSsid, n.ssid.trim()).toFixed(4)),
      }))
      .filter((n) => n.score > 0.3)
      .sort((a, b) => b.score - a.score);

    const best = scored[0] || null;

    return res.status(200).json({
      ok: true,
      data: {
        ocrSsid: normalizedOcrSsid,
        bestMatch: best ? best.ssid : null,
        score: best ? best.score : null,
        matches: scored,
      },
    });
  } catch (error) {
    return nextError(res, error);
  }
});

// ── POST /api/networks ────────────────────────────────────────────────────────
app.post("/api/networks", requireAuth, (req, res) => {
  try {
    const { ssid, password, security, sourceFormat, confidence, connectedAtEpochMs } = req.body || {};

    if (typeof ssid !== "string" || !ssid.trim()) {
      return res.status(400).json({ ok: false, error: "Field 'ssid' must be a non-empty string" });
    }
    if (ssid.trim().length > 64) {
      return res.status(400).json({ ok: false, error: "Field 'ssid' is too long (max 64 chars)" });
    }
    if (password !== undefined && password !== null && typeof password !== "string") {
      return res.status(400).json({ ok: false, error: "Field 'password' must be a string when provided" });
    }
    if (typeof password === "string" && password.length > 128) {
      return res.status(400).json({ ok: false, error: "Field 'password' is too long (max 128 chars)" });
    }
    if (connectedAtEpochMs !== undefined && typeof connectedAtEpochMs !== "number") {
      return res.status(400).json({ ok: false, error: "Field 'connectedAtEpochMs' must be a number when provided" });
    }

    const passwordHash = password && password.trim()
      ? hashPassword(password.trim())
      : null;

    const record = {
      id: Date.now().toString(36) + Math.random().toString(36).slice(2, 6),
      ssid: ssid.trim(),
      passwordHash,
      security: typeof security === "string" ? security.trim() || null : null,
      sourceFormat: typeof sourceFormat === "string" ? sourceFormat.trim() || null : null,
      confidence: typeof confidence === "number" ? confidence : null,
      connectedAtEpochMs: typeof connectedAtEpochMs === "number" ? connectedAtEpochMs : Date.now(),
      savedAtEpochMs: Date.now(),
    };

    savedNetworks.push(record);
    persistNetworks();

    return res.status(201).json({
      ok: true,
      data: {
        id: record.id,
        ssid: record.ssid,
        security: record.security,
        sourceFormat: record.sourceFormat,
        confidence: record.confidence,
        connectedAtEpochMs: record.connectedAtEpochMs,
        savedAtEpochMs: record.savedAtEpochMs,
      },
    });
  } catch (error) {
    return nextError(res, error);
  }
});

// ── GET /api/networks ─────────────────────────────────────────────────────────
app.get("/api/networks", requireAuth, (req, res) => {
  try {
    const page = Math.max(1, parseInt(req.query.page, 10) || 1);
    const limit = Math.min(100, Math.max(1, parseInt(req.query.limit, 10) || 20));
    const offset = (page - 1) * limit;

    const sorted = savedNetworks
      .slice()
      .sort((a, b) => b.savedAtEpochMs - a.savedAtEpochMs);

    const records = sorted
      .slice(offset, offset + limit)
      .map(({ passwordHash: _ph, ...rest }) => rest); // never expose password hash

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

app.use((_req, res) => {
  res.status(404).json({ ok: false, error: "Endpoint not found" });
});

app.use((err, _req, res, _next) => {
  return nextError(res, err);
});

// Only start listening when run directly, not when imported for testing
if (require.main === module) {
  app.listen(port, () => {
    console.log(`[SmartWiFiConnect] API running on http://localhost:${port}`);
  });

  // ── Process-level safety nets ─────────────────────────────────────────────────
  // Prevent the server from crashing silently on uncaught async rejections or
  // unexpected synchronous throws that escape all try/catch boundaries.
  process.on("unhandledRejection", (reason) => {
    console.error("[SmartWiFiConnect] Unhandled promise rejection:", reason);
  });

  process.on("uncaughtException", (err) => {
    console.error("[SmartWiFiConnect] Uncaught exception — server will stay up:", err);
  });
}

module.exports = { app, savedNetworks };

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

// ── Password hashing (SHA-256 + random salt, no plain text stored) ───────────
function hashPassword(plain) {
  const salt = crypto.randomBytes(16).toString("hex");
  const hash = crypto.createHash("sha256").update(salt + plain).digest("hex");
  return `sha256:${salt}:${hash}`;
}

// ── Fuzzy SSID scoring (normalised edit distance + common prefix bonus) ───────
function fuzzyScore(a, b) {
  if (!a || !b) return 0;
  const na = a.toLowerCase().replace(/[\s_\-]/g, "");
  const nb = b.toLowerCase().replace(/[\s_\-]/g, "");
  if (na === nb) return 1;

  const maxLen = Math.max(na.length, nb.length);
  if (maxLen === 0) return 1;

  const dist = levenshtein(na, nb);
  const editScore = 1 - dist / maxLen;

  // Common prefix bonus (up to 0.1)
  let prefix = 0;
  for (let i = 0; i < Math.min(na.length, nb.length); i++) {
    if (na[i] === nb[i]) prefix++;
    else break;
  }
  const prefixBonus = Math.min(prefix / maxLen, 0.1);

  return Math.min(editScore + prefixBonus, 1);
}

function levenshtein(a, b) {
  const m = a.length;
  const n = b.length;
  const dp = Array.from({ length: m + 1 }, (_, i) => {
    const row = new Array(n + 1).fill(0);
    row[0] = i;
    return row;
  });
  for (let j = 0; j <= n; j++) dp[0][j] = j;
  for (let i = 1; i <= m; i++) {
    for (let j = 1; j <= n; j++) {
      dp[i][j] = a[i - 1] === b[j - 1]
        ? dp[i - 1][j - 1]
        : 1 + Math.min(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1]);
    }
  }
  return dp[m][n];
}
