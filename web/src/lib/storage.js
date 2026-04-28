const SCAN_HISTORY_KEY = "smartwifi.scanHistory";
const SAVED_NETWORKS_KEY = "smartwifi.savedNetworks";
const USER_KEY = "smartwifi.user";
const API_BASE_URL_KEY = "smartwifi.apiBaseUrl";

// Configuration
const MAX_SAVED_NETWORKS = 50;
const MAX_SCAN_HISTORY = 30;

/**
 * Safely read JSON from localStorage
 * @param {string} key - Storage key
 * @param {any} fallback - Fallback value if key doesn't exist or data is corrupted
 * @returns {any} Parsed value or fallback
 */
function readJson(key, fallback) {
  try {
    const raw = localStorage.getItem(key);
    if (!raw) return fallback;
    
    const parsed = JSON.parse(raw);
    
    // Validate parsed data is an object or array
    if (parsed === null || typeof parsed !== 'object') {
      console.warn(`[Storage] Invalid data type for key "${key}":`, typeof parsed);
      return fallback;
    }
    
    return parsed;
  } catch (error) {
    console.error(`[Storage] Failed to parse key "${key}":`, error);
    return fallback;
  }
}

/**
 * Safely write JSON to localStorage
 * @param {string} key - Storage key
 * @param {any} value - Value to store
 * @returns {boolean} True if successful, false otherwise
 */
function writeJson(key, value) {
  try {
    const serialized = JSON.stringify(value);
    localStorage.setItem(key, serialized);
    return true;
  } catch (error) {
    console.error(`[Storage] Failed to write key "${key}":`, error);
    
    // If quota exceeded, try to clear old data
    if (error.name === 'QuotaExceededError') {
      console.warn(`[Storage] Quota exceeded for key "${key}", attempting cleanup...`);
      try {
        if (key === SAVED_NETWORKS_KEY) {
          // Keep only 25 most recent networks
          const networks = readJson(key, []);
          const trimmed = networks.slice(0, 25);
          localStorage.setItem(key, JSON.stringify(trimmed));
          console.log('[Storage] Cleaned up saved networks');
          return true;
        }
      } catch {
        console.error('[Storage] Cleanup failed');
      }
    }
    
    return false;
  }
}

// ===== SCAN HISTORY =====

export function getScanHistory() {
  return readJson(SCAN_HISTORY_KEY, []);
}

export function setScanHistory(records) {
  if (!Array.isArray(records)) {
    console.error('[Storage] setScanHistory: records must be an array');
    return false;
  }
  
  // Keep only recent records
  const trimmed = records.slice(0, MAX_SCAN_HISTORY);
  return writeJson(SCAN_HISTORY_KEY, trimmed);
}

// ===== SAVED NETWORKS =====

/**
 * Get all saved networks from storage
 * @returns {Array} Array of saved network objects
 */
export function getSavedNetworksStorage() {
  const networks = readJson(SAVED_NETWORKS_KEY, []);
  
  // Validate and clean up networks
  if (Array.isArray(networks)) {
    return networks.filter(net => {
      // Basic validation
      if (!net || typeof net !== 'object') return false;
      if (!net.ssid || typeof net.ssid !== 'string') return false;
      if (typeof net.passwordSaved !== 'boolean') return false;
      if (typeof net.lastConnectedAtMillis !== 'number') return false;
      return true;
    });
  }
  
  return [];
}

/**
 * Save networks to storage
 * @param {Array} records - Array of network objects to save
 * @returns {boolean} True if successful
 */
export function setSavedNetworksStorage(records) {
  if (!Array.isArray(records)) {
    console.error('[Storage] setSavedNetworksStorage: records must be an array');
    return false;
  }
  
  // Validate each record
  const validated = records.filter(record => {
    if (!record || typeof record !== 'object') return false;
    if (!record.ssid || typeof record.ssid !== 'string') return false;
    if (record.ssid.trim().length === 0) return false;
    if (typeof record.passwordSaved !== 'boolean') return false;
    if (typeof record.lastConnectedAtMillis !== 'number') return false;
    return true;
  });
  
  // Keep only recent records
  const trimmed = validated.slice(0, MAX_SAVED_NETWORKS);
  
  return writeJson(SAVED_NETWORKS_KEY, trimmed);
}

/**
 * Add a single network to saved networks
 * @param {Object} network - Network object to save
 * @returns {Object} Saved network object or null if failed
 */
