import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAppState } from "../context/AppState";

export default function ManualEntryScreen() {
  const navigate = useNavigate();
  const { actions } = useAppState();
  const [form, setForm] = useState({
    ssid: "",
    password: "",
    security: "WPA/WPA2",
  });
  const [showPassword, setShowPassword] = useState(false);

  function handleSubmit(event) {
    event.preventDefault();
    actions.saveManualDraft(form);
    navigate("/review");
  }

  return (
    <section className="screen-stack">
      <form className="panel section-stack" onSubmit={handleSubmit}>
        <p className="eyebrow">Manual Entry</p>
        <h3>Enter Wi-Fi details directly.</h3>
        <label className="field">
          <span>SSID</span>
          <input
            value={form.ssid}
            onChange={(event) => setForm({ ...form, ssid: event.target.value })}
            required
          />
        </label>
        <div className="field">
          <span>Password</span>
          <div style={{ display: "flex", gap: "0.5rem" }}>
            <input
              style={{ flex: 1 }}
              type={showPassword ? "text" : "password"}
              value={form.password}
              onChange={(event) => setForm({ ...form, password: event.target.value })}
              autoComplete="new-password"
            />
            <button
              type="button"
              className="button-ghost compact-button"
              onClick={() => setShowPassword((v) => !v)}
            >
              {showPassword ? "Hide" : "Show"}
            </button>
          </div>
        </div>
        <label className="field">
          <span>Security</span>
          <select
            value={form.security}
            onChange={(event) => setForm({ ...form, security: event.target.value })}
          >
            <option>WPA/WPA2</option>
            <option>WPA3</option>
            <option>Open</option>
            <option>Unknown</option>
          </select>
        </label>
        <button type="submit" className="button-primary compact-button">
          Continue to review
        </button>
      </form>
    </section>
  );
}