import { useState } from "react";
import { formatTime, maskPassword } from "../lib/format";

export default function NetworkCard({ network, revealPassword = false, decryptedPassword, actions }) {
  const [copiedField, setCopiedField] = useState(null);

  function copyToClipboard(text, field) {
    navigator.clipboard.writeText(text).then(() => {
      setCopiedField(field);
      setTimeout(() => setCopiedField(null), 2000);
    });
  }

  function getRevealedPassword() {
    if (!network.passwordSaved || !revealPassword) return null;
    if (network.passwordEncrypted) return decryptedPassword ?? null;
    return network.password;
  }

  function renderPassword() {
    if (!network.passwordSaved) return "Not stored";
    if (!revealPassword) return network.passwordEncrypted ? "🔒 Encrypted" : maskPassword(network.password, false);
    if (network.passwordEncrypted) {
      return decryptedPassword != null ? decryptedPassword : "🔒 Enter passphrase to reveal";
    }
    return maskPassword(network.password, true);
  }

  const copyablePassword = getRevealedPassword();

  return (
    <article className="panel card-stack">
      <div className="card-head">
        <div>
          <p className="eyebrow">Saved network</p>
          <h3>{network.ssid}</h3>
        </div>
        <div className="row-actions">
          <span className="badge">{network.security || "Unknown"}</span>
          {network.passwordEncrypted ? <span className="badge">Encrypted</span> : null}
          <button
            type="button"
            className="button-ghost compact-button"
            onClick={() => copyToClipboard(network.ssid, "ssid")}
          >
            {copiedField === "ssid" ? "Copied!" : "Copy SSID"}
          </button>
        </div>
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
          <dd>
            {renderPassword()}
            {copyablePassword ? (
              <button
                type="button"
                className="button-ghost compact-button"
                style={{ marginLeft: "0.5rem" }}
                onClick={() => copyToClipboard(copyablePassword, "password")}
              >
                {copiedField === "password" ? "Copied!" : "Copy"}
              </button>
            ) : null}
          </dd>
        </div>
        <div>
          <dt>Saved</dt>
          <dd>{network.passwordSaved ? (network.passwordEncrypted ? "Encrypted in browser" : "Stored in browser") : "Not stored"}</dd>
        </div>
      </dl>

      {actions ? <div className="row-actions">{actions}</div> : null}
    </article>
  );
}