export function addSavedNetwork(network) {
  if (!network || typeof network !== 'object') {
    console.error('[Storage] addSavedNetwork: invalid network object');
    return null;
  }
  
  // Validate required fields
  if (!network.ssid || typeof network.ssid !== 'string' || network.ssid.trim().length === 0) {
    console.error('[Storage] addSavedNetwork: SSID is required');
    return null;
  }
  
  const networks = getSavedNetworksStorage();
  
  // Remove any existing network with same SSID
  const filtered = networks.filter(n => n.ssid !== network.ssid);
  
  // Add new network at the beginning (most recent first)
  const updated = [network, ...filtered].slice(0, MAX_SAVED_NETWORKS);
  
  if (writeJson(SAVED_NETWORKS_KEY, updated)) {
    return network;
  }
  
  return null;
}

/**
 * Delete a specific network from saved networks
 * @param {number} networkId - Network ID to delete
 * @returns {boolean} True if successful
 */
export function deleteSavedNetwork(networkId) {
  const networks = getSavedNetworksStorage();
  const filtered = networks.filter(n => n.id !== networkId);
  return writeJson(SAVED_NETWORKS_KEY, filtered);
}

/**
 * Clear all saved networks
 * @returns {boolean} True if successful
 */
export function clearAllSavedNetworks() {
  return writeJson(SAVED_NETWORKS_KEY, []);
}

// ===== USER DATA =====

export function getStoredUser() {
  return readJson(USER_KEY, { name: "", email: "" });
}

export function setStoredUser(user) {
  if (!user || typeof user !== 'object') {
    console.error('[Storage] setStoredUser: invalid user object');
    return false;
  }
  
  return writeJson(USER_KEY, {
    name: String(user.name || "").trim(),
    email: String(user.email || "").trim(),
  });
}

// ===== API BASE URL =====

export function getStoredApiBaseUrl(fallback) {
  const stored = localStorage.getItem(API_BASE_URL_KEY);
  return stored || fallback;
}

export function setStoredApiBaseUrl(value) {
  if (!value || typeof value !== 'string') {
    console.error('[Storage] setStoredApiBaseUrl: invalid URL');
    return false;
  }
  
  try {
    localStorage.setItem(API_BASE_URL_KEY, String(value).trim());
    return true;
  } catch (error) {
    console.error('[Storage] Failed to set API base URL:', error);
    return false;
  }
}

// ===== STORAGE UTILITIES =====

/**
 * Get total storage usage
 * @returns {Object} { used: bytes, estimated: bytes, available: bytes }
 */
export function getStorageUsage() {
  try {
    const total = JSON.stringify(localStorage).length;
    
    // Estimate based on average
    const networks = getSavedNetworksStorage();
    const history = getScanHistory();
    const user = getStoredUser();
    
    const estimated = {
      networks: JSON.stringify(networks).length,
      history: JSON.stringify(history).length,
      user: JSON.stringify(user).length,
      total,
    };
    
    return estimated;
  } catch {
    return { total: 0 };
  }
}

/**
 * Export all data as JSON
 * @returns {Object} All stored data
 */
export function exportAllData() {
  return {
    networks: getSavedNetworksStorage(),
    history: getScanHistory(),
    user: getStoredUser(),
    apiBaseUrl: getStoredApiBaseUrl('http://localhost:8080'),
    exportedAt: new Date().toISOString(),
  };
}

/**
 * Import data from exported JSON
 * @param {Object} data - Data object to import
 * @returns {boolean} True if successful
 */
export function importData(data) {
  if (!data || typeof data !== 'object') {
    console.error('[Storage] importData: invalid data object');
    return false;
  }
  
  try {
    if (data.networks && Array.isArray(data.networks)) {
      setSavedNetworksStorage(data.networks);
    }
    
    if (data.history && Array.isArray(data.history)) {
      setScanHistory(data.history);
    }
    
    if (data.user && typeof data.user === 'object') {
      setStoredUser(data.user);
    }
    
    if (data.apiBaseUrl && typeof data.apiBaseUrl === 'string') {
      setStoredApiBaseUrl(data.apiBaseUrl);
    }
    
    console.log('[Storage] Data imported successfully');
    return true;
  } catch (error) {
    console.error('[Storage] Import failed:', error);
    return false;
  }
}

/**
 * Clear all stored data
 * @returns {boolean} True if successful
 */
export function clearAllData() {
  try {
    localStorage.removeItem(SCAN_HISTORY_KEY);
    localStorage.removeItem(SAVED_NETWORKS_KEY);
    localStorage.removeItem(USER_KEY);
    localStorage.removeItem(API_BASE_URL_KEY);
    
    console.log('[Storage] All data cleared');
    return true;
  } catch (error) {
    console.error('[Storage] Failed to clear data:', error);
    return false;
  }
}