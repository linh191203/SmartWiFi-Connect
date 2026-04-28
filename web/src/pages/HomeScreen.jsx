import { Link } from "react-router-dom";
import { useEffect } from "react";
import { useAppState } from "../context/AppState";
import { formatTime } from "../lib/format";
import EmptyState from "../components/EmptyState";
import NetworkCard from "../components/NetworkCard";
import WifiReminderBanner from "../components/WifiReminderBanner";

const actionsList = [
  {
    title: "Paste OCR text",
    body: "Reuse the existing OCR parsing API and jump into review.",
    to: "/scan-image",
  },
  {
    title: "Parse Wi-Fi QR payload",
    body: "Use the same backend parser because it already understands WIFI: QR format.",
    to: "/scan-qr",
  },
  {
    title: "Manual entry",
    body: "Skip parsing and edit SSID, password, and security directly.",
    to: "/manual",
  },
];

export default function HomeScreen() {
  const { state, actions } = useAppState();

  useEffect(() => {
    if (!state.health.ok && !state.health.error) {
      actions.checkApiHealth().catch(() => {});
    }
  }, [actions, state.health.error, state.health.ok]);

  return (
    <div className="screen-stack">
      <WifiReminderBanner />

      <section className="hero-strip panel">
        <div>
          <p className="eyebrow">Dashboard</p>
          <h3>Save and manage your Wi-Fi credentials.</h3>
          <p className="muted">
            Capture Wi-Fi details from a photo, QR code, or enter them manually — then save them
            securely on your device. Copy your credentials anytime from history.
          </p>
        </div>
        <div className="summary-box">
          <strong>{state.savedSummary.count}</strong>
          <span>saved networks</span>
          <small>{state.savedSummary.latestSsid || "No networks saved yet"}</small>
        </div>
      </section>

      <section className="action-grid">
        {actionsList.map((item) => (
          <Link key={item.to} to={item.to} className="panel action-card">
            <p className="eyebrow">Quick action</p>
            <h3>{item.title}</h3>
            <p className="muted">{item.body}</p>
          </Link>
        ))}
      </section>

      <section className="panel section-stack">
        <div className="card-head">
          <div>
            <p className="eyebrow">Latest parsed scan</p>
            <h3>{state.latestScan?.ssid || "No OCR result yet"}</h3>
          </div>
          <Link to="/review" className="button-secondary compact-button">
            Open review
          </Link>
        </div>
        {state.latestScan ? (
          <dl className="meta-grid">
            <div>
              <dt>Source</dt>
              <dd>{state.latestScan.sourceFormat}</dd>
            </div>
            <div>
              <dt>Confidence</dt>
              <dd>{state.latestScan.confidence ?? "-"}</dd>
            </div>
            <div>
              <dt>Captured</dt>
              <dd>{formatTime(state.latestScan.createdAtMillis)}</dd>
            </div>
            <div>
              <dt>Backend</dt>
              <dd>{state.apiBaseUrl}</dd>
            </div>
          </dl>
        ) : (
          <EmptyState
            title="No scan result cached"
            body="Run OCR or QR parse once and the latest scan will be available here."
            action={
              <Link to="/scan-image" className="button-primary compact-button">
                Parse first OCR text
              </Link>
            }
          />
        )}
      </section>

      <section className="screen-stack">
        <div className="card-head">
          <div>
            <p className="eyebrow">Saved history</p>
            <h3>Recent networks</h3>
          </div>
          <Link to="/history" className="button-secondary compact-button">
            View all
          </Link>
        </div>
        {state.savedNetworks.length === 0 ? (
          <EmptyState
            title="No saved networks"
            body="Use the review screen to simulate connect-and-save from the current Wi-Fi draft."
          />
        ) : (
          state.savedNetworks.slice(0, 3).map((network) => (
            <NetworkCard key={network.id} network={network} />
          ))
        )}
      </section>
    </div>
  );
}