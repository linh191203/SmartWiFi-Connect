import { formatTime, maskPassword } from "../lib/format";

export default function NetworkCard({ network, revealPassword = false, actions }) {
  return (
    <article className="panel card-stack">
      <div className="card-head">
        <div>
          <p className="eyebrow">Saved network</p>
          <h3>{network.ssid}</h3>
        </div>
        <span className="badge">{network.security || "Unknown"}</span>
      </div>

      <dl className="meta-grid">
        <div>
          <dt>Source</dt>
          <dd>{network.sourceFormat}</dd>
        </div>
        <div>
          <dt>Last connected</dt>
          <dd>{formatTime(network.lastConnectedAtMillis)}</dd>
        </div>
        <div>
          <dt>Password</dt>
          <dd>{maskPassword(network.password, revealPassword)}</dd>
        </div>
        <div>
          <dt>Saved</dt>
          <dd>{network.passwordSaved ? "Stored in browser" : "Not stored"}</dd>
        </div>
      </dl>

      {actions ? <div className="row-actions">{actions}</div> : null}
    </article>
  );
}