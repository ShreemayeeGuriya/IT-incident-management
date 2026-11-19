export type Role = "EMPLOYEE" | "TECHNICIAN" | "ADMIN";
export type Priority = "P1" | "P2" | "P3" | "P4";
export type Level = "LOW" | "MEDIUM" | "HIGH";
export type Status =
  | "REPORTED" | "CLASSIFIED" | "PRIORITIZED" | "ASSIGNED"
  | "INVESTIGATING" | "RESOLVED" | "VERIFIED" | "CLOSED";
export type ObjStatus =
  | "NOT_STARTED" | "WITHIN_SLA" | "APPROACHING_BREACH" | "BREACHED" | "MET";

export interface User {
  id: number; name: string; email: string; role: string; createdAt: string;
}
export interface Me extends Omit<User, "role"> { role: Role; }
export interface AuthResponse { token: string; user: User; }

export interface Technician {
  id: number; userId: number; name: string; email: string; maxWorkload: number;
  availabilityStatus: "AVAILABLE" | "BUSY" | "UNAVAILABLE";
  experienceLevel: string | null;
}
export interface Lookup { id: number; name: string; description: string; }
export interface SlaPolicy {
  id: number; name: string; priority: Priority;
  responseTimeMinutes: number; resolutionTimeMinutes: number;
}

export interface Incident {
  id: number; reporter: User; assignedTechnician: Technician | null;
  category: Lookup; service: Lookup; slaPolicy: SlaPolicy | null;
  description: string; impact: Level; urgency: Level; businessCriticality: Level;
  priority: Priority; status: Status; slaStatus: string;
  responseSlaStatus: ObjStatus; resolutionSlaStatus: ObjStatus;
  investigationDetails: string | null; resolutionDetails: string | null;
  closureDetails: string | null;
  createdAt: string; firstResponseAt: string | null;
  resolvedAt: string | null; closedAt: string | null;
  responseDeadline: string; resolutionDeadline: string;
}

export interface Page<T> {
  content: T[]; page: number; size: number; totalElements: number; totalPages: number;
}
export interface Comment {
  id: number; incidentId: number; author: User; text: string; createdAt: string;
}
export interface AuditEntry {
  id: number; actorUserId: number | null; action: string;
  oldValue: string | null; newValue: string | null; description: string; timestamp: string;
}
export interface NotificationItem {
  id: number; incidentId: number | null; message: string; notificationType: string;
  isRead: boolean; createdAt: string; readAt: string | null;
}
export interface Workload {
  technicianId: number; name: string; availability: string;
  activeIncidents: number; maxWorkload: number; skills: string[];
}
export interface PriorityAssessment {
  priority: Priority; score: number; strategy: string; explanation: string;
}
export interface DuplicateSuggestion {
  incidentId: number; description: string; category: string; service: string;
  status: Status; similarityScore: number;
}