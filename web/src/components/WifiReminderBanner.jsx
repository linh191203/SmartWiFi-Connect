import { useEffect, useState } from "react";
import { getDeviceWifiStatus, openDeviceWifiSettings, supportsNativeWifiStatus } from "../lib/deviceWifi";

function createInitialState() {
  return {
    loading: true,
    showReminder: false,
    native: supportsNativeWifiStatus(),
  };
}

export default function WifiReminderBanner() {
  const [status, setStatus] = useState(createInitialState);

  useEffect(() => {
    let active = true;

    async function refreshStatus() {
      try {
        const result = await getDeviceWifiStatus();
        if (!active) {
          return;
        }

        setStatus({
          loading: false,
          showReminder: !result.wifiEnabled,
          native: Boolean(result.native),
        });
      } catch {
        if (!active) {
          return;
        }

        setStatus({ loading: false, showReminder: false, native: supportsNativeWifiStatus() });
      }
    }

    function handleNetworkChange() {
      refreshStatus().catch(() => {});
    }

    refreshStatus().catch(() => {});
    window.addEventListener("online", handleNetworkChange);
    window.addEventListener("offline", handleNetworkChange);
    document.addEventListener("visibilitychange", handleNetworkChange);

    return () => {
      active = false;
      window.removeEventListener("online", handleNetworkChange);
      window.removeEventListener("offline", handleNetworkChange);
      document.removeEventListener("visibilitychange", handleNetworkChange);
    };
  }, []);

  async function handleOpenWifiSettings() {
    try {
      await openDeviceWifiSettings();
    } catch {
      // Ignore settings open failures and keep the reminder visible.
    }
  }

  if (status.loading || !status.showReminder) {
    return null;
  }

  return (
    <section className="wifi-reminder" role="status" aria-live="polite">
      <div>
        <p className="eyebrow">Wi-Fi reminder</p>
        <h3>Turn on Wi-Fi before you continue.</h3>
        <p className="muted">
          Open Wi-Fi on the device first, then come back to scan, review, or save a network.
        </p>
      </div>
      {status.native ? (
        <button type="button" className="button-secondary wifi-reminder-action" onClick={handleOpenWifiSettings}>
          Open Wi-Fi settings
        </button>
      ) : null}
    </section>
  );
}