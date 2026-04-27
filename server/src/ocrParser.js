const WIFI_QR_REGEX = /^WIFI:/i;

function cleanLine(line) {
  return line
    .replace(/[\u200B-\u200D\uFEFF]/g, "")
    .replace(/[|]/g, "I")
    .trim();
}

function normalizeText(input) {
  return String(input || "")
    .replace(/\r/g, "\n")
    .split("\n")
    .map(cleanLine)
    .filter(Boolean);
}

function parseWifiQrFormat(raw) {
  const text = String(raw || "").trim();
  if (!WIFI_QR_REGEX.test(text)) return null;

  const payload = text.replace(/^WIFI:/i, "");
  const fields = payload.split(/;(?=(?:[^\\]|\\.)*$)/);

  let ssid = null;
  let password = null;
  let security = null;

  for (const field of fields) {
    if (!field) continue;
    const idx = field.indexOf(":");
    if (idx === -1) continue;

    const key = field.slice(0, idx).trim().toUpperCase();
    const value = field.slice(idx + 1).trim().replace(/\\;/g, ";");

    if (key === "S") ssid = value || null;
    if (key === "P") password = value || null;
    if (key === "T") security = value || null;
  }

  return {
    ssid,
    password,
    security,
    sourceFormat: "wifi_qr",
    confidence: ssid || password ? 0.98 : 0.5,
  };
}

function extractFromLabeledLines(lines) {
  const labels = {
    ssid: /(ssid|wifi(?:\s*name)?|network\s*name|ten\s*wifi|t[eê]n\s*m[aạ]ng)/i,
    password: /(password|pass\s*word|pass|mat\s*khau|m[aậạ]t\s*kh[aẩ]u|mk)/i,
  };

  let ssid = null;
  let password = null;

  for (const line of lines) {
    if (!ssid && labels.ssid.test(line)) {
      const v = line.split(/[:=-]/).slice(1).join(":").trim();
      if (v) ssid = v;
    }

    if (!password && labels.password.test(line)) {
      const v = line.split(/[:=-]/).slice(1).join(":").trim();
      if (v) password = v;
    }
  }

  if (!ssid && !password) return null;

  return {
    ssid,
    password,
    sourceFormat: "labeled_text",
    confidence: 0.85,
  };
}

function stripPasswordPrefix(text) {
  return String(text || "")
    .replace(/^(password|pass|pass\s*word|mat\s*khau|m[aậạ]t\s*kh[aẩ]u|mk)\s*[:=-]\s*/i, "")
    .trim();
}

function isWifiHeaderLine(text) {
  const normalized = String(text || "")
    .toLowerCase()
    .replace(/[^a-z]/g, "");
  return normalized === "wifi" || normalized === "wifiname";
}

function extractTwoLineSsidPassword(lines) {
  if (!Array.isArray(lines) || lines.length === 0) return null;

  const sanitizedLines = [...lines].map((line) => String(line || "").trim()).filter(Boolean);
  while (sanitizedLines.length > 0 && isWifiHeaderLine(sanitizedLines[0])) {
    sanitizedLines.shift();
  }

  if (sanitizedLines.length === 0) return null;

  const first = sanitizedLines[0];
  const second = sanitizedLines[1] || "";

  if (!first) return null;

  if (sanitizedLines.length === 1) {
    return {
      ssid: null,
      password: stripPasswordPrefix(first) || first,
      sourceFormat: "single_line_password",
      confidence: 0.9,
    };
  }

  const normalizedPassword = stripPasswordPrefix(second) || second;
  if (!normalizedPassword) {
    return {
      ssid: first,
      password: null,
      sourceFormat: "two_line_ssid_password",
      confidence: 0.72,
    };
  }

  return {
    ssid: first,
    password: normalizedPassword,
    sourceFormat: "two_line_ssid_password",
    confidence: 0.93,
  };
}

function looksLikePassword(text) {
  if (!text) return false;
  if (text.length < 8) return false;
  if (/\s{2,}/.test(text)) return false;
  return /[A-Za-z0-9!@#$%^&*()_+\-=\[\]{};':"\\|,.<>/?`~]/.test(text);
}

function extractHeuristic(lines) {
  const filtered = lines
    .map((line) => line.replace(/^(ssid|password|pass|mat\s*khau|ten\s*wifi)\s*[:=-]\s*/i, "").trim())
    .filter(Boolean);

  if (filtered.length === 0) return null;

  if (filtered.length === 1) {
    const only = filtered[0];
    return {
      ssid: null,
      password: only,
      sourceFormat: "single_line",
      confidence: 0.7,
    };
  }

  let password = null;
  let ssid = null;

  for (const candidate of filtered) {
    if (!password && looksLikePassword(candidate)) {
      password = candidate;
      continue;
    }

    if (!ssid && candidate.length <= 32) {
      ssid = candidate;
    }
  }

  return {
    ssid,
    password,
    sourceFormat: "heuristic",
    confidence: password ? 0.68 : 0.45,
  };
}

function parseOcrWifiData(ocrText) {
  const raw = String(ocrText || "").trim();
  if (!raw) {
    return {
      ok: false,
      error: "ocrText is empty",
    };
  }

  const wifiQr = parseWifiQrFormat(raw);
  if (wifiQr) {
    return {
      ok: true,
      data: {
        ...wifiQr,
        passwordOnly: Boolean(wifiQr.password && !wifiQr.ssid),
      },
    };
  }

  const lines = normalizeText(raw);

  // Prefer labeled extraction only when a labeled SSID was found (has value after delimiter).
  // If labeled only found a password (header-only SSID line like "WiFi" with no value),
  // fall through to two-line extraction which handles SSID-on-next-line patterns.
  const labeled = extractFromLabeledLines(lines);
  if (labeled?.ssid) {
    return {
      ok: true,
      data: {
        ...labeled,
        passwordOnly: false,
      },
    };
  }

  const twoLine = extractTwoLineSsidPassword(lines);
  if (twoLine) {
    return {
      ok: true,
      data: {
        ...twoLine,
        passwordOnly: Boolean(twoLine.password && !twoLine.ssid),
      },
    };
  }

  // Labeled found only a password (no ssid) — still useful
  if (labeled) {
    return {
      ok: true,
      data: {
        ...labeled,
        passwordOnly: Boolean(labeled.password && !labeled.ssid),
      },
    };
  }

  const heuristic = extractHeuristic(lines);
  if (heuristic) {
    return {
      ok: true,
      data: {
        ...heuristic,
        passwordOnly: Boolean(heuristic.password && !heuristic.ssid),
      },
    };
  }

  return {
    ok: false,
    error: "Unable to extract WiFi data from OCR text",
  };
}

module.exports = {
  parseOcrWifiData,
};
