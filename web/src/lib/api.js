/**
 * Fetch JSON from URL with proper error handling
 * @param {string} url - The URL to fetch from
 * @param {object} options - Fetch options (method, headers, body, etc.)
 * @returns {Promise<object>} Parsed JSON response or throws error
 */
export async function fetchJson(url, options = {}) {
  const response = await fetch(url, {
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {}),
    },
    ...options,
  });

  const data = await response.json().catch(() => null);
  if (!response.ok) {
    const message = data?.error || `Request failed with status ${response.status}`;
    throw new Error(message);
  }

  return data;
}

/**
 * Normalize base URL by removing trailing slashes
 * @param {string} baseUrl - The base URL
 * @returns {string} Normalized base URL
 */
export function normalizeBaseUrl(baseUrl) {
  return String(baseUrl || "http://localhost:8080").replace(/\/+$/, "");
}

/**
 * Get user-friendly error message from API error
 * @param {Error} error - Error thrown from fetch
 * @returns {string} User-friendly error message
 */
export function getErrorMessage(error) {
  if (error instanceof Error) {
    return error.message;
  }
  return "An unexpected error occurred";
}

/**
 * Check if API is reachable with timeout
 * @param {string} baseUrl - The base URL to check
 * @param {number} timeoutMs - Timeout in milliseconds
 * @returns {Promise<boolean>} True if API is reachable
 */
export async function isApiReachable(baseUrl, timeoutMs = 5000) {
  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), timeoutMs);

    const response = await fetch(`${normalizeBaseUrl(baseUrl)}/health`, {
      signal: controller.signal,
    });

    clearTimeout(timeoutId);
    return response.ok;
  } catch {
    return false;
  }
}

/**
 * Create mock response for development/testing
 * @param {string} endpoint - The endpoint being mocked
 * @param {object} requestData - The request data
 * @returns {object} Mock response
 */
export function getMockResponse(endpoint, requestData = {}) {
  const mockResponses = {
    "/health": {
      ok: true,
      service: "smartwificonnect-server",
      uptimeSeconds: Math.floor(Math.random() * 10000),
      timestamp: new Date().toISOString(),
    },
    "/api/v1/ocr/parse": {
      ok: true,
      data: {
        ssid: requestData.ocrText ? "MockSSID" : null,
        password: requestData.ocrText ? "MockPassword123" : null,
        security: "WPA/WPA2",
        sourceFormat: "mock_ocr",
        confidence: 0.75,
        passwordOnly: false,
      },
    },
    "/api/ai/validate": {
      ok: true,
      input: requestData,
      data: {
        validated: true,
        confidence: 0.80,
        suggestion: "Mock validation result - looks good",
        flags: [],
        normalizedSsid: requestData.ssid || null,
        normalizedPassword: requestData.password || null,
        parseRecommendation: "review",
        shouldAutoConnect: false,
      },
      timestamp: new Date().toISOString(),
    },
  };

  return mockResponses[endpoint] || { ok: false, error: "Mock endpoint not found" };
}