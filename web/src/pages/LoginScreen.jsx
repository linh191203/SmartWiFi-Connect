import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAppState } from "../context/AppState";

export default function LoginScreen() {
  const navigate = useNavigate();
  const { actions } = useAppState();
  const [form, setForm] = useState({ email: "", password: "" });

  function handleSubmit(event) {
    event.preventDefault();
    actions.saveUser({
      name: form.email.split("@")[0] || "Demo User",
      email: form.email,
    });
    navigate("/home");
  }

  return (
    <div className="auth-shell">
      <form className="auth-card" onSubmit={handleSubmit}>
        <p className="eyebrow">Welcome back</p>
        <h1>Login</h1>
        <label className="field">
          <span>Email</span>
          <input
            type="email"
            value={form.email}
            onChange={(event) => setForm({ ...form, email: event.target.value })}
            placeholder="you@example.com"
            required
          />
        </label>
        <label className="field">
          <span>Password</span>
          <input
            type="password"
            value={form.password}
            onChange={(event) => setForm({ ...form, password: event.target.value })}
            placeholder="Enter your password"
            required
          />
        </label>
        <button type="submit" className="button-primary full-width">
          Continue to dashboard
        </button>
        <p className="muted centered">
          No account yet? <Link to="/register">Create one</Link>
        </p>
      </form>
    </div>
  );
}