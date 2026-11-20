import type { Incident, Role } from "../types/incident";

// Backend sends "USER" for employees; we use "EMPLOYEE" everywhere in the UI
export const normalizeRole = (r: string): Role =>
  r === "USER" ? "EMPLOYEE" : (r as Role);

// The backend has no title field, so we derive one from the description
export const titleOf = (i: Pick<Incident, "description">): string => {
  const first = i.description.split("\n")[0].trim();
  return first.length > 80 ? first.slice(0, 77) + "…" : first;
};

export const fmtDate = (iso: string | null | undefined): string =>
  iso
    ? new Date(iso).toLocaleString([], { dateStyle: "medium", timeStyle: "short" })
    : "—";

export const fmtRemaining = (iso: string | null | undefined): string => {
  if (!iso) return "Deadline unavailable";
  const delta = new Date(iso).getTime() - Date.now();
  const totalMinutes = Math.ceil(Math.abs(delta) / 60_000);
  const days = Math.floor(totalMinutes / 1440);
  const hours = Math.floor((totalMinutes % 1440) / 60);
  const minutes = totalMinutes % 60;
  const span = days ? `${days}d ${hours}h` : hours ? `${hours}h ${minutes}m` : `${minutes}m`;
  return delta < 0 ? `Overdue by ${span}` : `${span} remaining`;
};
