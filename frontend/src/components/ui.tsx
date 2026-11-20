import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import type { Incident } from "../types/incident";

export function PageHeader({ eyebrow, title, subtitle, action }: { eyebrow?: string; title: string; subtitle?: string; action?: ReactNode }) {
  return <div className="page-header"><div>{eyebrow && <div className="eyebrow">{eyebrow}</div>}<h1>{title}</h1>{subtitle && <p>{subtitle}</p>}</div>{action && <div className="header-action">{action}</div>}</div>;
}
export function Notice({ children, tone = "error" }: { children: ReactNode; tone?: "error" | "info" | "success" }) { return <div className={`notice ${tone}`} role="status">{children}</div>; }
export function Loading({ label = "Loading your Radar workspace…" }: { label?: string }) { return <div className="loading"><span className="spinner" />{label}</div>; }
export function Empty({ title, children }: { title: string; children?: ReactNode }) { return <div className="empty"><div className="empty-icon">◇</div><h3>{title}</h3>{children && <p>{children}</p>}</div>; }
export function StatusPill({ value }: { value: string }) { return <span className={`pill status s-${value.toLowerCase()}`}>{value.replaceAll("_", " ")}</span>; }
export function PriorityPill({ value }: { value: string }) { return <span className={`pill priority ${value.toLowerCase()}`}>{value}</span>; }
export function SlaPill({ value }: { value?: string | null }) { if (!value || value === "NOT_STARTED") return <span className="muted">Not started</span>; return <span className={`sla ${value.toLowerCase()}`}>{value.replaceAll("_", " ")}</span>; }
export function IncidentRow({ incident, selected = false }: { incident: Incident; selected?: boolean }) {
  return <Link to={`/incidents/${incident.id}`} className={`incident-row ${selected ? "selected" : ""}`}>
    <div className="row-top"><PriorityPill value={incident.priority} /><StatusPill value={incident.status} /><span className="row-time">{new Date(incident.createdAt).toLocaleDateString([], { month: "short", day: "numeric" })}</span></div>
    <strong>{incident.description.split("\n")[0].slice(0, 88)}</strong>
    <div className="row-meta"><span>#{incident.id} · {incident.category?.name || "Uncategorized"}</span><span>{incident.assignedTechnician?.name || "Unassigned"}</span></div>
  </Link>;
}
export function ApiErrorMessage({ error }: { error: unknown }) {
  const message = error instanceof Error ? error.message : "The request could not be completed.";
  return <Notice>{message}</Notice>;
}
