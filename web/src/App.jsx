import { NavLink, Navigate, Outlet, Route, Routes } from "react-router-dom";
import { useAppState } from "./context/AppState";
import OnboardingScreen from "./pages/OnboardingScreen";
import LoginScreen from "./pages/LoginScreen";
import RegisterScreen from "./pages/RegisterScreen";
import HomeScreen from "./pages/HomeScreen";
import ScanImageScreen from "./pages/ScanImageScreen";
import QrScannerScreen from "./pages/QrScannerScreen";
import ManualEntryScreen from "./pages/ManualEntryScreen";
import ReviewScreen from "./pages/ReviewScreen";
import HistoryScreen from "./pages/HistoryScreen";
import SettingsScreen from "./pages/SettingsScreen";

const navItems = [
  { to: "/home", label: "Home" },
  { to: "/scan-image", label: "OCR" },
  { to: "/scan-qr", label: "QR" },
  { to: "/history", label: "History" },
  { to: "/settings", label: "Settings" },
];

function AppLayout() {
  const { state } = useAppState();

  return (
    <div className="app-frame">
      <aside className="sidebar">
        <div>
          <p className="eyebrow">SmartWiFi Connect</p>
          <h1>React Port</h1>
          <p className="muted">
            Web frontend rebuilt from the existing Android flow, repository behavior, and OCR API.
          </p>
        </div>

        <nav className="nav-list" aria-label="Primary">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                isActive ? "nav-link nav-link-active" : "nav-link"
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className="sidebar-card">
          <span className="status-dot" data-state={state.health.ok ? "ok" : "idle"} />
          <div>
            <p className="sidebar-title">Backend</p>
            <p className="muted">
              {state.health.ok
                ? `Online at ${state.apiBaseUrl}`
                : state.health.error || "Health check not run yet"}
            </p>
          </div>
        </div>
      </aside>

      <main className="content-shell">
        <header className="topbar">
          <div>
            <p className="eyebrow">Current user</p>
            <h2>{state.user.name || "Guest mode"}</h2>
          </div>
          <div className="topbar-meta">
            <div className="stat-chip">
              <strong>{state.savedSummary.count}</strong>
              <span>saved networks</span>
            </div>
            <div className="stat-chip">
              <strong>{state.savedSummary.latestSsid || "-"}</strong>
              <span>latest SSID</span>
            </div>
          </div>
        </header>
        <Outlet />
      </main>
    </div>
  );
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<OnboardingScreen />} />
      <Route path="/login" element={<LoginScreen />} />
      <Route path="/register" element={<RegisterScreen />} />
      <Route element={<AppLayout />}>
        <Route path="/home" element={<HomeScreen />} />
        <Route path="/scan-image" element={<ScanImageScreen />} />
        <Route path="/scan-qr" element={<QrScannerScreen />} />
        <Route path="/manual" element={<ManualEntryScreen />} />
        <Route path="/review" element={<ReviewScreen />} />
        <Route path="/history" element={<HistoryScreen />} />
        <Route path="/settings" element={<SettingsScreen />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}