import type { Technician, User } from "./user";

export type Priority = "P1" | "P2" | "P3" | "P4";
export type Level = "LOW" | "MEDIUM" | "HIGH";

export type IncidentStatus =
  | "REPORTED"
  | "CLASSIFIED"
  | "PRIORITIZED"
  | "ASSIGNED"
  | "INVESTIGATING"
  | "RESOLVED"
  | "VERIFIED"
  | "CLOSED";

export type ObjectiveSlaStatus =
  | "NOT_STARTED"
  | "WITHIN_SLA"
  | "APPROACHING_BREACH"
  | "BREACHED"
  | "MET";

export type SummarySlaStatus =
  | "NOT_STARTED"
  | "WITHIN_SLA"
  | "RESPONSE_BREACHED"
  | "RESOLUTION_BREACHED"
  | "BREACHED"
  | "MET";

export interface Category {
  id: number;
  name: string;
  description: string;
}

export interface Service {
  id: number;
  name: string;
  description: string;
}

export interface SlaPolicy {
  id: number;
  name: string;
  priority: Priority;
  responseTimeMinutes: number;
  resolutionTimeMinutes: number;
}

export interface Incident {
  id: number;
  reporter: User;
  assignedTechnician: Technician | null;
  category: Category;
  service: Service;
  slaPolicy: SlaPolicy;
  description: string;
  impact: Level;
  urgency: Level;
  businessCriticality: Level;
  priority: Priority;
  status: IncidentStatus;
  slaStatus: SummarySlaStatus;
  responseSlaStatus: ObjectiveSlaStatus;
  resolutionSlaStatus: ObjectiveSlaStatus;
  investigationDetails: string | null;
  resolutionDetails: string | null;
  closureDetails: string | null;
  createdAt: string;
  firstResponseAt: string | null;
  resolvedAt: string | null;
  closedAt: string | null;
  responseDeadline: string;
  resolutionDeadline: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface IncidentCreateRequest {
  description: string;
  categoryId: number;
  serviceId: number;
  slaPolicyId: number | null;
  impact: Level;
  urgency: Level;
  businessCriticality: Level;
  priority: Priority | null;
}

export interface IncidentUpdateRequest {
  description?: string;
  categoryId?: number;
  serviceId?: number;
  slaPolicyId?: number | null;
  impact?: Level;
  urgency?: Level;
  businessCriticality?: Level;
  investigationDetails?: string | null;
  resolutionDetails?: string | null;
  closureDetails?: string | null;
}

export interface DuplicateSuggestion {
  incidentId: number;
  description: string;
  category: string;
  service: string;
  status: IncidentStatus;
  similarityScore: number;
}

export interface PriorityAssessment {
  priority: Priority;
  score: number;
  strategy: string;
  explanation: string;
}

export interface PriorityRecalculationResponse {
  assessment: PriorityAssessment;
  changed: boolean;
  incident: Incident;
}

export interface Comment {
  id: number;
  incidentId: number;
  author: User;
  text: string;
  createdAt: string;
}

export interface AuditEntry {
  id: number;
  actorUserId: number | null;
  action: string;
  oldValue: string | null;
  newValue: string | null;
  description: string;
  timestamp: string;
}

export interface AutoAssignResponse {
  strategy: string;
  technician: {
    technicianId: number;
    name: string;
    availability: string;
    activeIncidents: number;
    maxWorkload: number;
    skills: string[];
  };
  incident: Incident;
}

export interface NotificationItem {
  id: number;
  incidentId: number;
  message: string;
  notificationType: string;
  isRead: boolean;
  createdAt: string;
  readAt: string | null;
}