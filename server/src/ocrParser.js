const WIFI_QR_REGEX = /^WIFI:/i;
const WIFI_QR_INLINE_REGEX = /WIFI:[^\n]*?;;/i;
const WIFI_QR_LINE_REGEX = /WIFI:[^\n]*/i;
const SSID_VALUE_REGEX =
  /^\s*(?:ssid|wifi(?:\s*name)?|network\s*name|ten\s*wifi|t[eê]n\s*m[aạ]ng)\s*[:=-]\s*(.*)$/i;
const PASSWORD_VALUE_REGEX =
  /^\s*(?:password|pass\s*word|pass\s*wifi|wifi\s*pass|wi-?fi\s*pass|pass|pwd|mat\s*khau|m[aạ]t\s*kh[aẩ]u|mk)\s*[:=-]\s*(.*)$/i;
const SSID_LABEL_ONLY_REGEX =
  /^\s*(?:ssid|wifi(?:\s*name)?|network\s*name|ten\s*wifi|t[eê]n\s*m[aạ]ng)\s*[:=-]?\s*$/i;
const PASSWORD_LABEL_ONLY_REGEX =
  /^(?:password|pass\s*word|pass\s*wifi|wifi\s*pass|wi-?fi\s*pass|pass|pwd|mat\s*khau|m[aạ]t\s*kh[aẩ]u|mk)\s*[:=-]?\s*$/i;
const PASSWORD_LABEL_REGEX =
  /^\s*(?:password|pass\s*word|pass\s*wifi|wifi\s*pass|wi-?fi\s*pass|pass|pwd|mat\s*khau|m[aạ]t\s*kh[aẩ]u|mk)\b/i;
const SSID_LABEL_REGEX =
  /^\s*(?:ssid|wifi(?:\s*name)?|network\s*name|ten\s*wifi|t[eê]n\s*m[aạ]ng)\b/i;

function cleanLine(line) {
  return String(line || "")
    .replace(/[\u200B-\u200D\uFEFF]/g, "")
    .replace(/[：﹕꞉]/g, ":")
    .replace(/[|]/g, "I")
    .replace(/[“”]/g, "\"")
    .replace(/[‘’]/g, "'")
    .trim();
}

