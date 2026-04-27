import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAppState } from "../context/AppState";

export default function RegisterScreen() {
  const navigate = useNavigate();
  const { actions } = useAppState();
  const [form, setForm] = useState({
    name: "",
    email: "",
    password: "",
    confirmPassword: "",
  });

  function handleSubmit(event) {
    event.preventDefault();
    if (form.password !== form.confirmPassword) {
      return;
    }

    actions.saveUser({ name: form.name, email: form.email });
    navigate("/home");
  }

  return (
    <div className="auth-shell">
      <form className="auth-card" onSubmit={handleSubmit}>
        <p className="eyebrow">Create account</p>
        <h1>Register</h1>
        <label className="field">
          <span>Full name</span>
          <input
            value={form.name}
            onChange={(event) => setForm({ ...form, name: event.target.value })}
            placeholder="Nguyen Van A"
            required
          />
        </label>
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
            required
          />
        </label>
        <label className="field">
          <span>Confirm password</span>
          <input
            type="password"
            value={form.confirmPassword}
            onChange={(event) => setForm({ ...form, confirmPassword: event.target.value })}
            required
          />
        </label>
        <button type="submit" className="button-primary full-width">
          Create account
        </button>
        <p className="muted centered">
          Already have an account? <Link to="/login">Login</Link>
        </p>
      </form>
    </div>
  );
}