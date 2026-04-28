import { useState } from "react";
import { useAppState } from "../context/AppState";
import EmptyState from "../components/EmptyState";
import NetworkCard from "../components/NetworkCard";

export default function HistoryScreen() {
  const { state, actions } = useAppState();
  const [revealPasswords, setRevealPasswords] = useState(false);
  const [passphrase, setPassphrase] = useState("");
  const [decryptedPasswords, setDecryptedPasswords] = useState({});
  const [passphraseError, setPassphraseError] = useState("");
  const [decrypting, setDecrypting] = useState(false);
  const [query, setQuery] = useState("");
  const [confirmClearAll, setConfirmClearAll] = useState(false);
  const [confirmDeleteId, setConfirmDeleteId] = useState(null);

  const hasEncrypted = state.savedNetworks.some((n) => n.passwordEncrypted);
  const filteredNetworks = state.savedNetworks.filter((n) =>
    n.ssid?.toLowerCase().includes(query.toLowerCase()),
  );

  async function handleRevealToggle() {
    if (revealPasswords) {
      setRevealPasswords(false);
      setDecryptedPasswords({});
      setPassphrase("");
      setPassphraseError("");
      return;
    }
    if (!hasEncrypted) {
      setRevealPasswords(true);
      return;
    }
    setRevealPasswords(true);
  }

  function handleClearAll() {
    actions.clearSavedNetworks();
    setConfirmClearAll(false);
  }

  function handleDelete(id) {
    actions.deleteSavedNetworkById(id);
    setConfirmDeleteId(null);
  }

  async function handleDecryptAll() {
    if (!passphrase) {
      setPassphraseError("Please enter your passphrase.");
      return;
    }
    setDecrypting(true);
    setPassphraseError("");
    try {
      const results = {};
      for (const network of state.savedNetworks) {
        if (network.passwordEncrypted && network.password) {
          results[network.id] = await actions.decryptNetworkPassword(network.id, passphrase);
        }
      }
      setDecryptedPasswords(results);
    } catch {
      setPassphraseError("Incorrect passphrase. Please try again.");
      setDecryptedPasswords({});
    } finally {
      setDecrypting(false);
    }
  }

  if (state.savedNetworks.length === 0) {
    return (
      <EmptyState
        title="No saved networks"
        body="Save at least one reviewed network and it will appear here."
      />
    );
  }

  return (
    <section className="screen-stack">
      <div className="panel section-stack">
        <div className="card-head">
          <div>
            <p className="eyebrow">History</p>
            <h3>{state.savedNetworks.length} saved networks</h3>
          </div>
          <div className="row-actions">
            <button
              type="button"
              className="button-secondary compact-button"
              onClick={handleRevealToggle}
            >
              {revealPasswords ? "Hide passwords" : "Reveal passwords"}
            </button>
            {confirmClearAll ? (
              <>
                <span className="hint-text">Delete all?</span>
                <button
                  type="button"
                  className="button-ghost compact-button"
                  style={{ color: "var(--color-danger, #dc2626)" }}
                  onClick={handleClearAll}
                >
                  Yes, delete all
                </button>
                <button
                  type="button"
                  className="button-ghost compact-button"
                  onClick={() => setConfirmClearAll(false)}
                >
                  Cancel
                </button>
              </>
            ) : (
              <button
                type="button"
                className="button-ghost compact-button"
                onClick={() => setConfirmClearAll(true)}
              >
                Clear all
              </button>
            )}
          </div>
        </div>

        <label className="field">
          <span>Search networks</span>
          <input
            type="search"
            placeholder="Filter by network name…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </label>

        {revealPasswords && hasEncrypted && (
          <div className="section-stack">
            <p className="hint-text">Some passwords are encrypted. Enter your passphrase to decrypt them.</p>
            <div className="row-actions">
              <label className="field" style={{ flex: 1 }}>
                <span>Passphrase</span>
                <input
                  type="password"
                  placeholder="Enter passphrase to decrypt"
                  value={passphrase}
                  onChange={(e) => setPassphrase(e.target.value)}
                  onKeyDown={(e) => e.key === "Enter" && handleDecryptAll()}
                  autoComplete="current-password"
                />
              </label>
              <button
                type="button"
                className="button-primary compact-button"
                onClick={handleDecryptAll}
                disabled={decrypting}
                style={{ alignSelf: "flex-end" }}
              >
                {decrypting ? "Decrypting..." : "Unlock"}
              </button>
            </div>
            {passphraseError ? <p className="error-text">{passphraseError}</p> : null}
          </div>
        )}
      </div>

      {filteredNetworks.length === 0 && query ? (
        <EmptyState
          title="No matching networks"
          body={`No saved networks match "${query}".`}
        />
      ) : null}

      {filteredNetworks.map((network) => (
        <NetworkCard
          key={network.id}
          network={network}
          revealPassword={revealPasswords}
          decryptedPassword={decryptedPasswords[network.id]}
          actions={
            confirmDeleteId === network.id ? (
              <>
                <span className="hint-text">Delete this network?</span>
                <button
                  type="button"
                  className="button-ghost compact-button"
                  style={{ color: "var(--color-danger, #dc2626)" }}
                  onClick={() => handleDelete(network.id)}
                >
                  Yes, delete
                </button>
                <button
                  type="button"
                  className="button-ghost compact-button"
                  onClick={() => setConfirmDeleteId(null)}
                >
                  Cancel
                </button>
              </>
            ) : (
              <button
                type="button"
                className="button-ghost compact-button"
                onClick={() => setConfirmDeleteId(network.id)}
              >
                Delete
              </button>
            )
          }
        />
      ))}
    </section>
  );
}