function normalizeLabelText(value) {
  return cleanLine(value)
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[1l|]/g, "i")
    .replace(/5/g, "s")
    .replace(/0/g, "o")
    .replace(/[-_]+/g, " ")
    .replace(/[^a-z\s]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function isSsidLabelText(value) {
  return [
    "ssid",
    "wifi",
    "wi fi",
    "wifi name",
    "wi fi name",
    "network name",
    "ten wifi",
    "ten mang",
    "id",
    "name",
  ].includes(normalizeLabelText(value));
}

function isPasswordLabelText(value) {
  return [
    "password",
    "pass word",
    "pass",
    "pwd",
    "mat khau",
    "mk",
    "wifi password",
    "wi fi password",
    "pass wifi",
    "wifi pass",
    "wi fi pass",
  ].includes(normalizeLabelText(value));
}

function splitLabelValue(line) {
  const cleaned = cleanLine(line);
  const match = cleaned.match(/^(.{1,40}?)[\s]*[:=\-]+[\s]*(.*)$/);
  if (!match) return null;

  if (isSsidLabelText(match[1])) {
    return { type: "ssid", value: sanitizeCandidate(match[2]) };
  }

  if (isPasswordLabelText(match[1])) {
    return { type: "password", value: sanitizePasswordCandidate(match[2]) };
  }

  return null;
}

function isSsidLabelOnly(line) {
  return isSsidLabelText(String(line || "").replace(/[\s:=\-]+$/g, ""));
}

function isPasswordLabelOnly(line) {
  return isPasswordLabelText(String(line || "").replace(/[\s:=\-]+$/g, ""));
}

function sanitizeCandidate(value) {
  return String(value || "")
    .replace(/^[`"'[\](){}<>]+/, "")
    .replace(/[`"'[\](){}<>]+$/, "")
    .replace(/\s{2,}/g, " ")
    .trim();
}

function sanitizePasswordCandidate(value) {
  return expandRepeatedPasswordPattern(sanitizeCandidate(value));
}

function expandRepeatedPasswordPattern(value) {
  const candidate = String(value || "").trim();
  if (!candidate) return candidate;

  const patterns = [
    /^([A-Za-z0-9@#$%^&._!-]+)\s+(\d{1,2})\s*(?:lan|lần)$/i,
    /^([A-Za-z0-9@#$%^&._!-]+)\s+(?:lap|lặp)\s+(\d{1,2})\s*(?:lan|lần)$/i,
    /^([A-Za-z0-9@#$%^&._!-]+)\s*(?:x|×|\*)\s*(\d{1,2})$/i,
  ];

  for (const pattern of patterns) {
    const match = candidate.match(pattern);
    if (!match) continue;

    const token = match[1];
    const count = Number.parseInt(match[2], 10);
    if (!isSafeRepeatToken(token) || count < 2 || count > 12) continue;

    const expanded = token.repeat(count);
    if (expanded.length <= 63) return expanded;
  }

  return candidate;
}

function isSafeRepeatToken(token) {
  return /^[A-Za-z0-9@#$%^&._!-]{1,16}$/.test(String(token || ""));
}

function looksLikeUrl(value) {
  return /(https?:\/\/|www\.|\.com\b|\.net\b|\.org\b)/i.test(String(value || ""));
}

function isLikelyNoiseLine(value) {
  const text = String(value || "").trim();
  if (!text) return true;
  if (looksLikeUrl(text)) return true;
  return /(free\s*wifi|mien\s*phi|hotline|email|username|dang\s*nhap|login|welcome|xin\s*chao|kinh\s*chao|quy\s*khach|cam\s*on|scan|qr|grabfood|befood|shopeefood|open\s*:|\b\d{1,3}\s*k\b|\b\d{1,2}\s*(?:am|pm)\b)/i.test(
    text,
  );
}

function extractValueAfterLabel(line, valueRegex, cutRegex) {
  const match = cleanLine(line).match(valueRegex);
  if (!match) return null;
  const rawValue = String(match[1] || "");
  const value = cutRegex ? rawValue.replace(cutRegex, "") : rawValue;
  const cleaned = valueRegex === PASSWORD_VALUE_REGEX
    ? sanitizePasswordCandidate(value)
    : sanitizeCandidate(value);
  return cleaned || null;
}

function pickNextUsefulLine(lines, fromIndex, forPassword) {
  for (let i = fromIndex; i < Math.min(lines.length, fromIndex + 3); i += 1) {
    const candidate = sanitizeCandidate(lines[i]);
    if (!candidate) continue;
    if (
      SSID_LABEL_ONLY_REGEX.test(candidate) ||
      PASSWORD_LABEL_ONLY_REGEX.test(candidate) ||
      isSsidLabelOnly(candidate) ||
      isPasswordLabelOnly(candidate)
    ) {
      continue;
    }
    if (isLikelyNoiseLine(candidate)) continue;
    if (forPassword) {
      if (looksLikePassword(candidate)) return stripPasswordPrefix(candidate) || sanitizePasswordCandidate(candidate);
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
    if (key === "P") password = sanitizePasswordCandidate(value) || null;
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
    const labeledValue = splitLabelValue(cleaned);

    if (!ssid) {
      ssid =
        (labeledValue?.type === "ssid" ? labeledValue.value : null) ||
        extractValueAfterLabel(cleaned, SSID_VALUE_REGEX, PASSWORD_VALUE_REGEX) ||
        (SSID_LABEL_ONLY_REGEX.test(cleaned) || isSsidLabelOnly(cleaned)
          ? pickNextUsefulLine(lines, i + 1, false)
          : null);
    }

    if (!password) {
      const extractedPassword =
        (labeledValue?.type === "password" ? labeledValue.value : null) ||
        extractValueAfterLabel(cleaned, PASSWORD_VALUE_REGEX, SSID_VALUE_REGEX) ||
        (PASSWORD_LABEL_ONLY_REGEX.test(cleaned) || isPasswordLabelOnly(cleaned)
          ? pickNextUsefulLine(lines, i + 1, true)
          : null);
      password = extractedPassword ? stripPasswordPrefix(extractedPassword) || sanitizePasswordCandidate(extractedPassword) : null;
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
  const labeledValue = splitLabelValue(text);
  if (labeledValue?.type === "password") return labeledValue.value;

  return sanitizePasswordCandidate(
    String(text || "")
    .replace(/^(password|pass|pass\s*word|pass\s*wifi|wifi\s*pass|wi-?fi\s*pass|mat\s*khau|m[aạ]t\s*kh[aẩ]u|mk)\s*[:=-]\s*/i, "")
    .trim(),
  );
}

function stripSsidPrefix(text) {
  const labeledValue = splitLabelValue(text);
  if (labeledValue?.type === "ssid") return labeledValue.value;

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
  while (
    sanitizedLines.length > 0 &&
    (isWifiHeaderLine(sanitizedLines[0]) || isSsidLabelOnly(sanitizedLines[0]))
  ) {
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
      password: stripPasswordPrefix(first) || sanitizePasswordCandidate(first),
      sourceFormat: "single_line_password",
      confidence: 0.9,
    };
  }

  let normalizedPassword = stripPasswordPrefix(second) || sanitizePasswordCandidate(second);
  if ((PASSWORD_LABEL_ONLY_REGEX.test(second) || isPasswordLabelOnly(second)) && third) {
    normalizedPassword = stripPasswordPrefix(third) || sanitizePasswordCandidate(third);
  }
  if (PASSWORD_LABEL_ONLY_REGEX.test(normalizedPassword) || isPasswordLabelOnly(normalizedPassword)) {
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

function extractSplitRowLayout(lines) {
  for (let i = 0; i < lines.length - 1; i += 1) {
    const header = normalizeLabelText(lines[i]);
    if (!header.includes("name") || !header.includes("password")) continue;

    const row = cleanLine(lines[i + 1]);
    const parts = row.split(/\s{2,}|\t+/).map(sanitizeCandidate).filter(Boolean);
    if (parts.length < 2) continue;

    const ssid = stripSsidPrefix(parts[0]) || parts[0];
    const password = stripPasswordPrefix(parts.slice(1).join(" ")) ||
      sanitizePasswordCandidate(parts.slice(1).join(" "));
    if (!ssid || !password) continue;

    return {
      ssid,
      password,
      sourceFormat: "split_row",
      confidence: 0.96,
    };
  }

  return null;
}

function looksLikePassword(text) {
  const value = sanitizePasswordCandidate(text);
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
  let value = sanitizePasswordCandidate(cleaned);
  const labeledValue = splitLabelValue(cleaned);
  if (labeledValue?.type === "password") {
    score += 4;
    value = labeledValue.value || cleaned;
  } else if (PASSWORD_LABEL_REGEX.test(cleaned)) {
    score += 4;
    value = stripPasswordPrefix(cleaned) || sanitizePasswordCandidate(cleaned);
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
  const labeledValue = splitLabelValue(cleaned);
  if (labeledValue?.type === "ssid") {
    score += 4;
    value = labeledValue.value || cleaned;
  } else if (SSID_LABEL_REGEX.test(cleaned)) {
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
    .filter(
      (line) =>
        !SSID_LABEL_ONLY_REGEX.test(line) &&
        !PASSWORD_LABEL_ONLY_REGEX.test(line) &&
        !isSsidLabelOnly(line) &&
        !isPasswordLabelOnly(line),
    );

  if (filtered.length === 0) return null;
  if (filtered.length === 1) {
    const password = stripPasswordPrefix(filtered[0]) || sanitizePasswordCandidate(filtered[0]);
    if (!isLikelyNoiseLine(password)) {
      return {
        ssid: null,
        password,
        sourceFormat: "single_line_password",
        confidence: 0.86,
      };
    }
  }

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
  if (candidate.sourceFormat === "labeled_text") score += 0.2;
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
  const splitRow = extractSplitRowLayout(lines);
  const twoLine = extractTwoLineSsidPassword(lines);

  const heuristic = extractHeuristic(lines);
  const candidates = [labeled, splitRow, twoLine, heuristic].filter(Boolean);
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
    error: "Không trích xuất được dữ liệu Wi-Fi từ nội dung OCR",
  };
}

module.exports = {
  parseOcrWifiData,
};
