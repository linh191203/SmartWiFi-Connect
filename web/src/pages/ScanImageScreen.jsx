import { useState } from "react";
import { Link } from "react-router-dom";
import { useAppState } from "../context/AppState";

const sampleOcr = "WIFI: Cafe-Guest\nPass: 12345678";

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

export default function ScanImageScreen() {
  const { state, actions } = useAppState();
  const [ocrText, setOcrText] = useState("");
  const [result, setResult] = useState(null);
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");

  async function handleSubmit(event) {
    event.preventDefault();
    setError("");
    setResult(null);
    setShowPassword(false);
    try {
      const draft = await actions.parseScan({ ocrText, sourceLabel: "ocr_text" });
      setResult(draft);
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : "Unable to parse OCR text");
    }
  }

  function handleReset() {
    setResult(null);
    setOcrText("");
    setError("");
    setShowPassword(false);
  }

  if (result) {
    return (
      <section className="screen-stack">
        <div className="panel section-stack">
          <p className="eyebrow">OCR Result</p>
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
        <p className="eyebrow">OCR Parse</p>
        <h3>Paste OCR text to extract Wi-Fi credentials.</h3>
        <p className="muted">
          Copy the text from a photo or document containing Wi-Fi details, paste it below, and the
          parser will extract the network name and password for you.
        </p>

        <form onSubmit={handleSubmit} className="section-stack">
          <label className="field">
            <span>OCR text</span>
            <textarea
              rows="8"
              value={ocrText}
              onChange={(event) => setOcrText(event.target.value)}
              placeholder="Paste your OCR output here…"
            />
          </label>

          <div className="row-actions">
            <button
              type="button"
              className="button-secondary compact-button"
              onClick={() => setOcrText(sampleOcr)}
            >
              Use sample text
            </button>
            <button
              type="submit"
              className="button-primary compact-button"
              disabled={state.busy || !ocrText.trim()}
            >
              {state.busy ? "Parsing…" : "Parse text"}
            </button>
          </div>

          {error ? <p className="error-text">{error}</p> : null}
        </form>
      </div>
    </section>
  );
}
