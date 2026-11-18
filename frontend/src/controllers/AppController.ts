
import { useEffect, useMemo, useState } from "react";
import type {
  Attachment,
  Incident,
  Message,
  Priority,
  Role,
  User,
} from "../models/types";

import { IncidentRepository } from "../repositories/IncidentRepository";

const repository = new IncidentRepository();

export function useAppController() {
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [users, setUsers] = useState<User[]>([]);
  const [currentUser, setCurrentUser] = useState<User | null>(null);

  useEffect(() => {
    setIncidents(repository.getIncidents());
    setUsers(repository.getUsers());
  }, []);

  useEffect(() => {
    if (incidents.length > 0) {
      repository.saveIncidents(incidents);
    }
  }, [incidents]);

  useEffect(() => {
    if (users.length > 0) {
      repository.saveUsers(users);
    }
  }, [users]);

  const technicians = useMemo(
    () => users.filter(user => user.role === "Technician"),
    [users]
  );

  // Login
  function login(name: string, role: Role) {
    const cleanName = name.trim() || role;

    let user = users.find(
      u =>
        u.name.toLowerCase() === cleanName.toLowerCase() &&
        u.role === role
    );

    if (!user) {
      user = {
        id: `${role.toLowerCase()}-${Date.now()}`,
        name: cleanName,
        email: `${cleanName.toLowerCase().replace(/\s+/g, ".")}@resolveit.local`,
        role,
        ...(role === "Technician"
          ? { skills: ["Network", "Hardware"], available: true }
          : {}),
      };

      setUsers(prev => [...prev, user!]);
    }

    setCurrentUser(user);
  }

  function logout() {
    setCurrentUser(null);
  }

  // Create incident
  function createIncident(data: {
    title: string;
    description: string;
    category: string;
    priority: Priority;
    attachments: Attachment[];
  }) {
    if (!currentUser) return;

    const incident: Incident = {
      id: `INC-${Date.now()}`,
      ...data,
      status: "Open",
      reporterId: currentUser.id,
      reporterName: currentUser.name,
      createdAt: new Date().toISOString(),
      messages: [],
    };

    setIncidents(prev => [incident, ...prev]);
  }

  // Add discussion message
  function addMessage(incidentId: string, body: string) {
    if (!currentUser || !body.trim()) return;

    const message: Message = {
      id: `MSG-${Date.now()}`,
      authorId: currentUser.id,
      authorName: currentUser.name,
      authorRole: currentUser.role,
      body: body.trim(),
      createdAt: new Date().toISOString(),
    };

    setIncidents(prev =>
      prev.map(incident =>
        incident.id === incidentId
          ? {
              ...incident,
              messages: [...incident.messages, message],
            }
          : incident
      )
    );
  }

  // Update incident status
  function updateStatus(
    incidentId: string,
    status: Incident["status"]
  ) {
    setIncidents(prev =>
      prev.map(incident =>
        incident.id === incidentId
          ? { ...incident, status }
          : incident
      )
    );
  }

  // Assign technician
  function assignTechnician(
    incidentId: string,
    technicianId: string
  ) {
    const technician = users.find(
      user => user.id === technicianId
    );

    if (!technician) return;

    setIncidents(prev =>
      prev.map(incident =>
        incident.id === incidentId
          ? {
              ...incident,
              assignedTo: technician.id,
              assignedName: technician.name,
              status: "Assigned",
            }
          : incident
      )
    );
  }

  // Admin creates technician
  function createTechnician(
    name: string,
    email: string,
    skills: string
  ) {
    const technician: User = {
      id: `tech-${Date.now()}`,
      name: name.trim(),
      email: email.trim(),
      role: "Technician",
      skills: skills
        .split(",")
        .map(skill => skill.trim())
        .filter(Boolean),
      available: true,
    };

    setUsers(prev => [...prev, technician]);
  }

  // Change technician availability
  function toggleAvailability(userId: string) {
    setUsers(prev =>
      prev.map(user =>
        user.id === userId
          ? { ...user, available: !user.available }
          : user
      )
    );
  }

  return {
    incidents,
    users,
    technicians,
    currentUser,
    login,
    logout,
    createIncident,
    addMessage,
    updateStatus,
    assignTechnician,
    createTechnician,
    toggleAvailability,
  };
}
