import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useAppState } from "../context/AppState";
import EmptyState from "../components/EmptyState";

export default function ReviewScreen() {
  const { state, actions } = useAppState();
  const [savePassword, setSavePassword] = useState(true);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  const draft = useMemo(() => state.currentDraft, [state.currentDraft]);

  async function handleConnect() {
    setMessage("");
    setError("");
    try {
      await actions.connectCurrent({ savePassword });
      setMessage("Network saved to browser history. Direct Wi-Fi connection is not available on web.");
    } catch (connectError) {
      setError(connectError instanceof Error ? connectError.message : "Unable to save network");
    }
  }

  if (!draft) {
    return (
      <EmptyState
        title="No current Wi-Fi draft"
        body="Run OCR parse, QR parse, or manual entry first."
        action={
          <Link to="/home" className="button-primary compact-button">
            Go back home
          </Link>
        }
      />
    );
  }

  return (
    <section className="screen-stack">
      <div className="panel section-stack">
        <div className="card-head">
          <div>
            <p className="eyebrow">Review Result</p>
            <h3>{draft.ssid || "Password-only result"}</h3>
          </div>
          <span className="badge">{draft.sourceFormat}</span>
        </div>

        <div className="field-grid">
          <label className="field">
            <span>SSID</span>
            <input
              value={draft.ssid}
              onChange={(event) => actions.updateCurrentDraft({ ssid: event.target.value })}
            />
          </label>
          <label className="field">
            <span>Password</span>
            <input
              value={draft.password}
              onChange={(event) => actions.updateCurrentDraft({ password: event.target.value })}
            />
          </label>
          <label className="field">
            <span>Security</span>
            <input
              value={draft.security}
              onChange={(event) => actions.updateCurrentDraft({ security: event.target.value })}
            />
          </label>
          <label className="field">
            <span>Confidence</span>
            <input value={draft.confidence ?? "-"} disabled />
          </label>
        </div>

        <label className="checkbox-row">
          <input
            type="checkbox"
            checked={savePassword}
            onChange={(event) => setSavePassword(event.target.checked)}
          />
          <span>Save password in this browser</span>
        </label>

        {draft.ocrText ? (
          <label className="field">
            <span>OCR / QR input</span>
            <textarea value={draft.ocrText} rows="6" disabled />
          </label>
        ) : null}

        <div className="row-actions">
          <button type="button" className="button-primary compact-button" onClick={handleConnect} disabled={state.busy}>
            {state.busy ? "Saving..." : "Save network"}
          </button>
          <Link to="/history" className="button-secondary compact-button">
            Open history
          </Link>
        </div>

        {message ? <p className="success-text">{message}</p> : null}
        {error ? <p className="error-text">{error}</p> : null}
      </div>
    </section>
  );
}