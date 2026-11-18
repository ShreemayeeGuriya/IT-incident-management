
import type { Incident, User } from "../models/types";

const INCIDENTS_KEY = "resolveit.incidents.v2";
const USERS_KEY = "resolveit.users.v2";

const seedUsers: User[] = [
  {
    id: "emp-1",
    name: "Aarav Mehta",
    email: "aarav@example.com",
    role: "Employee",
  },
  {
    id: "tech-1",
    name: "Riya Sharma",
    email: "riya@example.com",
    role: "Technician",
    skills: ["Network", "Hardware"],
    available: true,
  },
  {
    id: "tech-2",
    name: "Kabir Rao",
    email: "kabir@example.com",
    role: "Technician",
    skills: ["Software", "Access"],
    available: true,
  },
  {
    id: "admin-1",
    name: "Admin",
    email: "admin@example.com",
    role: "Admin",
  },
];

const seedIncidents: Incident[] = [
  {
    id: "INC-1042",
    title: "VPN connection keeps dropping",
    description: "VPN disconnects every few minutes when working remotely.",
    category: "Network",
    priority: "High",
    status: "In Progress",
    reporterId: "emp-1",
    reporterName: "Aarav Mehta",
    assignedTo: "tech-1",
    assignedName: "Riya Sharma",
    createdAt: new Date().toISOString(),
    attachments: [],
    messages: [],
  },
  {
    id: "INC-1041",
    title: "Unable to access shared drive",
    description: "The department shared folder shows an access denied message.",
    category: "Access",
    priority: "Medium",
    status: "Assigned",
    reporterId: "emp-1",
    reporterName: "Aarav Mehta",
    assignedTo: "tech-2",
    assignedName: "Kabir Rao",
    createdAt: new Date().toISOString(),
    attachments: [],
    messages: [],
  },
];

export class IncidentRepository {
  private read<T>(key: string, fallback: T): T {
    try {
      const value = localStorage.getItem(key);
      return value ? JSON.parse(value) as T : fallback;
    } catch {
      return fallback;
    }
  }

  private write<T>(key: string, value: T): void {
    localStorage.setItem(key, JSON.stringify(value));
  }

  getIncidents(): Incident[] {
    const current = this.read<Incident[] | null>(
      INCIDENTS_KEY,
      null
    );

    if (!current) {
      this.write(INCIDENTS_KEY, seedIncidents);
      return seedIncidents;
    }

    return current;
  }

  saveIncidents(incidents: Incident[]): void {
    this.write(INCIDENTS_KEY, incidents);
  }

  getUsers(): User[] {
    const current = this.read<User[] | null>(
      USERS_KEY,
      null
    );

    if (!current) {
      this.write(USERS_KEY, seedUsers);
      return seedUsers;
    }

    return current;
  }

  saveUsers(users: User[]): void {
    this.write(USERS_KEY, users);
  }
}
