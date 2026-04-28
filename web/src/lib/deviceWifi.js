import { Capacitor, registerPlugin } from "@capacitor/core";

const DeviceWifi = registerPlugin("DeviceWifi");

export function supportsNativeWifiStatus() {
  return Capacitor.getPlatform() === "android";
}

export async function getDeviceWifiStatus() {
  if (!supportsNativeWifiStatus()) {
    return {
      wifiEnabled: typeof navigator !== "undefined" ? navigator.onLine : true,
      platform: Capacitor.getPlatform(),
      native: false,
    };
  }

  const result = await DeviceWifi.getStatus();
  return { ...result, native: true };
}

export async function openDeviceWifiSettings() {
  if (!supportsNativeWifiStatus()) {
    return { opened: false };
  }

  return DeviceWifi.openSettings();
}