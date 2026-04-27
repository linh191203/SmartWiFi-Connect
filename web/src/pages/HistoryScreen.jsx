import { useState } from "react";
import { useAppState } from "../context/AppState";
import EmptyState from "../components/EmptyState";
import NetworkCard from "../components/NetworkCard";

export default function HistoryScreen() {
  const { state, actions } = useAppState();
  const [revealPasswords, setRevealPasswords] = useState(false);

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
              onClick={() => setRevealPasswords((value) => !value)}
            >
              {revealPasswords ? "Hide passwords" : "Reveal passwords"}
            </button>
            <button
              type="button"
              className="button-ghost compact-button"
              onClick={() => actions.clearSavedNetworks()}
            >
              Clear all
            </button>
          </div>
        </div>
      </div>

      {state.savedNetworks.map((network) => (
        <NetworkCard
          key={network.id}
          network={network}
          revealPassword={revealPasswords}
          actions={
            <button
              type="button"
              className="button-ghost compact-button"
              onClick={() => actions.deleteSavedNetworkById(network.id)}
            >
              Delete
            </button>
          }
        />
      ))}
    </section>
  );
}