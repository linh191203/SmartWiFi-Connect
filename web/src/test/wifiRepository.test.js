import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { createWifiRepository } from "../lib/wifiRepository";

// Mock the api module to avoid real fetch calls
vi.mock("../lib/api", () => ({
  fetchJson: vi.fn(),
  normalizeBaseUrl: (url) => String(url || "http://localhost:8080").replace(/\/+$/, ""),
}));

import { fetchJson } from "../lib/api";

describe("wifiRepository", () => {
  let repo;

  beforeEach(() => {
    localStorage.clear();
    repo = createWifiRepository();
    vi.clearAllMocks();
  });

  afterEach(() => {
    localStorage.clear();
  });

  describe("saveParsedWifi", () => {
    it("stores a record in scanHistory", async () => {
      const record = await repo.saveParsedWifi(
        "http://localhost:8080",
        "WIFI:S:TestNet;P:pass1234;;",
        { ssid: "TestNet", password: "pass1234", sourceFormat: "wifi_qr", confidence: 0.98 },
      );
      expect(record.ssid).toBe("TestNet");
      expect(record.password).toBe("pass1234");
      expect(typeof record.id).toBe("number");
      expect(typeof record.createdAtMillis).toBe("number");
    });

    it("prepends new records to history", async () => {
      await repo.saveParsedWifi("http://localhost:8080", "text1", { ssid: "Net1" });
      await repo.saveParsedWifi("http://localhost:8080", "text2", { ssid: "Net2" });
      const history = await repo.getLatestSavedWifi();
      expect(history.ssid).toBe("Net2");
    });

    it("caps history at 30 records", async () => {
      for (let i = 0; i < 35; i++) {
        await repo.saveParsedWifi("http://localhost:8080", `text${i}`, { ssid: `Net${i}` });
      }
      const { getScanHistory } = await import("../lib/storage");
      expect(getScanHistory().length).toBe(30);
    });
  });

  describe("saveConnectedWifi", () => {
    it("saves network with password when savePassword is true", async () => {
      const record = await repo.saveConnectedWifi({
        ssid: "HomeNet",
        password: "homepass123",
        security: "WPA2",
        sourceFormat: "wifi_qr",
        savePassword: true,
      });
      expect(record.ssid).toBe("HomeNet");
      expect(record.password).toBe("homepass123");
      expect(record.passwordSaved).toBe(true);
    });

    it("saves network without password when savePassword is false", async () => {
      const record = await repo.saveConnectedWifi({
        ssid: "PubNet",
        password: "pubpass123",
        savePassword: false,
      });
      expect(record.password).toBeNull();
      expect(record.passwordSaved).toBe(false);
    });

    it("deduplicates by SSID (keeps latest)", async () => {
      await repo.saveConnectedWifi({ ssid: "DupNet", password: "pass1234A", savePassword: true });
      await repo.saveConnectedWifi({ ssid: "DupNet", password: "pass5678B", savePassword: true });
      const networks = await repo.getSavedNetworks();
      const dupNets = networks.filter((n) => n.ssid === "DupNet");
      expect(dupNets.length).toBe(1);
      expect(dupNets[0].password).toBe("pass5678B");
    });
  });

  describe("getSavedNetworks", () => {
    it("returns empty array when none saved", async () => {
      const networks = await repo.getSavedNetworks();
      expect(networks).toEqual([]);
    });

    it("returns networks sorted by lastConnectedAtMillis descending", async () => {
      await repo.saveConnectedWifi({ ssid: "OldNet", savePassword: false });
      await new Promise((r) => setTimeout(r, 5));
      await repo.saveConnectedWifi({ ssid: "NewNet", savePassword: false });
      const networks = await repo.getSavedNetworks();
      expect(networks[0].ssid).toBe("NewNet");
    });
  });

  describe("deleteSavedNetworkById", () => {
    it("removes network by id", async () => {
      const saved = await repo.saveConnectedWifi({ ssid: "ToDelete", savePassword: false });
      await repo.deleteSavedNetworkById(saved.id);
      const networks = await repo.getSavedNetworks();
      expect(networks.find((n) => n.id === saved.id)).toBeUndefined();
    });

    it("throws for unknown id", async () => {
      await repo.saveConnectedWifi({ ssid: "KeepMe", savePassword: false });
      await expect(repo.deleteSavedNetworkById(999999)).rejects.toThrow("Network not found");
      const networks = await repo.getSavedNetworks();
      expect(networks.length).toBe(1);
    });
  });

  describe("checkHealth", () => {
    it("calls fetchJson with /health endpoint", async () => {
      fetchJson.mockResolvedValue({ ok: true });
      await repo.checkHealth("http://localhost:8080");
      expect(fetchJson).toHaveBeenCalledWith("http://localhost:8080/health");
    });
  });
});
