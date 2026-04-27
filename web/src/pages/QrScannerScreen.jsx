import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAppState } from "../context/AppState";

const sampleQrPayload = "WIFI:T:WPA;S:Office-5G;P:secure12345;;";

export default function QrScannerScreen() {
  const navigate = useNavigate();
  const { state, actions } = useAppState();
  const [qrText, setQrText] = useState(sampleQrPayload);
  const [error, setError] = useState("");

  async function handleSubmit(event) {
    event.preventDefault();
    setError("");
    try {
      await actions.parseScan({ ocrText: qrText, sourceLabel: "wifi_qr" });
      navigate("/review");
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : "Unable to parse QR payload");
    }
  }

  return (
    <section className="screen-stack">
      <div className="panel section-stack">
        <p className="eyebrow">QR Scanner</p>
        <h3>Paste a Wi-Fi QR payload.</h3>
        <p className="muted">
          Camera scanning is not included in this first React port. The parser still supports the
          same WIFI: format used by the Android flow.
        </p>

        <form onSubmit={handleSubmit} className="section-stack">
          <label className="field">
            <span>QR payload</span>
            <textarea
              rows="6"
              value={qrText}
              onChange={(event) => setQrText(event.target.value)}
            />
          </label>

          <div className="row-actions">
            <button
              type="button"
              className="button-secondary compact-button"
              onClick={() => setQrText(sampleQrPayload)}
            >
              Use sample QR
            </button>
            <button type="submit" className="button-primary compact-button" disabled={state.busy}>
              {state.busy ? "Parsing..." : "Parse QR payload"}
            </button>
          </div>

          {error ? <p className="error-text">{error}</p> : null}
        </form>
      </div>
    </section>
  );
}