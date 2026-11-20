import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { incidentsApi, usersApi } from "../api/endpoints";
import { useAuth } from "../auth/AuthContext";
import { ApiErrorMessage, Empty, Loading, PageHeader, PriorityPill, StatusPill } from "../components/ui";
import type { Incident, Page, Technician } from "../types/incident";

const STATUSES = ["REPORTED", "CLASSIFIED", "PRIORITIZED", "ASSIGNED", "INVESTIGATING", "RESOLVED", "VERIFIED", "CLOSED"];
export default function IncidentsPage() {
  const { user, techProfile } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const [page, setPage] = useState<Page<Incident> | null>(null);
  const [technicians, setTechnicians] = useState<Technician[]>([]);
  const [error, setError] = useState<unknown>(null);
  const [search, setSearch] = useState(searchParams.get("search") || "");
  const status = searchParams.get("status") || "";
  const priority = searchParams.get("priority") || "";
  const mine = searchParams.get("mine") === "1";
  const pageNumber = Number(searchParams.get("page") || 0);
  const technicianId = searchParams.get("technicianId") || "";
  const sortBy = searchParams.get("sortBy") || "createdAt";
  const direction = searchParams.get("direction") || "desc";
  useEffect(() => { if (user?.role !== "EMPLOYEE") usersApi.assignees().then(setTechnicians).catch(() => {}); }, [user?.role]);
  useEffect(() => { setSearch(searchParams.get("search") || ""); }, [searchParams]);
  useEffect(() => {
    setPage(null); setError(null);
    incidentsApi.list({ page: pageNumber, size: 50, search: searchParams.get("search") || undefined, status: status || undefined, priority: priority || undefined, sortBy, direction, ...(mine && user?.role === "TECHNICIAN" && techProfile ? { technicianId: techProfile.id } : technicianId ? { technicianId: Number(technicianId) } : {}) }).then(setPage).catch(setError);
  }, [searchParams, mine, status, priority, pageNumber, sortBy, direction, technicianId, user?.role, techProfile?.id]);
  const setFilter = (key: string, value: string) => { const next = new URLSearchParams(searchParams); value ? next.set(key, value) : next.delete(key); if (key === "mine" && value) next.delete("technicianId"); if (key === "technicianId" && value) next.delete("mine"); if (key !== "page") next.delete("page"); setSearchParams(next); };
  const title = user?.role === "EMPLOYEE" ? "My incidents" : mine ? "My assigned incidents" : user?.role === "ADMIN" ? "Incident monitor" : "Incident queue";
  if (error) return <div className="page"><PageHeader title={title} /><ApiErrorMessage error={error} /></div>;
  return <div className="page">
    <PageHeader eyebrow="Incident management" title={title} action={user?.role === "EMPLOYEE" && <Link className="btn" to="/incidents/new">＋ Report incident</Link>} />
    <section className="panel list-panel">
      <div className="filters"><label className="search-field"><span>⌕</span><input placeholder="Search descriptions…" value={search} onChange={(e) => setSearch(e.target.value)} onKeyDown={(e) => e.key === "Enter" && setFilter("search", search)} /></label><select aria-label="Filter by status" value={status} onChange={(e) => setFilter("status", e.target.value)}><option value="">All statuses</option>{STATUSES.map((s) => <option key={s} value={s}>{s.replaceAll("_", " ")}</option>)}</select><select aria-label="Filter by priority" value={priority} onChange={(e) => setFilter("priority", e.target.value)}><option value="">All priorities</option>{["P1", "P2", "P3", "P4"].map((p) => <option key={p}>{p}</option>)}</select>{user?.role !== "EMPLOYEE" && <select aria-label="Filter by technician" value={technicianId} onChange={(e) => setFilter("technicianId", e.target.value)}><option value="">All technicians</option>{technicians.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}</select>}{user?.role === "TECHNICIAN" && <label className="check-filter"><input type="checkbox" checked={mine} onChange={(e) => setFilter("mine", e.target.checked ? "1" : "")} /> Assigned to me</label>}<select aria-label="Sort incidents" value={sortBy} onChange={(e) => setFilter("sortBy", e.target.value)}><option value="createdAt">Created date</option><option value="priority">Priority</option><option value="status">Status</option><option value="id">Incident ID</option></select><button className="btn secondary small-btn" aria-label={`Sort ${direction === "desc" ? "ascending" : "descending"}`} onClick={() => setFilter("direction", direction === "desc" ? "asc" : "desc")}>{direction === "desc" ? "↓" : "↑"}</button><button className="btn secondary small-btn" onClick={() => { setSearch(""); setSearchParams({}); }}>Clear</button></div>
      {page ? <><div className="table-summary">Showing <strong>{page.content.length ? pageNumber * page.size + 1 : 0}–{pageNumber * page.size + page.content.length}</strong> of <strong>{page.totalElements}</strong> matching incidents</div>{page.content.length ? <div className="table-wrap"><table><thead><tr><th>Incident</th><th>Priority</th><th>Status</th><th>Category / Service</th><th>Assigned to</th><th>Response SLA</th><th>Resolution SLA</th></tr></thead><tbody>{page.content.map((i) => <tr key={i.id}><td><Link className="incident-link" to={`/incidents/${i.id}`}><strong>#{i.id}</strong><span>{i.description.split("\n")[0].slice(0, 72)}</span></Link></td><td><PriorityPill value={i.priority} /></td><td><StatusPill value={i.status} /></td><td>{i.category?.name}<span className="cell-sub">{i.service?.name}</span></td><td>{i.assignedTechnician?.name || <span className="muted">Unassigned</span>}</td><td><SlaValue incident={i} kind="response" /></td><td><SlaValue incident={i} kind="resolution" /></td></tr>)}</tbody></table></div> : <Empty title="No incidents match these filters">Adjust your search or clear filters to see more.</Empty>}<div className="pagination"><button className="btn secondary small-btn" disabled={pageNumber <= 0} onClick={() => setFilter("page", String(pageNumber - 1))}>← Previous</button><span>Page {pageNumber + 1} of {Math.max(page.totalPages, 1)}</span><button className="btn secondary small-btn" disabled={pageNumber + 1 >= page.totalPages} onClick={() => setFilter("page", String(pageNumber + 1))}>Next →</button></div></> : <Loading label="Loading incidents…" />}
    </section>
  </div>;
}
function SlaValue({ incident, kind }: { incident: Incident; kind: "response" | "resolution" }) { const status = kind === "response" ? incident.responseSlaStatus : incident.resolutionSlaStatus; const deadline = kind === "response" ? incident.responseDeadline : incident.resolutionDeadline; return <div className="sla-cell"><StatusSla value={status} /><span>{new Date(deadline).toLocaleDateString([], { month: "short", day: "numeric" })}</span></div>; }
function StatusSla({ value }: { value: string }) { return <span className={`sla ${value.toLowerCase()}`}>{value.replaceAll("_", " ")}</span>; }
