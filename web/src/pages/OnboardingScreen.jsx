import { Link } from "react-router-dom";

export default function OnboardingScreen() {
  return (
    <div className="landing-shell">
      <section className="hero-card">
        <div>
          <p className="eyebrow">Android logic, React UI</p>
          <h1>Scan Wi-Fi details faster, now in the browser.</h1>
          <p className="hero-copy">
            This port keeps the same app flow: onboarding, login, OCR parse, QR parse, review,
            save, and history. Browser limits still apply for direct Wi-Fi connection.
          </p>
          <div className="hero-actions">
            <Link to="/login" className="button-primary">
              Start demo
            </Link>
            <Link to="/home" className="button-secondary">
              Skip to dashboard
            </Link>
          </div>
        </div>

        <div className="hero-panel">
          <div className="signal-orb" />
          <div className="metric-tile">
            <strong>OCR</strong>
            <span>Uses the existing backend parser</span>
          </div>
          <div className="metric-tile">
            <strong>History</strong>
            <span>Browser localStorage mirrors the Android repository</span>
          </div>
          <div className="metric-tile">
            <strong>Review</strong>
            <span>Edit SSID, password, security, then save the draft</span>
          </div>
        </div>
      </section>
    </div>
  );
}