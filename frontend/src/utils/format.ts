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