import { useState } from "react";
import { useAppState } from "../context/AppState";

export default function SettingsScreen() {
  const { state, actions } = useAppState();
  const [baseUrl, setBaseUrl] = useState(state.apiBaseUrl);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  async function handleHealthCheck() {
    setMessage("");
    setError("");
    try {
      await actions.checkApiHealth();
      setMessage("Backend responded successfully.");
    } catch (healthError) {
      setError(healthError instanceof Error ? healthError.message : "Health check failed");
    }
  }

  function handleSave() {
    actions.setApiBaseUrl(baseUrl);
    setMessage("API base URL saved in browser storage.");
    setError("");
  }

  return (
    <section className="screen-stack">
      <div className="panel section-stack">
        <p className="eyebrow">Settings</p>
        <h3>Backend and platform notes</h3>
        <label className="field">
          <span>API base URL</span>
          <input value={baseUrl} onChange={(event) => setBaseUrl(event.target.value)} />
        </label>
        <div className="row-actions">
          <button type="button" className="button-primary compact-button" onClick={handleSave}>
            Save URL
          </button>
          <button
            type="button"
            className="button-secondary compact-button"
            onClick={handleHealthCheck}
          >
            Ping backend
          </button>
        </div>
        <ul className="notes-list muted">
          <li>OCR parsing is preserved through the existing Node backend.</li>
          <li>Browser localStorage replaces Android SQLite/Room persistence for saved networks.</li>
          <li>Direct Wi-Fi connection and camera-native flows are still platform-limited on web.</li>
        </ul>
        {message ? <p className="success-text">{message}</p> : null}
        {error ? <p className="error-text">{error}</p> : null}
      </div>
    </section>
  );
}