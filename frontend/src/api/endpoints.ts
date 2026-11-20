import { api } from "./client";
import type * as T from "../types/incident";

const qs = (p: Record<string, unknown>) => {
  const s = new URLSearchParams();
  Object.entries(p).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== "") s.set(k, String(v));
  });
  const q = s.toString();
  return q ? `?${q}` : "";
};
const json = (b: unknown) => ({ body: JSON.stringify(b) });

export const authApi = {
  login: (b: { email: string; password: string }) =>
    api<T.AuthResponse>("/auth/login", { method: "POST", ...json(b) }),
  register: (b: { name: string; email: string; password: string }) =>
    api<T.AuthResponse>("/auth/register", { method: "POST", ...json(b) }),
  me: () => api<T.User>("/auth/me"),
};

export const incidentsApi = {
  list: (p: {
    priority?: string; status?: string; technicianId?: number; search?: string;
    page?: number; size?: number; sortBy?: string; direction?: string;
  }) => api<T.Page<T.Incident>>(`/incidents${qs(p)}`),
  get: (id: number) => api<T.Incident>(`/incidents/${id}`),
  create: (b: unknown) => api<T.Incident>("/incidents", { method: "POST", ...json(b) }),
  update: (id: number, b: unknown) =>
    api<T.Incident>(`/incidents/${id}`, { method: "PUT", ...json(b) }),
  remove: (id: number) => api<null>(`/incidents/${id}`, { method: "DELETE" }),
  setStatus: (id: number, status: T.Status) =>
    api<T.Incident>(`/incidents/${id}/status`, { method: "PATCH", ...json({ status }) }),
  assign: (id: number, technicianId: number | null) =>
    api<T.Incident>(`/incidents/${id}/assign`, { method: "PATCH", ...json({ technicianId }) }),
  autoAssign: (id: number) => api<any>(`/incidents/${id}/auto-assign`, { method: "POST" }),
  recommendation: (id: number) => api<any>(`/incidents/${id}/assignment/recommendation`),
  duplicates: (b: { description: string; categoryId: number; serviceId: number }) =>
    api<T.DuplicateSuggestion[]>("/incidents/duplicate-suggestions", { method: "POST", ...json(b) }),
  comments: (id: number) => api<T.Comment[]>(`/incidents/${id}/comments`),
  addComment: (id: number, text: string) =>
    api<T.Comment>(`/incidents/${id}/comments`, { method: "POST", ...json({ text }) }),
  audit: (id: number) => api<T.AuditEntry[]>(`/incidents/${id}/audit`),
  assessment: (id: number) => api<T.PriorityAssessment>(`/incidents/${id}/priority/assessment`),
  recalculate: (id: number) => api<any>(`/incidents/${id}/priority/recalculate`, { method: "POST" }),
};

export const lookupApi = {
  categories: () => api<T.Lookup[]>("/categories", { cache: "no-store" }),
  services: () => api<T.Lookup[]>("/services", { cache: "no-store" }),
  slaPolicies: () => api<T.SlaPolicy[]>("/sla-policies"),
  skills: () => api<string[]>("/skills"),
};

export const priorityApi = {
  preview: (b: { impact: T.Level; urgency: T.Level; businessCriticality: T.Level }) =>
    api<T.PriorityAssessment>("/priority/preview", { method: "POST", ...json(b) }),
  strategies: () => api<any>("/priority/strategies"),
  setStrategy: (strategy: string) =>
    api<any>("/priority/strategy", { method: "PUT", ...json({ strategy }) }),
};

export const assignmentApi = {
  strategies: () => api<any>("/assignment/strategies"),
  setStrategy: (strategy: string) =>
    api<any>("/assignment/strategy", { method: "PUT", ...json({ strategy }) }),
};

export const notificationsApi = {
  list: (page = 0, size = 20) =>
    api<T.Page<T.NotificationItem>>(`/notifications${qs({ page, size })}`),
  unreadCount: () => api<{ unreadCount: number }>("/notifications/unread-count"),
  markRead: (id: number) =>
    api<T.NotificationItem>(`/notifications/${id}/read`, { method: "PATCH" }),
};

export const usersApi = {
  list: () => api<any>("/users"),
  assignees: () => api<T.Technician[]>("/users/assignees"),
  setRole: (id: number, role: string) =>
    api<any>(`/users/${id}/role`, { method: "PUT", ...json({ role }) }),
  remove: (id: number) => api<null>(`/users/${id}`, { method: "DELETE" }),
  workload: () => api<T.Workload[]>("/technicians/workload"),
  skillsOf: (id: number) => api<string[]>(`/technicians/${id}/skills`),
  setSkills: (id: number, skills: string[]) =>
    api<string[]>(`/technicians/${id}/skills`, { method: "PUT", ...json({ skills }) }),
};
