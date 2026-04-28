const WIFI_QR_REGEX = /^WIFI:/i;
const WIFI_QR_INLINE_REGEX = /WIFI:[^\n]*?;;/i;
const WIFI_QR_LINE_REGEX = /WIFI:[^\n]*/i;
const SSID_VALUE_REGEX =
  /^\s*(?:ssid|wifi(?:\s*name)?|network\s*name|ten\s*wifi|t[eê]n\s*m[aạ]ng)\s*[:=-]\s*(.*)$/i;
const PASSWORD_VALUE_REGEX =
  /^\s*(?:password|pass\s*word|pass|pwd|mat\s*khau|m[aạ]t\s*kh[aẩ]u|mk)\s*[:=-]\s*(.*)$/i;
const SSID_LABEL_ONLY_REGEX =
  /^\s*(?:ssid|wifi(?:\s*name)?|network\s*name|ten\s*wifi|t[eê]n\s*m[aạ]ng)\s*[:=-]?\s*$/i;
const PASSWORD_LABEL_ONLY_REGEX =
  /^(?:password|pass\s*word|pass|pwd|mat\s*khau|m[aạ]t\s*kh[aẩ]u|mk)\s*[:=-]?\s*$/i;
const PASSWORD_LABEL_REGEX =
  /^\s*(?:password|pass\s*word|pass|pwd|mat\s*khau|m[aạ]t\s*kh[aẩ]u|mk)\b/i;
const SSID_LABEL_REGEX =
  /^\s*(?:ssid|wifi(?:\s*name)?|network\s*name|ten\s*wifi|t[eê]n\s*m[aạ]ng)\b/i;

function cleanLine(line) {
  return line
    .replace(/[\u200B-\u200D\uFEFF]/g, "")
    .replace(/[|]/g, "I")
    .replace(/[“”]/g, "\"")
    .replace(/[‘’]/g, "'")
    .trim();
}

