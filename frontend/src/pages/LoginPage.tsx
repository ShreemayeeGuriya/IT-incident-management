import { useState, type FormEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { isAxiosError } from "axios";
import { useAuth } from "../auth/AuthContext";

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      await login({ email, password });
      navigate("/");
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 401) {
        setError("Incorrect email or password.");
      } else {
        setError("Could not sign in. Please try again.");
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <div style={{ maxWidth: 360, margin: "80px auto", padding: 24 }}>
      <h2 style={{ marginTop: 0 }}>Sign in to Radar</h2>
      {params.get("expired") && (
        <p style={{ color: "var(--muted)" }}>Your session expired. Please sign in again.</p>
      )}
      <form onSubmit={handleSubmit} style={{ display: "flex", flexDirection: "column", gap: 12 }}>
        <label>
          Email
          <input
            type="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            style={{ width: "100%", padding: 8, marginTop: 4 }}
          />
        </label>
        <label>
          Password
          <input
            type="password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            style={{ width: "100%", padding: 8, marginTop: 4 }}
          />
        </label>
        {error && <div style={{ color: "var(--p1)" }}>{error}</div>}
        <button type="submit" disabled={busy} style={{ padding: 8 }}>
          {busy ? "Signing in..." : "Sign in"}
        </button>
      </form>
      <p>
        No account? <Link to="/register">Register</Link>
      </p>
    </div>
  );
}