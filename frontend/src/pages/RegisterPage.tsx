import { useState, type FormEvent } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { ApiError } from "../api/client";

export default function RegisterPage() {
  const { user, register } = useAuth();
  const nav = useNavigate();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to="/" replace />;

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError("");
    setFieldErrors({});

    if (password.length < 8) return setFieldErrors({ password: "Password must be at least 8 characters" });
    if (password !== confirm) return setFieldErrors({ confirm: "Passwords do not match" });

    setBusy(true);
    try {
      await register(name.trim(), email.trim(), password);
      nav("/", { replace: true });
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.status === 409) setError("This email is already registered.");
        else if (err.body?.fieldErrors) setFieldErrors(err.body.fieldErrors);
        else setError(err.message);
      } else {
        setError("Cannot reach the server. Is the backend running on port 8080?");
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-wrap">
      <form className="auth-card" onSubmit={onSubmit}>
        <h1>Create account</h1>
        <div className="sub">New accounts are created as Employees</div>
        {error && <div className="auth-banner">{error}</div>}

        <label>Full name</label>
        <input value={name} onChange={(e) => setName(e.target.value)} maxLength={100} required autoFocus />
        {fieldErrors.name && <div className="error">{fieldErrors.name}</div>}

        <label>Email</label>
        <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} maxLength={150} required />
        {fieldErrors.email && <div className="error">{fieldErrors.email}</div>}

        <label>Password (8–100 characters)</label>
        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} maxLength={100} required />
        {fieldErrors.password && <div className="error">{fieldErrors.password}</div>}

        <label>Confirm password</label>
        <input type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)} required />
        {fieldErrors.confirm && <div className="error">{fieldErrors.confirm}</div>}

        <button className="btn" type="submit" disabled={busy}>
          {busy ? "Creating…" : "Create account"}
        </button>
        <div className="alt">
          Already have an account? <Link to="/login">Sign in</Link>
        </div>
      </form>
    </div>
  );
}