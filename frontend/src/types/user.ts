// Backend sends "USER" for Employee accounts.
export type Role = "USER" | "TECHNICIAN" | "ADMIN";

export interface User {
  id: number;
  name: string;
  email: string;
  role: Role;
  createdAt: string;
}

export interface AuthResponse {
  token: string;
  user: User;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  name: string;
  email: string;
  password: string;
}

export type ExperienceLevel = string | null;

export type Availability = "AVAILABLE" | "BUSY" | "UNAVAILABLE";

export interface Technician {
  id: number; // technician PROFILE id, used for assign and filter APIs
  userId: number;
  name: string;
  email: string;
  maxWorkload: number;
  availabilityStatus: Availability;
  experienceLevel: ExperienceLevel;
}

export interface TechnicianWorkload {
  technicianId: number;
  name: string;
  availability: Availability;
  activeIncidents: number;
  maxWorkload: number;
  skills: string[];
}