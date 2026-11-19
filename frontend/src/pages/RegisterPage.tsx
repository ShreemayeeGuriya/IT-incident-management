import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { isAxiosError } from "axios";
import { useAuth } from "../auth/AuthContext";

interface ErrorResponse {
  message?: string;
  fieldErrors?: Record<string, string>;
}

export default function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError("");
    setFieldErrors({});
    setBusy(true);
    try {
      await register({ name, email, password });
      navigate("/");
    } catch (err) {
      if (isAxiosError<ErrorResponse>(err)) {
        const status = err.response?.status;
        if (status === 409) {
          setError("This email is already registered.");
        } else if (status === 400) {
          setFieldErrors(err.response?.data?.fieldErrors ?? {});
          setError(err.response?.data?.message ?? "Please check your details.");
        } else {
          setError("Could not register. Please try again.");
        }
      } else {
        setError("Could not register. Please try again.");
      }
    } finally {
      setBusy(false);
    }
  }

  const fieldError = (key: string) =>
    fieldErrors[key] ? (
      <div style={{ color: "var(--p1)", fontSize: 12 }}>{fieldErrors[key]}</div>
    ) : null;

  return (
    <div style={{ maxWidth: 360, margin: "80px auto", padding: 24 }}>
      <h2 style={{ marginTop: 0 }}>Create your Radar account</h2>
      <form onSubmit={handleSubmit} style={{ display: "flex", flexDirection: "column", gap: 12 }}>
        <label>
          Name
          <input
            required
            maxLength={100}
            value={name}
            onChange={(e) => setName(e.target.value)}
            style={{ width: "100%", padding: 8, marginTop: 4 }}
          />
          {fieldError("name")}
        </label>
        <label>
          Email
          <input
            type="email"
            required
            maxLength={150}
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            style={{ width: "100%", padding: 8, marginTop: 4 }}
          />
          {fieldError("email")}
        </label>
        <label>
          Password (8 to 100 characters)
          <input
            type="password"
            required
            minLength={8}
            maxLength={100}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            style={{ width: "100%", padding: 8, marginTop: 4 }}
          />
          {fieldError("password")}
        </label>
        {error && <div style={{ color: "var(--p1)" }}>{error}</div>}
        <button type="submit" disabled={busy} style={{ padding: 8 }}>
          {busy ? "Creating account..." : "Register"}
        </button>
      </form>
      <p>
        Already have an account? <Link to="/login">Sign in</Link>
      </p>
    </div>
  );
}