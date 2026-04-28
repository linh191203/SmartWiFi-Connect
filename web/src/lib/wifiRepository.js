import { fetchJson, normalizeBaseUrl } from "./api";
import { decryptPassword, encryptPassword } from "./crypto";
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

    async validateWifi(baseUrl, { ssid, password, ocrText }) {
      return fetchJson(`${normalizeBaseUrl(baseUrl)}/api/ai/validate`, {
        method: "POST",
        body: JSON.stringify({ ssid, password, ocrText }),
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

    async saveConnectedWifi({ ssid, password, security, sourceFormat, savePassword, passphrase }) {
      // Validate SSID
      const trimmedSsid = String(ssid || "").trim();
      if (!trimmedSsid) {
        throw new Error("SSID is required");
      }
      if (trimmedSsid.length > 32) {
        throw new Error("SSID must be 32 characters or less");
      }

      // Validate password if provided
      const trimmedPassword = String(password || "").trim();
      if (trimmedPassword && trimmedPassword.length < 8) {
        throw new Error("Password must be at least 8 characters");
      }
      if (trimmedPassword && trimmedPassword.length > 63) {
        throw new Error("Password must be 63 characters or less");
      }

      // Encrypt password if passphrase supplied, otherwise store plaintext
      let storedPassword = null;
      let passwordEncrypted = false;
      if (savePassword && trimmedPassword) {
        const trimmedPassphrase = String(passphrase || "").trim();
        if (trimmedPassphrase) {
          storedPassword = await encryptPassword(trimmedPassphrase, trimmedPassword);
          passwordEncrypted = true;
        } else {
          storedPassword = trimmedPassword;
        }
      }

        const payload = {
          ssid: trimmedSsid,
          password: storedPassword,
          security: String(security || "WPA/WPA2").trim(),
          sourceFormat: String(sourceFormat || "manual_entry").trim(),
          passwordSaved: Boolean(savePassword && trimmedPassword),
          passwordEncrypted,
        };

        // Try server first; fall back to localStorage on network error
        try {
          const envelope = await fetchJson(
            `${normalizeBaseUrl(defaultApiBaseUrl)}/api/networks`,
            { method: "POST", body: JSON.stringify(payload) },
          );
          // Also sync to localStorage so offline reads work
          const existing = getSavedNetworksStorage();
          const nextRecord = { id: envelope.data.id, ...payload, lastConnectedAtMillis: envelope.data.lastConnectedAtMillis ?? Date.now() };
          setSavedNetworksStorage([nextRecord, ...existing.filter((n) => n.ssid !== trimmedSsid)]);
          return envelope.data;
        } catch {
          // Offline / server down — save locally only
          const nextRecord = {
            id: makeId(),
            ...payload,
            lastConnectedAtMillis: Date.now(),
          };
          const existing = getSavedNetworksStorage();
          const success = setSavedNetworksStorage([nextRecord, ...existing.filter((n) => n.ssid !== trimmedSsid)]);
          if (!success) throw new Error("Failed to save network to browser storage");
          return nextRecord;
        }
    },

    async getSavedNetworks() {
        try {
          const envelope = await fetchJson(
            `${normalizeBaseUrl(defaultApiBaseUrl)}/api/networks`,
          );
          // Sync server list to localStorage
          setSavedNetworksStorage(envelope.data);
          return envelope.data;
        } catch {
          return getSavedNetworksStorage().sort(
            (left, right) => right.lastConnectedAtMillis - left.lastConnectedAtMillis,
          );
        }
    },

    /**
     * Decrypt the password for a single saved network.
     * @param {number} networkId - ID of the network to decrypt
     * @param {string} passphrase - User passphrase
     * @returns {Promise<string>} - Decrypted plaintext password
     */
    async decryptNetworkPassword(networkId, passphrase) {
      const networks = getSavedNetworksStorage();
      const network = networks.find((n) => n.id === networkId);
      if (!network) throw new Error("Network not found");
      if (!network.passwordEncrypted) return network.password || "";
      if (!network.password) return "";
      return decryptPassword(passphrase, network.password);
    },

    async deleteSavedNetworkById(id) {
      if (!id) throw new Error("Network ID is required");
      try {
        await fetchJson(
          `${normalizeBaseUrl(defaultApiBaseUrl)}/api/networks/${id}`,
          { method: "DELETE" },
        );
      } catch (err) {
        if (err.message === "Network not found") throw err;
        // Server unreachable — fall through to local delete
      }
      const networks = getSavedNetworksStorage();
      const filtered = networks.filter((item) => item.id !== id);
      if (filtered.length === networks.length) throw new Error("Network not found");
      const success = setSavedNetworksStorage(filtered);
      if (!success) throw new Error("Failed to delete network");
      return true;
    },

    async clearSavedNetworks() {
      try {
        await fetchJson(
          `${normalizeBaseUrl(defaultApiBaseUrl)}/api/networks`,
          { method: "DELETE" },
        );
      } catch {
        // Server unreachable — fall through to local clear
      }
      const success = setSavedNetworksStorage([]);
      if (!success) throw new Error("Failed to clear networks");
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

    /**
     * Export all data for backup/transfer
     */
    exportAllData() {
      return {
        networks: getSavedNetworksStorage(),
        scanHistory: getScanHistory(),
        user: getStoredUser(),
        apiBaseUrl: defaultApiBaseUrl,
        exportedAt: new Date().toISOString(),
      };
    },

    /**
     * Import data from backup
     */
    async importData(data) {
      if (!data || typeof data !== 'object') {
        throw new Error('Invalid data format');
      }

      try {
        if (data.networks && Array.isArray(data.networks)) {
          setSavedNetworksStorage(data.networks);
        }
        if (data.scanHistory && Array.isArray(data.scanHistory)) {
          setScanHistory(data.scanHistory);
        }
        if (data.user && typeof data.user === 'object') {
          setStoredUser(data.user);
        }
        return true;
      } catch (error) {
        throw new Error('Failed to import data: ' + error.message);
      }
    },

    /**
     * Get storage usage information
     */
    getStorageInfo() {
      const networks = getSavedNetworksStorage();
      const history = getScanHistory();
      const user = getStoredUser();

      return {
        networks: {
          count: networks.length,
          sizeBytes: JSON.stringify(networks).length,
        },
        history: {
          count: history.length,
          sizeBytes: JSON.stringify(history).length,
        },
        user: {
          sizeBytes: JSON.stringify(user).length,
        },
        totalSizeBytes: 
          JSON.stringify(networks).length +
          JSON.stringify(history).length +
          JSON.stringify(user).length,
      };
    },
  };
}