function sanitizeCandidate(value) {
  return String(value || "")
    .replace(/^[`"'[\](){}<>]+/, "")
    .replace(/[`"'[\](){}<>]+$/, "")
    .replace(/\s{2,}/g, " ")
    .trim();
}

function looksLikeUrl(value) {
  return /(https?:\/\/|www\.|\.com\b|\.net\b|\.org\b)/i.test(String(value || ""));
}

function isLikelyNoiseLine(value) {
  const text = String(value || "").trim();
  if (!text) return true;
  if (looksLikeUrl(text)) return true;
  return /(free\s*wifi|mien\s*phi|hotline|email|username|dang\s*nhap|login|welcome|xin\s*chao|cam\s*on|scan|qr)/i.test(
    text,
  );
}

function extractValueAfterLabel(line, valueRegex, cutRegex) {
  const match = cleanLine(line).match(valueRegex);
  if (!match) return null;
  const rawValue = String(match[1] || "");
  const value = cutRegex ? rawValue.replace(cutRegex, "") : rawValue;
  const cleaned = sanitizeCandidate(value);
  return cleaned || null;
}

function pickNextUsefulLine(lines, fromIndex, forPassword) {
  for (let i = fromIndex; i < Math.min(lines.length, fromIndex + 3); i += 1) {
    const candidate = sanitizeCandidate(lines[i]);
    if (!candidate) continue;
    if (SSID_LABEL_ONLY_REGEX.test(candidate) || PASSWORD_LABEL_ONLY_REGEX.test(candidate)) continue;
    if (isLikelyNoiseLine(candidate)) continue;
    if (forPassword) {
      if (looksLikePassword(candidate)) return stripPasswordPrefix(candidate) || candidate;
      continue;
    }
    if (candidate.length <= 32 && !looksLikePassword(candidate)) return candidate;
  }
  return null;
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
  let qrText = text;

  if (!WIFI_QR_REGEX.test(qrText)) {
    const embedded = text.match(WIFI_QR_INLINE_REGEX) || text.match(WIFI_QR_LINE_REGEX);
    qrText = embedded ? embedded[0] : "";
  }

  if (!WIFI_QR_REGEX.test(qrText)) return null;
  if (!/;\s*[SPT]\s*:/i.test(qrText)) return null;

  const payload = qrText.replace(/^WIFI:/i, "");
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

  if (!ssid && !password) return null;

  return {
    ssid,
    password,
    security,
    sourceFormat: "wifi_qr",
    confidence: ssid || password ? 0.98 : 0.5,
  };
}

function extractFromLabeledLines(lines) {
  let ssid = null;
  let password = null;

  for (let i = 0; i < lines.length; i += 1) {
    const line = lines[i];
    const cleaned = cleanLine(line);

    if (!ssid) {
      ssid =
        extractValueAfterLabel(cleaned, SSID_VALUE_REGEX, PASSWORD_VALUE_REGEX) ||
        (SSID_LABEL_ONLY_REGEX.test(cleaned) ? pickNextUsefulLine(lines, i + 1, false) : null);
    }

    if (!password) {
      const extractedPassword =
        extractValueAfterLabel(cleaned, PASSWORD_VALUE_REGEX, SSID_VALUE_REGEX) ||
        (PASSWORD_LABEL_ONLY_REGEX.test(cleaned) ? pickNextUsefulLine(lines, i + 1, true) : null);
      password = extractedPassword ? stripPasswordPrefix(extractedPassword) || extractedPassword : null;
    }

    if (ssid && password) break;
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
    .replace(/^(password|pass|pass\s*word|mat\s*khau|m[aạ]t\s*kh[aẩ]u|mk)\s*[:=-]\s*/i, "")
    .trim();
}

function stripSsidPrefix(text) {
  return String(text || "")
    .replace(/^(ssid|wifi(?:\s*name)?|network\s*name|ten\s*wifi|t[eê]n\s*m[aạ]ng)\s*[:=-]\s*/i, "")
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

  // Two-line parsing is strict by design; large OCR blocks should be handled by labeled/heuristic parser.
  if (sanitizedLines.length > 3) return null;
  if (sanitizedLines.length === 0) return null;

  const first = stripSsidPrefix(sanitizedLines[0]) || sanitizedLines[0];
  const second = sanitizedLines[1] || "";
  const third = sanitizedLines[2] || "";

  if (!first) return null;

  if (sanitizedLines.length === 1) {
    return {
      ssid: null,
      password: stripPasswordPrefix(first) || first,
      sourceFormat: "single_line_password",
      confidence: 0.9,
    };
  }

  let normalizedPassword = stripPasswordPrefix(second) || second;
  if (PASSWORD_LABEL_ONLY_REGEX.test(second) && third) {
    normalizedPassword = stripPasswordPrefix(third) || third;
  }
  if (PASSWORD_LABEL_ONLY_REGEX.test(normalizedPassword)) {
    normalizedPassword = "";
  }
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
  const value = sanitizeCandidate(text);
  if (!value) return false;
  if (looksLikeUrl(value)) return false;
  if (value.length < 8 || value.length > 63) return false;
  if (/\s{2,}/.test(value)) return false;
  return /[A-Za-z0-9!@#$%^&*()_+\-=\[\]{};':"\\|,.<>/?`~]/.test(value);
}

function scorePasswordCandidate(line) {
  const cleaned = sanitizeCandidate(line);
  if (!cleaned) return null;

  let score = 0;
  let value = cleaned;
  if (PASSWORD_LABEL_REGEX.test(cleaned)) {
    score += 4;
    value = stripPasswordPrefix(cleaned) || cleaned;
  }

  if (looksLikePassword(value)) score += 3;
  if (!/\s/.test(value)) score += 1;
  if (/[A-Za-z]/.test(value) && /\d/.test(value)) score += 1;
  if (value.length > 20) score -= 0.5;
  if (isLikelyNoiseLine(value)) score -= 3;

  return { value, score };
}

function scoreSsidCandidate(line) {
  const cleaned = sanitizeCandidate(line);
  if (!cleaned) return null;

  let score = 0;
  let value = cleaned;
  if (SSID_LABEL_REGEX.test(cleaned)) {
    score += 4;
    value = cleaned.replace(SSID_LABEL_REGEX, "").replace(/^[\s:=-]+/, "").trim();
  }

  if (!value || value.length > 32) return null;
  if (looksLikeUrl(value)) return null;
  if (looksLikePassword(value)) score -= 2;
  if (!PASSWORD_LABEL_REGEX.test(value)) score += 1;
  if (!isLikelyNoiseLine(value)) score += 1;

  return { value, score };
}

function extractHeuristic(lines) {
  const filtered = lines
    .map((line) => sanitizeCandidate(line))
    .filter(Boolean)
    .filter((line) => !SSID_LABEL_ONLY_REGEX.test(line) && !PASSWORD_LABEL_ONLY_REGEX.test(line));

  if (filtered.length === 0) return null;

  let bestPassword = null;
  let bestSsid = null;

  for (const line of filtered) {
    const passwordScored = scorePasswordCandidate(line);
    if (passwordScored && (!bestPassword || passwordScored.score > bestPassword.score)) {
      bestPassword = passwordScored;
    }

    const ssidScored = scoreSsidCandidate(line);
    if (ssidScored && (!bestSsid || ssidScored.score > bestSsid.score)) {
      bestSsid = ssidScored;
    }
  }

  const password = bestPassword && bestPassword.score >= 2 ? bestPassword.value : null;
  const ssid = bestSsid && bestSsid.score >= 1 ? bestSsid.value : null;

  if (!ssid && !password) return null;

  return {
    ssid,
    password,
    sourceFormat: "heuristic",
    confidence: password && ssid ? 0.78 : password ? 0.66 : 0.52,
  };
}

function computeCandidateScore(candidate) {
  if (!candidate) return -1;
  let score = Number(candidate.confidence || 0);
  if (candidate.ssid) score += 0.35;
  if (candidate.password) score += 0.35;
  if (candidate.sourceFormat === "labeled_text") score += 0.05;
  if (candidate.password && !candidate.ssid) score -= 0.08;
  return score;
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

  const labeled = extractFromLabeledLines(lines);
  const twoLine = extractTwoLineSsidPassword(lines);

  const heuristic = extractHeuristic(lines);
  const candidates = [labeled, twoLine, heuristic].filter(Boolean);
  if (candidates.length > 0) {
    const best = candidates.sort((a, b) => computeCandidateScore(b) - computeCandidateScore(a))[0];
    return {
      ok: true,
      data: {
        ...best,
        passwordOnly: Boolean(best.password && !best.ssid),
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
