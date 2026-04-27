import { describe, it, expect, beforeEach, afterEach } from "vitest";
import {
  getScanHistory,
  setScanHistory,
  getSavedNetworksStorage,
  setSavedNetworksStorage,
  getStoredUser,
  setStoredUser,
  getStoredApiBaseUrl,
  setStoredApiBaseUrl,
} from "../lib/storage";

describe("storage", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
  });

  describe("scanHistory", () => {
    it("returns empty array by default", () => {
      expect(getScanHistory()).toEqual([]);
    });

    it("saves and retrieves scan history", () => {
      const records = [{ id: 1, ssid: "Net1" }, { id: 2, ssid: "Net2" }];
      setScanHistory(records);
      expect(getScanHistory()).toEqual(records);
    });

    it("returns fallback if localStorage is corrupted", () => {
      localStorage.setItem("smartwifi.scanHistory", "not-json{{{");
      expect(getScanHistory()).toEqual([]);
    });
  });

  describe("savedNetworks", () => {
    it("returns empty array by default", () => {
      expect(getSavedNetworksStorage()).toEqual([]);
    });

    it("saves and retrieves saved networks", () => {
      const networks = [{ id: 1, ssid: "HomeNet", password: "pass1234" }];
      setSavedNetworksStorage(networks);
      expect(getSavedNetworksStorage()).toEqual(networks);
    });
  });

  describe("user", () => {
    it("returns default user object when empty", () => {
      const user = getStoredUser();
      expect(user).toEqual({ name: "", email: "" });
    });

    it("saves and retrieves user", () => {
      setStoredUser({ name: "Alice", email: "alice@example.com" });
      expect(getStoredUser()).toEqual({ name: "Alice", email: "alice@example.com" });
    });
  });

  describe("apiBaseUrl", () => {
    it("returns fallback when not set", () => {
      expect(getStoredApiBaseUrl("http://localhost:8080")).toBe("http://localhost:8080");
    });

    it("returns stored value when set", () => {
      setStoredApiBaseUrl("http://myserver:9000");
      expect(getStoredApiBaseUrl("http://localhost:8080")).toBe("http://myserver:9000");
    });
  });
});
