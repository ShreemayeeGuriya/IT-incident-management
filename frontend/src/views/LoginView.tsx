
import { useState } from "react";
import type { Role } from "../models/types";

interface Props {
  onLogin: (name: string, role: Role) => void;
}

export function LoginView({ onLogin }: Props) {
  const [name, setName] = useState("Aarav Mehta");
  const [role, setRole] = useState<Role>("Employee");

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    onLogin(name, role);
  }

  return (
    <main className="login-shell">
      <section className="login-card">

        <div className="brand-mark large">R</div>

        <div className="eyebrow">
          INCIDENT MANAGEMENT PLATFORM
        </div>

        <h1>Welcome to ResolveIT</h1>

        <p className="muted">
          Sign in to report, manage and resolve incidents.
        </p>

        <form onSubmit={handleSubmit}>

          <label>Your name</label>

          <input
            value={name}
            onChange={e => setName(e.target.value)}
            placeholder="Enter your name"
            required
          />

          <label>Sign in as</label>

          <div className="role-picker">
            {(["Employee", "Technician", "Admin"] as Role[]).map(r => (
              <button
                type="button"
                key={r}
                className={
                  role === r
                    ? "role-option active"
                    : "role-option"
                }
                onClick={() => {
                  setRole(r);

                  setName(
                    r === "Admin"
                      ? "Admin"
                      : r === "Technician"
                      ? "Riya Sharma"
                      : "Aarav Mehta"
                  );
                }}
              >
                {r}
              </button>
            ))}
          </div>

          <button className="primary full" type="submit">
            Continue →
          </button>

        </form>

        <p className="tiny muted">
          ResolveIT · Incident Management
        </p>

      </section>
    </main>
  );
}
