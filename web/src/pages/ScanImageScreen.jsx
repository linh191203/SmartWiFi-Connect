import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAppState } from "../context/AppState";

const sampleOcr = "WIFI: Cafe-Guest\nPass: 12345678";

export default function ScanImageScreen() {
  const navigate = useNavigate();
  const { state, actions } = useAppState();
  const [ocrText, setOcrText] = useState(sampleOcr);
  const [error, setError] = useState("");

  async function handleSubmit(event) {
    event.preventDefault();
    setError("");
    try {
      await actions.parseScan({ ocrText, sourceLabel: "ocr_text" });
      navigate("/review");
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : "Unable to parse OCR text");
    }
  }

  return (
    <section className="screen-stack">
      <div className="panel section-stack">
        <p className="eyebrow">OCR Result</p>
        <h3>Paste OCR text and parse it with the existing backend.</h3>
        <p className="muted">
          The Android app uses ML Kit before calling the parser. This web port keeps the parser and
          review flow, while text capture is manual in the browser.
        </p>

        <form onSubmit={handleSubmit} className="section-stack">
          <label className="field">
            <span>OCR text</span>
            <textarea
              rows="10"
              value={ocrText}
              onChange={(event) => setOcrText(event.target.value)}
              placeholder="Paste OCR output here"
            />
          </label>

          <div className="row-actions">
            <button type="button" className="button-secondary compact-button" onClick={() => setOcrText(sampleOcr)}>
              Use sample text
            </button>
            <button type="submit" className="button-primary compact-button" disabled={state.busy}>
              {state.busy ? "Parsing..." : "Parse OCR text"}
            </button>
          </div>

          {error ? <p className="error-text">{error}</p> : null}
        </form>
      </div>
    </section>
  );
}