const { parseOcrWifiData } = require("./ocrParser");

function normalizeWhitespace(value) {
  return String(value || "").replace(/\s+/g, " ").trim();
}

function stripSsidLabel(value) {
  return normalizeWhitespace(value).replace(
    /^(ssid|wifi\s*name|network\s*name|ten\s*wifi|t[eê]n\s*m[aạ]ng)\s*[:=-]\s*/i,
    "",
  );
}

function hasSsidLabel(value) {
  return /^(ssid|wifi\s*name|network\s*name|ten\s*wifi|t[eê]n\s*m[aạ]ng)\s*[:=-]\s*/i.test(
    normalizeWhitespace(value),
  );
}

function stripPasswordLabel(value) {
  return normalizeWhitespace(value).replace(
    /^(password|pass\s*word|pass|mat\s*khau|m[aậạ]t\s*kh[aẩ]u|mk)\s*[:=-]\s*/i,
    "",
  );
}

function normalizeSsid(value) {
  return stripSsidLabel(value) || null;
}

function normalizePassword(value) {
  return stripPasswordLabel(value) || null;
}

function hasAmbiguousOcrChars(value) {
  return /[|]/.test(String(value || ""));
}

function buildSuggestion({ flags, shouldAutoConnect, normalizedSsid, normalizedPassword }) {
  if (!normalizedSsid && !normalizedPassword) {
    return "Khong du du lieu WiFi. Nen OCR lai hoac nhap tay.";
  }

  if (flags.includes("missing_password")) {
    return "Da tim thay SSID nhung chua co mat khau. Nen kiem tra lai OCR truoc khi ket noi.";
  }

  if (flags.includes("missing_ssid")) {
    return "Da tim thay mat khau nhung chua co SSID. Nen kiem tra lai ten mang WiFi.";
  }

  if (flags.includes("ssid_mismatch_with_ocr") || flags.includes("password_mismatch_with_ocr")) {
    return "Du lieu da nhap khong khop hoan toan voi OCR. Nen kiem tra lai truoc khi ket noi.";
  }

  if (shouldAutoConnect) {
    return "Du lieu WiFi co do tin cay tot, co the uu tien tu dong ket noi.";
  }

  return "Da co du lieu WiFi, nhung nen xac nhan lai truoc khi ket noi.";
}

function validateWifiCandidate(input) {
  const rawSsid = input?.ssid;
  const rawPassword = input?.password;
  const rawOcrText = input?.ocrText;

  const parsed = typeof rawOcrText === "string" && rawOcrText.trim()
    ? parseOcrWifiData(rawOcrText)
    : null;

  const parsedSsid = parsed?.ok ? parsed.data?.ssid : null;
  const parsedPasswordCandidate = parsed?.ok ? parsed.data?.password : null;
  const parsedPassword = !rawPassword && hasSsidLabel(parsedPasswordCandidate)
    ? null
    : parsedPasswordCandidate;

  const normalizedSsid = normalizeSsid(rawSsid || parsedSsid);
  const normalizedPassword = normalizePassword(rawPassword || parsedPassword);

  const flags = [];
  let score = 0.1;

  if (parsed?.ok) {
    score += Math.min(Math.max(parsed.data?.confidence || 0, 0), 1) * 0.25;
  } else if (rawOcrText) {
    flags.push("ocr_parse_failed");
  }

  if (normalizedSsid) {
    if (normalizedSsid.length > 32) {
      flags.push("ssid_too_long");
      score -= 0.15;
    } else {
      score += 0.25;
    }
  } else {
    flags.push("missing_ssid");
    score -= 0.1;
  }

  if (normalizedPassword) {
    if (normalizedPassword.length >= 8 && normalizedPassword.length <= 63) {
      score += 0.3;
    } else if (normalizedPassword.length > 63) {
      flags.push("password_too_long");
      score -= 0.2;
    } else {
      flags.push("password_too_short");
      score += 0.05;
    }

    if (/\s/.test(normalizedPassword)) {
      flags.push("password_contains_spaces");
      score -= 0.05;
    }
  } else {
    flags.push("missing_password");
    score -= 0.1;
  }

  if (rawSsid && parsedSsid && normalizeSsid(rawSsid) !== normalizeSsid(parsedSsid)) {
    flags.push("ssid_mismatch_with_ocr");
    score -= 0.12;
  }

  if (rawPassword && parsedPassword && normalizePassword(rawPassword) !== normalizePassword(parsedPassword)) {
    flags.push("password_mismatch_with_ocr");
    score -= 0.12;
  }

  if (hasAmbiguousOcrChars(rawOcrText) || hasAmbiguousOcrChars(rawPassword) || hasAmbiguousOcrChars(rawSsid)) {
    flags.push("ocr_ambiguous_characters");
    score -= 0.08;
  }

  const confidence = Number(Math.min(Math.max(score, 0.01), 0.99).toFixed(2));
  const criticalFlags = new Set(["password_too_long", "ssid_too_long", "ocr_parse_failed"]);
  const hasCriticalFlag = flags.some((flag) => criticalFlags.has(flag));
  // A credential is "validated" only when both SSID and password are present and valid
  const validated = Boolean(normalizedSsid && normalizedPassword);
  const shouldAutoConnect = Boolean(
    normalizedSsid &&
      normalizedPassword &&
      confidence >= 0.72 &&
      !hasCriticalFlag &&
      !flags.includes("ssid_mismatch_with_ocr") &&
      !flags.includes("password_mismatch_with_ocr"),
  );

  return {
    validated,
    confidence,
    suggestion: buildSuggestion({ flags, shouldAutoConnect, normalizedSsid, normalizedPassword }),
    flags,
    normalizedSsid,
    normalizedPassword,
    parseRecommendation: shouldAutoConnect ? "connect" : confidence >= 0.5 ? "review" : "retry_ocr",
    shouldAutoConnect,
  };
}

module.exports = {
  validateWifiCandidate,
};