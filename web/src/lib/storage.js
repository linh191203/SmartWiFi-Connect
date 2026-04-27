const SCAN_HISTORY_KEY = "smartwifi.scanHistory";
const SAVED_NETWORKS_KEY = "smartwifi.savedNetworks";
const USER_KEY = "smartwifi.user";
const API_BASE_URL_KEY = "smartwifi.apiBaseUrl";

function readJson(key, fallback) {
  try {
    const raw = localStorage.getItem(key);
    return raw ? JSON.parse(raw) : fallback;
  } catch {
    return fallback;
  }
}

function writeJson(key, value) {
  localStorage.setItem(key, JSON.stringify(value));
}

export function getScanHistory() {
  return readJson(SCAN_HISTORY_KEY, []);
}

export function setScanHistory(records) {
  writeJson(SCAN_HISTORY_KEY, records);
}

export function getSavedNetworksStorage() {
  return readJson(SAVED_NETWORKS_KEY, []);
}

export function setSavedNetworksStorage(records) {
  writeJson(SAVED_NETWORKS_KEY, records);
}

export function getStoredUser() {
  return readJson(USER_KEY, { name: "", email: "" });
}

export function setStoredUser(user) {
  writeJson(USER_KEY, user);
}

export function getStoredApiBaseUrl(fallback) {
  return localStorage.getItem(API_BASE_URL_KEY) || fallback;
}

export function setStoredApiBaseUrl(value) {
  localStorage.setItem(API_BASE_URL_KEY, value);
}