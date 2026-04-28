import { useEffect } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAppState } from "../context/AppState";
import WifiReminderBanner from "../components/WifiReminderBanner";

export default function OnboardingScreen() {
  const { state } = useAppState();
  const navigate = useNavigate();

  useEffect(() => {
    if (state.user.name) {
      navigate("/home", { replace: true });
    }
  }, [state.user.name, navigate]);

  return (
    <div className="landing-shell">
      <div className="landing-stack">
        <WifiReminderBanner />

        <section className="hero-card">
          <div>
            <p className="eyebrow">SmartWiFi Connect</p>
            <h1>Save your Wi-Fi credentials in seconds.</h1>
            <p className="hero-copy">
              Paste OCR text from a photo, scan a QR code, or enter details manually.
              Passwords are stored securely — copy them anytime from your history.
            </p>
            <div className="hero-actions">
              <Link to="/login" className="button-primary">
                Get started
              </Link>
              <Link to="/home" className="button-secondary">
                Continue as guest
              </Link>
            </div>
          </div>

          <div className="hero-panel">
            <div className="signal-orb" />
            <div className="metric-tile">
              <strong>OCR</strong>
              <span>Extract credentials from any text</span>
            </div>
            <div className="metric-tile">
              <strong>History</strong>
              <span>All saved networks in one place</span>
            </div>
            <div className="metric-tile">
              <strong>Secure</strong>
              <span>Passwords encrypted with your own passphrase</span>
            </div>
          </div>
        </section>
      </div>
    </div>
  );
}