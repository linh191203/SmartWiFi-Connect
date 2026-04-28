import { useState } from "react";
import { Link } from "react-router-dom";
import { useAppState } from "../context/AppState";

const sampleQrPayload = "WIFI:T:WPA;S:Office-5G;P:secure12345;;";

function CopyButton({ text }) {
  const [copied, setCopied] = useState(false);
  function handleCopy() {
    navigator.clipboard.writeText(text).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  }
  return (
    <button type="button" className="button-ghost compact-button" onClick={handleCopy}>
      {copied ? "Copied!" : "Copy"}
    </button>
  );
}

export default function QrScannerScreen() {
  const { state, actions } = useAppState();
  const [qrText, setQrText] = useState("");
  const [result, setResult] = useState(null);
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");

  async function handleSubmit(event) {
    event.preventDefault();
    setError("");
    setResult(null);
    setShowPassword(false);
    try {
      const draft = await actions.parseScan({ ocrText: qrText, sourceLabel: "wifi_qr" });
      setResult(draft);
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : "Unable to parse QR payload");
    }
  }

  function handleReset() {
    setResult(null);
    setQrText("");
    setError("");
    setShowPassword(false);
  }

  if (result) {
    return (
      <section className="screen-stack">
        <div className="panel section-stack">
          <p className="eyebrow">QR Result</p>
          <h3>Credentials found</h3>

          <div className="section-stack" style={{ gap: "0.75rem" }}>
            {/* SSID row */}
            <div className="field">
              <span>Network name (SSID)</span>
              <div style={{ display: "flex", gap: "0.5rem", alignItems: "center" }}>
                <input
                  style={{ flex: 1 }}
                  type="text"
                  value={result.ssid || "—"}
                  readOnly
                  autoComplete="off"
                />
                {result.ssid ? <CopyButton text={result.ssid} /> : null}
              </div>
            </div>

            {/* Password row */}
            <div className="field">
              <span>Password</span>
              <div style={{ display: "flex", gap: "0.5rem", alignItems: "center" }}>
                <input
                  style={{ flex: 1 }}
                  type={showPassword ? "text" : "password"}
                  value={result.password || ""}
                  readOnly
                  autoComplete="off"
                />
                <button
                  type="button"
                  className="button-ghost compact-button"
                  onClick={() => setShowPassword((v) => !v)}
                >
                  {showPassword ? "Hide" : "Show"}
                </button>
                {result.password ? <CopyButton text={result.password} /> : null}
              </div>
            </div>

            {/* Meta */}
            <dl className="meta-grid">
              <div>
                <dt>Security</dt>
                <dd>{result.security || "Unknown"}</dd>
              </div>
              <div>
                <dt>Source format</dt>
                <dd>{result.sourceFormat}</dd>
              </div>
              {result.confidence != null ? (
                <div>
                  <dt>Confidence</dt>
                  <dd>{result.confidence}</dd>
                </div>
              ) : null}
            </dl>
          </div>

          <div className="row-actions">
            <Link to="/review" className="button-primary compact-button">
              Continue to review
            </Link>
            <button type="button" className="button-secondary compact-button" onClick={handleReset}>
              Scan again
            </button>
          </div>
        </div>
      </section>
    );
  }

  return (
    <section className="screen-stack">
      <div className="panel section-stack">
        <p className="eyebrow">QR Scanner</p>
        <h3>Paste a Wi-Fi QR payload to extract credentials.</h3>
        <p className="muted">
          Copy the raw text from a Wi-Fi QR code (format: <code>WIFI:T:WPA;S:…;P:…;;</code>) and
          paste it below.
        </p>

        <form onSubmit={handleSubmit} className="section-stack">
          <label className="field">
            <span>QR payload</span>
            <textarea
              rows="5"
              value={qrText}
              onChange={(event) => setQrText(event.target.value)}
              placeholder="WIFI:T:WPA;S:MyNetwork;P:mypassword;;"
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
            <button
              type="submit"
              className="button-primary compact-button"
              disabled={state.busy || !qrText.trim()}
            >
              {state.busy ? "Parsing…" : "Parse QR"}
            </button>
          </div>

          {error ? <p className="error-text">{error}</p> : null}
        </form>
      </div>
    </section>
  );
}
