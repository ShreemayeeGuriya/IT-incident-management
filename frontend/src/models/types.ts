
export type Role = "Employee" | "Technician" | "Admin";

export type Status =
  | "Open"
  | "Assigned"
  | "In Progress"
  | "Resolved";

export type Priority =
  | "Low"
  | "Medium"
  | "High"
  | "Critical";

export interface User {
  id: string;
  name: string;
  email: string;
  role: Role;
  skills?: string[];
  available?: boolean;
}

export interface Attachment {
  name: string;
  type: string;
  dataUrl: string;
}

export interface Message {
  id: string;
  authorId: string;
  authorName: string;
  authorRole: Role;
  body: string;
  createdAt: string;
}

export interface Incident {
  id: string;
  title: string;
  description: string;
  category: string;
  priority: Priority;
  status: Status;
  reporterId: string;
  reporterName: string;
  assignedTo?: string;
  assignedName?: string;
  createdAt: string;
  attachments: Attachment[];
  messages: Message[];
}
