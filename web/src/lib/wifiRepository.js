import { fetchJson, normalizeBaseUrl } from "./api";
import {
  getSavedNetworksStorage,
  getScanHistory,
  getStoredApiBaseUrl,
  getStoredUser,
  setSavedNetworksStorage,
  setScanHistory,
  setStoredApiBaseUrl,
  setStoredUser,
} from "./storage";

export const defaultApiBaseUrl = getStoredApiBaseUrl(
  import.meta.env.VITE_API_BASE_URL || "http://localhost:8080",
);

function makeId() {
  return Date.now() + Math.floor(Math.random() * 1000);
}

export function createWifiRepository() {
  return {
    async checkHealth(baseUrl) {
      return fetchJson(`${normalizeBaseUrl(baseUrl)}/health`);
    },

    async parseOcr(baseUrl, ocrText) {
      return fetchJson(`${normalizeBaseUrl(baseUrl)}/api/v1/ocr/parse`, {
        method: "POST",
        body: JSON.stringify({ ocrText }),
      });
    },

    async saveParsedWifi(baseUrl, ocrText, parsedWifiData) {
      const nextRecord = {
        id: makeId(),
        baseUrl: normalizeBaseUrl(baseUrl),
        ocrText,
        ssid: parsedWifiData.ssid || "",
        password: parsedWifiData.password || "",
        sourceFormat: parsedWifiData.sourceFormat || "unknown",
        confidence: parsedWifiData.confidence ?? null,
        createdAtMillis: Date.now(),
      };

      const records = getScanHistory();
      records.unshift(nextRecord);
      setScanHistory(records.slice(0, 30));
      return nextRecord;
    },

    async getLatestSavedWifi() {
      return getScanHistory()[0] || null;
    },

    async saveConnectedWifi({ ssid, password, security, sourceFormat, savePassword }) {
      const nextRecord = {
        id: makeId(),
        ssid: String(ssid || "").trim(),
        password: savePassword ? String(password || "").trim() || null : null,
        security: String(security || "WPA/WPA2").trim(),
        sourceFormat: String(sourceFormat || "manual_entry").trim(),
        passwordSaved: Boolean(savePassword && password),
        lastConnectedAtMillis: Date.now(),
      };

      const existing = getSavedNetworksStorage();
      const nextNetworks = [nextRecord, ...existing.filter((item) => item.ssid !== nextRecord.ssid)];
      setSavedNetworksStorage(nextNetworks);
      return nextRecord;
    },

    async getSavedNetworks() {
      return getSavedNetworksStorage().sort(
        (left, right) => right.lastConnectedAtMillis - left.lastConnectedAtMillis,
      );
    },

    async deleteSavedNetworkById(id) {
      const filtered = getSavedNetworksStorage().filter((item) => item.id !== id);
      setSavedNetworksStorage(filtered);
      return true;
    },

    async clearSavedNetworks() {
      setSavedNetworksStorage([]);
      return 0;
    },

    async getSavedNetworksSummary() {
      const savedNetworks = getSavedNetworksStorage().sort(
        (left, right) => right.lastConnectedAtMillis - left.lastConnectedAtMillis,
      );
      return {
        count: savedNetworks.length,
        latestSsid: savedNetworks[0]?.ssid || null,
      };
    },

    async getUser() {
      return getStoredUser();
    },

    saveUser(user) {
      setStoredUser(user);
    },

    saveApiBaseUrl(value) {
      setStoredApiBaseUrl(normalizeBaseUrl(value));
    },
  };
}