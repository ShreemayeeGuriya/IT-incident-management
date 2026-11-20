import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { incidentsApi, lookupApi, priorityApi } from "../api/endpoints";
import { ApiError } from "../api/client";
import { Notice, PageHeader, PriorityPill, StatusPill } from "../components/ui";
import type { DuplicateSuggestion, Level, Lookup, PriorityAssessment } from "../types/incident";

export default function NewIncidentPage() {
  const nav = useNavigate();
  const [categories, setCategories] = useState<Lookup[]>([]); const [services, setServices] = useState<Lookup[]>([]);
  const [description, setDescription] = useState(""); const [categoryId, setCategoryId] = useState(""); const [serviceId, setServiceId] = useState("");
  const [impact, setImpact] = useState<Level>("MEDIUM"); const [urgency, setUrgency] = useState<Level>("MEDIUM"); const [businessCriticality, setCriticality] = useState<Level>("MEDIUM");
  const [assessment, setAssessment] = useState<PriorityAssessment | null>(null); const [suggestions, setSuggestions] = useState<DuplicateSuggestion[] | null>(null);
  const [busy, setBusy] = useState(false); const [lookupLoading, setLookupLoading] = useState(true); const [lookupError, setLookupError] = useState(""); const [error, setError] = useState(""); const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const fetchLookups = useCallback(() => Promise.all([
    lookupApi.categories(), lookupApi.services(),
  ]), []);
  const applyLookups = useCallback(([categoryRows, serviceRows]: Awaited<ReturnType<typeof fetchLookups>>) => {
    setCategories(categoryRows);
    setServices(serviceRows);
    setLookupError("");
  }, []);
  const handleLookupError = useCallback((e: unknown) => {
    setLookupError(e instanceof Error ? e.message : "Could not load incident categories and services.");
  }, []);
  const loadLookups = useCallback(() => {
    setLookupLoading(true);
    void fetchLookups().then(applyLookups).catch(handleLookupError).finally(() => setLookupLoading(false));
  }, [applyLookups, fetchLookups, handleLookupError]);
  const refreshLookups = useCallback(() => {
    setLookupLoading(true);
    void loadLookups();
  }, [loadLookups]);
  useEffect(() => {
    void fetchLookups().then(applyLookups).catch(handleLookupError).finally(() => setLookupLoading(false));
    window.addEventListener("focus", refreshLookups);
    return () => window.removeEventListener("focus", refreshLookups);
  }, [applyLookups, fetchLookups, handleLookupError, refreshLookups]);
  useEffect(() => { if (!categoryId || !serviceId || description.trim().length < 12) return; const timer = setTimeout(() => { priorityApi.preview({ impact, urgency, businessCriticality }).then(setAssessment).catch(() => setAssessment(null)); }, 450); return () => clearTimeout(timer); }, [impact, urgency, businessCriticality, categoryId, serviceId, description]);
  const checkDuplicates = async () => { if (!categoryId || !serviceId || description.trim().length < 12) return; setBusy(true); try { setSuggestions(await incidentsApi.duplicates({ description, categoryId: Number(categoryId), serviceId: Number(serviceId) })); } catch (e) { setError(e instanceof Error ? e.message : "Could not check for similar incidents."); } finally { setBusy(false); } };
  const submit = async (e: FormEvent) => { e.preventDefault(); setBusy(true); setError(""); setFieldErrors({}); try { const item = await incidentsApi.create({ description, categoryId: Number(categoryId), serviceId: Number(serviceId), impact, urgency, businessCriticality }); nav(`/incidents/${item.id}`); } catch (err) { if (err instanceof ApiError && err.body?.fieldErrors) setFieldErrors(err.body.fieldErrors); setError(err instanceof Error ? err.message : "Could not submit incident."); } finally { setBusy(false); } };
  return <div className="page narrow-page"><PageHeader eyebrow="Incident intake" title="Report an incident" action={<button className="btn secondary" type="button" onClick={refreshLookups} disabled={lookupLoading}>{lookupLoading ? "Refreshing…" : "↻ Refresh categories & services"}</button>} />
    <form className="panel form-panel" onSubmit={submit}>
      {lookupError && <Notice>{lookupError}</Notice>}{error && <Notice>{error}</Notice>}
      <label className="field full"><span>What happened? <b>*</b></span><textarea required minLength={5} maxLength={10000} rows={5} value={description} onChange={(e) => { setDescription(e.target.value); setAssessment(null); }} />{fieldErrors.description && <small className="error">{fieldErrors.description}</small>}</label>
      <div className="form-grid"><label className="field"><span>Category <b>*</b></span><select required value={categoryId} onChange={(e) => { setCategoryId(e.target.value); setSuggestions(null); setAssessment(null); }}><option value="">Select a category</option>{categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</select>{fieldErrors.categoryId && <small className="error">{fieldErrors.categoryId}</small>}</label>
        <label className="field"><span>Affected service <b>*</b></span><select required value={serviceId} onChange={(e) => { setServiceId(e.target.value); setSuggestions(null); setAssessment(null); }}><option value="">Select a service</option>{services.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}</select>{fieldErrors.serviceId && <small className="error">{fieldErrors.serviceId}</small>}</label></div>
      <div className="form-grid factors"><Factor label="Impact" value={impact} change={(v) => { setImpact(v); setAssessment(null); }} /><Factor label="Urgency" value={urgency} change={(v) => { setUrgency(v); setAssessment(null); }} /><Factor label="Business criticality" value={businessCriticality} change={(v) => { setCriticality(v); setAssessment(null); }} /></div>
      <div className="priority-preview"><div><span className="eyebrow">Calculated priority</span></div>{assessment && <PriorityPill value={assessment.priority} />}</div>
      <div className="duplicate-check"><div><strong>Check for similar reports</strong></div><button className="btn secondary" type="button" disabled={busy || !categoryId || !serviceId || description.trim().length < 12} onClick={checkDuplicates}>{busy ? "Checking…" : "Find similar"}</button></div>
      {suggestions && <div className="suggestions">{suggestions.length ? <>{suggestions.map((s) => <Link className="suggestion" key={s.incidentId} to={`/incidents/${s.incidentId}`}><span><strong>#{s.incidentId} · {s.category} / {s.service}</strong><small>{s.description}</small></span><span><StatusPill value={s.status} />{Math.round(s.similarityScore * 100)}% match</span></Link>)}</> : <div className="muted">No similar incidents found.</div>}</div>}
      <div className="form-actions"><Link className="btn secondary" to="/incidents">Cancel</Link><button className="btn" disabled={busy || lookupLoading || !categories.length || !services.length}>{lookupLoading ? "Loading form…" : busy ? "Submitting…" : "Submit report"}</button></div>
    </form>
  </div>;
}
function Factor({ label, value, change }: { label: string; value: Level; change: (v: Level) => void }) { return <label className="field"><span>{label} <b>*</b></span><select value={value} onChange={(e) => change(e.target.value as Level)}>{["LOW", "MEDIUM", "HIGH"].map((v) => <option key={v}>{v}</option>)}</select></label>; }
