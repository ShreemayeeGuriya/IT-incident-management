import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { incidentsApi, usersApi } from "../api/endpoints";
import { useAuth } from "../auth/AuthContext";
import { ApiErrorMessage, IncidentRow, Loading, PageHeader, PriorityPill } from "../components/ui";
import type { Incident, Page, Workload } from "../types/incident";

export default function DashboardPage() {
  const { user, techProfile } = useAuth();
  const [page, setPage] = useState<Page<Incident> | null>(null);
  const [workload, setWorkload] = useState<Workload[]>([]);
  const [workloadError, setWorkloadError] = useState("");
  const [error, setError] = useState<unknown>(null);
  useEffect(() => {
    const params = { page: 0, size: 100, ...(user?.role === "TECHNICIAN" && techProfile ? { technicianId: techProfile.id } : {}) };
    incidentsApi.list(params).then(setPage).catch(setError);
    if (user?.role !== "EMPLOYEE") usersApi.workload().then(setWorkload).catch((e) => setWorkloadError(e instanceof Error ? e.message : "Workload data is unavailable."));
  }, [user?.role, techProfile?.id]);
  if (error) return <div className="page"><PageHeader title="Overview" /><ApiErrorMessage error={error} /></div>;
  if (!page) return <div className="page"><Loading /></div>;
  const records = page.content;
  const open = records.filter((i) => !["RESOLVED", "VERIFIED", "CLOSED"].includes(i.status)).length;
  const resolved = records.filter((i) => ["RESOLVED", "VERIFIED", "CLOSED"].includes(i.status)).length;
  const critical = records.filter((i) => i.priority === "P1").length;
  const atRisk = records.filter((i) => ["APPROACHING_BREACH", "BREACHED"].includes(i.responseSlaStatus) || ["APPROACHING_BREACH", "BREACHED"].includes(i.resolutionSlaStatus)).length;
  const greeting = user?.role === "EMPLOYEE" ? "Your service desk" : user?.role === "TECHNICIAN" ? "Your workbench" : "Service operations";
  return <div className="page">
    <PageHeader eyebrow={greeting} title={`Good ${new Date().getHours() < 12 ? "morning" : "afternoon"}, ${user?.name.split(" ")[0] || "there"}`} action={user?.role === "EMPLOYEE" ? <Link className="btn" to="/incidents/new">＋ Report an incident</Link> : <Link className="btn" to="/incidents">Open incident queue</Link>} />
    <div className="stat-grid">
      <Stat label={user?.role === "EMPLOYEE" ? "My incidents" : "Incidents in view"} value={page.totalElements} icon="◈" />
      <Stat label="Open" value={open} icon="◷" tone="blue" />
      <Stat label="Resolved" value={resolved} icon="✓" tone="green" />
      <Stat label="SLA at risk" value={atRisk} note={`${critical} P1 critical`} icon="⚑" tone={atRisk ? "orange" : "purple"} />
    </div>
    <div className="dashboard-columns">
      <section className="panel"><div className="panel-head"><div><div className="eyebrow">Live queue</div><h2>Recent incidents</h2></div><Link to="/incidents">View all →</Link></div>
        {records.length ? <div className="incident-list">{records.slice(0, 6).map((incident) => <IncidentRow key={incident.id} incident={incident} />)}</div> : <div className="empty compact"><h3>No incidents yet</h3></div>}
      </section>
      <section className="panel"><div className="panel-head"><div><div className="eyebrow">Signal</div><h2>Priority mix</h2></div></div>
        <div className="priority-breakdown">{(["P1", "P2", "P3", "P4"] as const).map((p) => { const n = records.filter((i) => i.priority === p).length; return <div className="priority-line" key={p}><PriorityPill value={p} /><div className="bar-track"><span className={`bar ${p.toLowerCase()}`} style={{ width: `${records.length ? Math.max(n / records.length * 100, n ? 5 : 0) : 0}%` }} /></div><strong>{n}</strong></div>; })}</div>
        {user?.role !== "EMPLOYEE" && <><div className="divider" /><div className="panel-head"><div><div className="eyebrow">Team capacity</div><h2>Technician workload</h2></div><Link to="/workload">View team →</Link></div>{workload.slice(0, 4).map((w) => <div className="workload-mini" key={w.technicianId}><div><strong>{w.name}</strong><span>{w.activeIncidents} / {w.maxWorkload} active</span></div><div className="bar-track"><span className="bar purple" style={{ width: `${Math.min(w.activeIncidents / Math.max(w.maxWorkload, 1) * 100, 100)}%` }} /></div></div>)}{workloadError ? <p className="small error">Workload could not be loaded: {workloadError}</p> : workload.length === 0 && <p className="muted small">No workload data available.</p>}</>}
      </section>
    </div>
  </div>;
}
function Stat({ label, value, note, icon, tone = "purple" }: { label: string; value: number; note?: string; icon: string; tone?: string }) { return <div className="stat-card"><div className={`stat-icon ${tone}`}>{icon}</div><div className="stat-label">{label}</div><div className="stat-value">{value}</div>{note && <div className="stat-note">{note}</div>}</div>; }
