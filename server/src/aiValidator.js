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
    /^(password|pass\s*word|pass\s*wifi|wifi\s*pass|wi-?fi\s*pass|pass|mat\s*khau|m[aạ]t\s*kh[aẩ]u|mk)\s*[:=-]\s*/i,
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
    return "Không đủ dữ liệu Wi-Fi. Nên OCR lại hoặc nhập tay.";
  }

  if (flags.includes("missing_password")) {
    return "Đã tìm thấy SSID nhưng chưa có mật khẩu. Nên kiểm tra lại OCR trước khi kết nối.";
  }

  if (flags.includes("missing_ssid")) {
    return "Đã tìm thấy mật khẩu nhưng chưa có SSID. Nên kiểm tra lại tên mạng Wi-Fi.";
  }

  if (flags.includes("ssid_mismatch_with_ocr") || flags.includes("password_mismatch_with_ocr")) {
    return "Dữ liệu đã nhập không khớp hoàn toàn với OCR. Nên kiểm tra lại trước khi kết nối.";
  }

  if (shouldAutoConnect) {
    return "Dữ liệu Wi-Fi có độ tin cậy tốt, có thể ưu tiên tự động kết nối.";
  }

  return "Đã có dữ liệu Wi-Fi, nhưng nên xác nhận lại trước khi kết nối.";
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
  const validated = Boolean(normalizedSsid || normalizedPassword);
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
