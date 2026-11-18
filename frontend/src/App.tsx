
import { useEffect, useState } from "react";

type Role = "Employee" | "Technician" | "Admin";
type Status = "Open" | "Assigned" | "In Progress" | "Resolved";
type Priority = "Low" | "Medium" | "High" | "Critical";

type User = {
  id: string;
  name: string;
  role: Role;
  email?: string;
  phone?: string;
  specialization?: string;
};

type Attachment = { name: string; type: string; url: string };
type Message = {
  id: string;
  author: string;
  role: Role;
  body: string;
  time: string;
};

type Incident = {
  id: string;
  title: string;
  description: string;
  category: string;
  priority: Priority;
  status: Status;
  reporter: string;
  assignedTo: string;
  createdAt: string;
  attachments: Attachment[];
  messages: Message[];
};

const seedUsers: User[] = [
  {
    id: "admin-1",
    name: "Admin",
    role: "Admin",
    email: "admin@resolveit.com",
  },

  // Employees
  {
    id: "emp-1",
    name: "Aarav Mehta",
    role: "Employee",
    email: "aarav@resolveit.com",
    phone: "9876543212",
  },
  {
    id: "emp-2",
    name: "Ananya Rao",
    role: "Employee",
    email: "ananya@resolveit.com",
    phone: "9876543213",
  },
  {
    id: "emp-3",
    name: "Ishaan Kapoor",
    role: "Employee",
    email: "ishaan@resolveit.com",
    phone: "9876543214",
  },
  {
    id: "emp-4",
    name: "Meera Nair",
    role: "Employee",
    email: "meera@resolveit.com",
    phone: "9876543215",
  },

  // Technicians
  {
    id: "tech-1",
    name: "Riya Sharma",
    role: "Technician",
    email: "riya@resolveit.com",
    phone: "9876543210",
    specialization: "Network",
  },
  {
    id: "tech-2",
    name: "Kabir Rao",
    role: "Technician",
    email: "kabir@resolveit.com",
    phone: "9876543211",
    specialization: "Software",
  },
];

const seedIncidents: Incident[] = [
  {
    id: "INC-1042",
    title: "VPN connection keeps dropping",
    description:
      "VPN disconnects every few minutes when working remotely. This is affecting access to internal office resources.",
    category: "Network",
    priority: "High",
    status: "In Progress",
    reporter: "Aarav Mehta",
    assignedTo: "tech-1",
    createdAt: new Date().toISOString(),
    attachments: [],
    messages: [
      {
        id: "m1",
        author: "Riya Sharma",
        role: "Technician",
        body: "I'm investigating the connection logs.",
        time: new Date().toISOString(),
      },
    ],
  },
  {
    id: "INC-1041",
    title: "Unable to access shared drive",
    description:
      "The department shared folder shows an access denied message despite having the required permissions.",
    category: "Access",
    priority: "Medium",
    status: "Assigned",
    reporter: "Aarav Mehta",
    assignedTo: "tech-2",
    createdAt: new Date().toISOString(),
    attachments: [],
    messages: [],
  },
  {
    id: "INC-1040",
    title: "Printer not responding",
    description:
      "The third-floor printer is not responding to print requests. The device has been restarted but the issue persists.",
    category: "Hardware",
    priority: "Low",
    status: "Resolved",
    reporter: "Aarav Mehta",
    assignedTo: "tech-1",
    createdAt: new Date().toISOString(),
    attachments: [],
    messages: [],
  },
  {
    id: "INC-1043",
    title: "Laptop overheating",
    description:
      "Laptop fan runs continuously and the device becomes very hot after a few minutes of use.",
    category: "Hardware",
    priority: "High",
    status: "Open",
    reporter: "Ananya Rao",
    assignedTo: "",
    createdAt: new Date().toISOString(),
    attachments: [],
    messages: [],
  },
  {
    id: "INC-1044",
    title: "Email application crashing",
    description:
      "The desktop email application closes unexpectedly whenever an attachment is opened.",
    category: "Software",
    priority: "Critical",
    status: "In Progress",
    reporter: "Ishaan Kapoor",
    assignedTo: "tech-2",
    createdAt: new Date().toISOString(),
    attachments: [],
    messages: [
      {
        id: "m2",
        author: "Kabir Rao",
        role: "Technician",
        body: "Investigating the application crash logs.",
        time: new Date().toISOString(),
      },
    ],
  },
  {
    id: "INC-1045",
    title: "Wi-Fi unavailable in meeting room",
    description:
      "The Wi-Fi network is not visible in Conference Room B, although connectivity works in other office areas.",
    category: "Network",
    priority: "Medium",
    status: "Assigned",
    reporter: "Meera Nair",
    assignedTo: "tech-1",
    createdAt: new Date().toISOString(),
    attachments: [],
    messages: [],
  },
  {
    id: "INC-1046",
    title: "Software installation request",
    description:
      "Requesting installation of the latest approved design software on the workstation.",
    category: "Software",
    priority: "Low",
    status: "Open",
    reporter: "Ananya Rao",
    assignedTo: "",
    createdAt: new Date().toISOString(),
    attachments: [],
    messages: [],
  },
  {
    id: "INC-1047",
    title: "Keyboard keys not working",
    description:
      "Several keys on the workstation keyboard are unresponsive, affecting daily work.",
    category: "Hardware",
    priority: "Medium",
    status: "Resolved",
    reporter: "Ishaan Kapoor",
    assignedTo: "tech-2",
    createdAt: new Date().toISOString(),
    attachments: [],
    messages: [],
  },
];

function readStorage<T>(key: string, fallback: T): T {
  try {
    const value = localStorage.getItem(key);
    return value ? JSON.parse(value) as T : fallback;
  } catch {
    return fallback;
  }
}

function fileToAttachment(file: File): Promise<Attachment> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve({
      name: file.name,
      type: file.type,
      url: String(reader.result),
    });
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}

function StatCard({ label, value, note, icon }: {
  label: string; value: number; note: string; icon: string;
}) {
  return (
    <div className="stat-card">
      <div className="stat-top">
        <span>{label}</span><span className="stat-icon">{icon}</span>
      </div>
      <strong>{value}</strong>
      <small>{note}</small>
    </div>
  );
}

export default function App() {
const [users, setUsers] = useState<User[]>(() => {
  const saved = readStorage<User[]>("resolveit.users", []);
  return [
    ...saved.filter(user => !seedUsers.some(seed => seed.id === user.id)),
    ...seedUsers,
  ];
});

const [incidents, setIncidents] = useState<Incident[]>(() => {
  const saved = readStorage<Incident[]>("resolveit.incidents", []);
  return [
    ...saved.filter(issue => !seedIncidents.some(seed => seed.id === issue.id)),
    ...seedIncidents,
  ];
});

  const [currentUser, setCurrentUser] = useState<User | null>(null);
  const [loginName, setLoginName] = useState("");
  const [password, setPassword] = useState("");
  const [loginRole, setLoginRole] = useState<Role>("Employee");

  const [page, setPage] = useState<"overview" | "incidents" | "technicians">("overview");
  const [selectedIncident, setSelectedIncident] = useState<Incident | null>(null);
  const [showIssueForm, setShowIssueForm] = useState(false);
  const [showTechForm, setShowTechForm] = useState(false);
  const [search, setSearch] = useState("");
  const [filter, setFilter] = useState("All");

  useEffect(() => {
    localStorage.setItem("resolveit.users", JSON.stringify(users));
  }, [users]);

  useEffect(() => {
    localStorage.setItem("resolveit.incidents", JSON.stringify(incidents));
  }, [incidents]);

  useEffect(() => {
    if (selectedIncident) {
      const latest = incidents.find((i) => i.id === selectedIncident.id);
      setSelectedIncident(latest || null);
    }
  }, [incidents]);

  const technicians = users.filter((u) => u.role === "Technician");

  function login() {
    if (!loginName.trim() || !password.trim()) return;

    let user = users.find(
      (u) => u.name.toLowerCase() === loginName.trim().toLowerCase()
        && u.role === loginRole
    );

    if (!user) {
      user = {
        id: `${loginRole.toLowerCase()}-${Date.now()}`,
        name: loginName.trim(),
        role: loginRole,
      };
      setUsers((prev) => [...prev, user!]);
    }

    setCurrentUser(user);
    setPage("overview");
    setPassword("");
  }

  function logout() {
    setCurrentUser(null);
    setSelectedIncident(null);
    setPage("overview");
    setLoginName("");
    setPassword("");
  }

  async function createIncident(form: HTMLFormElement) {
    if (!currentUser) return;
    const data = new FormData(form);
    const files = Array.from(data.getAll("attachments")) as File[];
    const attachments = await Promise.all(
      files.filter((file) => file.size > 0).map(fileToAttachment)
    );

    const incident: Incident = {
      id: `INC-${Date.now().toString().slice(-6)}`,
      title: String(data.get("title")),
      description: String(data.get("description")),
      category: String(data.get("category")),
      priority: String(data.get("priority")) as Priority,
      status: "Open",
      reporter: currentUser.name,
      assignedTo: "",
      createdAt: new Date().toISOString(),
      attachments,
      messages: [],
    };

    setIncidents((prev) => [incident, ...prev]);
    setShowIssueForm(false);
    setPage("incidents");
  }

  function createTechnician(form: HTMLFormElement) {
    const data = new FormData(form);
    const name = String(data.get("name")).trim();
    const email = String(data.get("email")).trim();

    if (!name || !email) return;

    const technician: User = {
      id: `tech-${Date.now()}`,
      name,
      role: "Technician",
      email,
      phone: String(data.get("phone")),
      specialization: String(data.get("specialization")),
    };

    setUsers((prev) => [...prev, technician]);
    setShowTechForm(false);
  }

  function updateIncident(id: string, changes: Partial<Incident>) {
    setIncidents((prev) =>
      prev.map((incident) =>
        incident.id === id ? { ...incident, ...changes } : incident
      )
    );
  }

  function addMessage(id: string, body: string) {
    if (!currentUser || !body.trim()) return;
    const message: Message = {
      id: `msg-${Date.now()}`,
      author: currentUser.name,
      role: currentUser.role,
      body: body.trim(),
      time: new Date().toISOString(),
    };

    setIncidents((prev) =>
      prev.map((incident) =>
        incident.id === id
          ? { ...incident, messages: [...incident.messages, message] }
          : incident
      )
    );
  }

  if (!currentUser) {
    return (
      <div className="login-page">
        <div className="login-card">
          <div className="logo-mark">R</div>
          <p className="eyebrow">INCIDENT MANAGEMENT SYSTEM</p>
          <h1>Welcome to ResolveIT</h1>
          <p className="muted">Sign in to manage and track your issues.</p>

          <label>Your name</label>
          <input value={loginName} onChange={(e) => setLoginName(e.target.value)}
            placeholder="Enter your name" />

          <label>Password</label>
          <input type="password" value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="Enter your password"
            onKeyDown={(e) => e.key === "Enter" && login()} />

          <label>Select your role</label>
          <div className="role-options">
            {(["Employee", "Technician", "Admin"] as Role[]).map((role) => (
              <button key={role} type="button"
                className={`role-button ${loginRole === role ? "selected" : ""}`}
                onClick={() => setLoginRole(role)}>
                <span className="role-symbol">
                  {role === "Employee" ? "◉" : role === "Technician" ? "⚙" : "◆"}
                </span>
                {role}
              </button>
            ))}
          </div>

          <button className="primary-button login-button" onClick={login}>
            Continue <span>→</span>
          </button>
          <p className="login-note">Demo login · Any non-empty password works for now</p>
        </div>
      </div>
    );
  }

  // Each role sees only its own incidents, except Admin who sees office-wide data.
  const myIncidents = incidents.filter((incident) => {
    if (currentUser.role === "Admin") return true;
    if (currentUser.role === "Employee") return incident.reporter === currentUser.name;
    return incident.assignedTo === currentUser.id;
  });

  const openCount = myIncidents.filter((i) => i.status === "Open").length;
  const assignedCount = myIncidents.filter((i) => i.status === "Assigned").length;
  const progressCount = myIncidents.filter((i) => i.status === "In Progress").length;
  const resolvedCount = myIncidents.filter((i) => i.status === "Resolved").length;
  const unresolvedCount = myIncidents.filter((i) => i.status !== "Resolved").length;

  const categories = ["Network", "Hardware", "Software", "Access", "Other"];
  const statusTypes: Status[] = ["Open", "Assigned", "In Progress", "Resolved"];

  const visibleIncidents = myIncidents.filter((incident) => {
    const matchesSearch =
      incident.title.toLowerCase().includes(search.toLowerCase()) ||
      incident.id.toLowerCase().includes(search.toLowerCase());
    const matchesFilter = filter === "All" || incident.status === filter;
    return matchesSearch && matchesFilter;
  });

  function openPage(next: "overview" | "incidents" | "technicians") {
    setPage(next);
    setSearch("");
    setFilter("All");
  }

  const pageTitle = page === "overview" ? "Overview"
    : page === "incidents" ? "Incidents" : "Technician management";

  return (
    <div className="app-layout">
      <aside className="sidebar">
        <div className="brand">
          <div className="logo-mark small">R</div>
          <span>Resolve<span className="brand-accent">IT</span></span>
        </div>

        <div className="sidebar-section-label">WORKSPACE</div>
        <button className={`side-link ${page === "overview" ? "active" : ""}`}
          onClick={() => openPage("overview")}>▦ <span>Overview</span></button>
        <button className={`side-link ${page === "incidents" ? "active" : ""}`}
          onClick={() => openPage("incidents")}>▤ <span>Incidents</span>
          <span className="side-count">{unresolvedCount}</span></button>

        {currentUser.role === "Admin" && (
          <>
            <div className="sidebar-section-label">MANAGEMENT</div>
            <button className={`side-link ${page === "technicians" ? "active" : ""}`}
              onClick={() => openPage("technicians")}>♙ <span>Technicians</span>
              <span className="side-count">{technicians.length}</span></button>
          </>
        )}

        <div className="sidebar-user">
          <div className="user-avatar">{currentUser.name.charAt(0).toUpperCase()}</div>
          <div className="user-info">
            <strong>{currentUser.name}</strong><span>{currentUser.role}</span>
          </div>
          <button className="logout-button" onClick={logout} title="Log out">↪</button>
        </div>
      </aside>

      <main className="main-content">
        <header className="topbar">
          <div><span className="muted">Workspace /</span> {pageTitle}</div>
          <div className="topbar-right"><span className="online-dot" />
            <span>{currentUser.role} view</span>
            <div className="user-avatar small-avatar">{currentUser.name.charAt(0).toUpperCase()}</div>
          </div>
        </header>

        <div className="page-content">
          {page === "overview" && (
            <>
              <div className="page-heading">
                <div>
                  <p className="eyebrow">{currentUser.role.toUpperCase()} OVERVIEW</p>
                  <h1>Good to see you, {currentUser.name.split(" ")[0]}</h1>
                  <p className="muted">
                    {currentUser.role === "Admin"
                      ? "Office-wide incident and technician performance."
                      : "Here's an overview of your incidents and their progress."}
                  </p>
                </div>
                {currentUser.role === "Employee" && (
                  <button className="primary-button" onClick={() => setShowIssueForm(true)}>+ Raise an issue</button>
                )}
                {currentUser.role === "Admin" && (
                  <button className="primary-button" onClick={() => setShowTechForm(true)}>+ Add technician</button>
                )}
              </div>

              <div className="stats-grid">
                <StatCard label="Total incidents" value={myIncidents.length} note="In your view" icon="▤" />
                <StatCard label="Unresolved" value={unresolvedCount} note="Still need attention" icon="◷" />
                <StatCard label="In progress" value={progressCount} note="Being worked on" icon="◌" />
                <StatCard label="Resolved" value={resolvedCount} note="Successfully completed" icon="✓" />
              </div>

              <div className="analytics-grid">
                <section className="panel">
                  <h2>Incidents by status</h2>
                  <p className="muted">Current workload breakdown</p>
                  {statusTypes.map((status) => {
                    const count = myIncidents.filter((i) => i.status === status).length;
                    const percent = myIncidents.length ? (count / myIncidents.length) * 100 : 0;
                    return (
                      <div className="bar-row" key={status}>
                        <div className="bar-label"><span>{status}</span><strong>{count}</strong></div>
                        <div className="bar-track"><div className={`bar-fill ${status.toLowerCase().replaceAll(" ", "-")}`} style={{ width: `${percent}%` }} /></div>
                      </div>
                    );
                  })}
                </section>

                <section className="panel">
                  <h2>Incidents by category</h2>
                  <p className="muted">Where issues are being reported</p>
                  {categories.map((category) => {
                    const count = myIncidents.filter((i) => i.category === category).length;
                    const percent = myIncidents.length ? (count / myIncidents.length) * 100 : 0;
                    return (
                      <div className="bar-row" key={category}>
                        <div className="bar-label"><span>{category}</span><strong>{count}</strong></div>
                        <div className="bar-track"><div className="bar-fill category-fill" style={{ width: `${percent}%` }} /></div>
                      </div>
                    );
                  })}
                </section>
              </div>

              {currentUser.role === "Admin" && (
                <section className="panel overview-team">
                  <div className="section-heading">
                    <div><h2>Technician performance</h2><p className="muted">Assigned work and resolution progress across the office.</p></div>
                    <button className="secondary-button" onClick={() => openPage("technicians")}>View all technicians →</button>
                  </div>
                  <div className="table-wrap">
                    <table className="data-table">
                      <thead><tr><th>Technician</th><th>Assigned</th><th>Resolved</th><th>Pending</th><th>Completion</th></tr></thead>
                      <tbody>
                        {technicians.map((tech) => {
                          const assigned = incidents.filter((i) => i.assignedTo === tech.id);
                          const resolved = assigned.filter((i) => i.status === "Resolved").length;
                          const pending = assigned.length - resolved;
                          const completion = assigned.length ? Math.round(resolved / assigned.length * 100) : 0;
                          return (
                            <tr key={tech.id}>
                              <td><strong>{tech.name}</strong></td><td>{assigned.length}</td><td>{resolved}</td><td>{pending}</td>
                              <td><div className="completion-cell"><span>{completion}%</span><div className="mini-track"><div style={{ width: `${completion}%` }} /></div></div></td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                </section>
              )}

              <section className="panel recent-panel">
                <div className="section-heading">
                  <div><h2>Recent incidents</h2><p className="muted">Latest issues in your view.</p></div>
                  <button className="secondary-button" onClick={() => openPage("incidents")}>View all →</button>
                </div>
                <IncidentList incidents={myIncidents.slice(0, 4)} technicians={technicians} onSelect={setSelectedIncident} />
              </section>
            </>
          )}

          {page === "incidents" && (
            <>
              <div className="page-heading">
                <div><p className="eyebrow">INCIDENT REGISTER</p><h1>{currentUser.role === "Admin" ? "All office incidents" : currentUser.role === "Technician" ? "My assigned incidents" : "My reported incidents"}</h1>
                  <p className="muted">{currentUser.role === "Admin" ? "Track and manage every incident across the office." : "Track the status and details of your incidents."}</p></div>
                {currentUser.role === "Employee" && <button className="primary-button" onClick={() => setShowIssueForm(true)}>+ Raise an issue</button>}
              </div>
              <div className="stats-grid compact-stats">
                <StatCard label="Total" value={myIncidents.length} note="Incidents" icon="▤" />
                <StatCard label="Open" value={openCount} note="Awaiting assignment" icon="◷" />
                <StatCard label="Assigned" value={assignedCount} note="Assigned to technician" icon="♙" />
                <StatCard label="Resolved" value={resolvedCount} note="Completed" icon="✓" />
              </div>
              <section className="panel">
                <div className="section-heading">
                  <div><h2>Incident register</h2><p className="muted">Search, filter and open an incident to see its details.</p></div>
                  <input className="search-input" placeholder="Search incidents..." value={search} onChange={(e) => setSearch(e.target.value)} />
                </div>
                <div className="filter-row">
                  {["All", "Open", "Assigned", "In Progress", "Resolved"].map((status) => (
                    <button key={status} className={`filter-button ${filter === status ? "active" : ""}`} onClick={() => setFilter(status)}>{status}</button>
                  ))}
                </div>
                <IncidentList incidents={visibleIncidents} technicians={technicians} onSelect={setSelectedIncident} />
              </section>
            </>
          )}

          {page === "technicians" && currentUser.role === "Admin" && (
            <>
              <div className="page-heading">
                <div><p className="eyebrow">TEAM MANAGEMENT</p><h1>Technicians</h1><p className="muted">Manage the technical team and monitor their workload.</p></div>
                <button className="primary-button" onClick={() => setShowTechForm(true)}>+ Add technician</button>
              </div>
              <div className="stats-grid">
                <StatCard label="Technicians" value={technicians.length} note="Team members" icon="♙" />
                <StatCard label="Assigned work" value={incidents.filter((i) => i.assignedTo).length} note="Across the office" icon="▤" />
                <StatCard label="Resolved work" value={incidents.filter((i) => i.status === "Resolved").length} note="Completed incidents" icon="✓" />
                <StatCard label="Unresolved work" value={incidents.filter((i) => i.assignedTo && i.status !== "Resolved").length} note="Still in progress" icon="◷" />
              </div>
              <div className="technician-grid">
                {technicians.map((tech) => {
                  const work = incidents.filter((i) => i.assignedTo === tech.id);
                  const resolved = work.filter((i) => i.status === "Resolved").length;
                  const pending = work.length - resolved;
                  const completion = work.length ? Math.round(resolved / work.length * 100) : 0;
                  return (
                    <section className="panel technician-profile" key={tech.id}>
                      <div className="technician-profile-head">
                        <div className="user-avatar large-avatar">{tech.name.charAt(0).toUpperCase()}</div>
                        <div><h3>{tech.name}</h3><span className="muted">{tech.specialization || "General technician"}</span></div>
                      </div>
                      <div className="technician-contact">
                        <p>{tech.email || "No email added"}</p>
                        <p>{tech.phone || "No contact number added"}</p>
                      </div>
                      <div className="tech-work-stats">
                        <div><strong>{work.length}</strong><span>Assigned</span></div>
                        <div><strong>{resolved}</strong><span>Resolved</span></div>
                        <div><strong>{pending}</strong><span>Pending</span></div>
                      </div>
                      <div className="completion-cell full-completion">
                        <span>Completion rate <strong>{completion}%</strong></span>
                        <div className="mini-track"><div style={{ width: `${completion}%` }} /></div>
                      </div>
                      <button className="secondary-button full-width" onClick={() => { setSearch(""); setFilter("All"); openPage("incidents"); }}>
                        View incident register
                      </button>
                    </section>
                  );
                })}
              </div>
            </>
          )}
        </div>
      </main>

      {showIssueForm && (
        <div className="modal-overlay" onClick={() => setShowIssueForm(false)}>
          <form className="modal-card" onClick={(e) => e.stopPropagation()}
            onSubmit={(e) => { e.preventDefault(); void createIncident(e.currentTarget); }}>
            <div className="modal-heading"><div><p className="eyebrow">NEW REQUEST</p><h2>Raise an issue</h2></div>
              <button type="button" className="close-button" onClick={() => setShowIssueForm(false)}>×</button></div>
            <label>Issue title</label><input name="title" required placeholder="Briefly describe the issue" />
            <label>Description</label><textarea name="description" required rows={4} placeholder="Explain what happened..." />
            <div className="form-two-columns">
              <div><label>Category</label><select name="category"><option>Network</option><option>Hardware</option><option>Software</option><option>Access</option><option>Other</option></select></div>
              <div><label>Priority</label><select name="priority"><option>Low</option><option>Medium</option><option>High</option><option>Critical</option></select></div>
            </div>
            <label>Attach screenshots or files</label>
            <div className="upload-area"><span className="upload-symbol">⇧</span><strong>Choose screenshots or files</strong><span>PNG, JPG, PDF or other files</span><input type="file" name="attachments" multiple /></div>
            <div className="modal-actions"><button type="button" className="secondary-button" onClick={() => setShowIssueForm(false)}>Cancel</button><button type="submit" className="primary-button">Submit issue</button></div>
          </form>
        </div>
      )}

      {showTechForm && (
        <div className="modal-overlay" onClick={() => setShowTechForm(false)}>
          <form className="modal-card compact-modal" onClick={(e) => e.stopPropagation()}
            onSubmit={(e) => { e.preventDefault(); createTechnician(e.currentTarget); }}>
            <div className="modal-heading"><div><p className="eyebrow">TEAM MANAGEMENT</p><h2>Add technician</h2></div>
              <button type="button" className="close-button" onClick={() => setShowTechForm(false)}>×</button></div>
            <label>Full name</label><input name="name" required placeholder="Enter technician's full name" />
            <label>Email address</label><input name="email" type="email" required placeholder="technician@company.com" />
            <label>Contact number</label><input name="phone" type="tel" placeholder="Enter contact number" />
            <label>Specialization</label>
            <select name="specialization"><option>Network</option><option>Hardware</option><option>Software</option><option>Access Management</option><option>General Support</option></select>
            <div className="modal-actions"><button type="button" className="secondary-button" onClick={() => setShowTechForm(false)}>Cancel</button><button type="submit" className="primary-button">Add technician</button></div>
          </form>
        </div>
      )}

      {selectedIncident && (
        <div className="modal-overlay detail-overlay" onClick={() => setSelectedIncident(null)}>
          <div className="detail-modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-heading"><div><p className="eyebrow">{selectedIncident.id} · INCIDENT DETAILS</p><h2>{selectedIncident.title}</h2></div>
              <button className="close-button" onClick={() => setSelectedIncident(null)}>×</button></div>
            <div className="detail-layout">
              <div className="detail-main">
                <section className="detail-section"><h3>Description</h3><p>{selectedIncident.description}</p>
                  <div className="detail-meta">
                    <div><span>Category</span><strong>{selectedIncident.category}</strong></div>
                    <div><span>Priority</span><strong>{selectedIncident.priority}</strong></div>
                    <div><span>Reported by</span><strong>{selectedIncident.reporter}</strong></div>
                    <div><span>Created</span><strong>{new Date(selectedIncident.createdAt).toLocaleString()}</strong></div>
                  </div>
                </section>
                <section className="detail-section"><h3>Attachments</h3>
                  {selectedIncident.attachments.length === 0 ? <p className="muted">No attachments added.</p> :
                    <div className="attachment-grid">{selectedIncident.attachments.map((file, index) => (
                      <a href={file.url} download={file.name} target="_blank" rel="noreferrer" key={index} className="attachment-link">
                        {file.type.startsWith("image/") ? <img src={file.url} alt={file.name} /> : <span className="file-icon">📎</span>}
                        <span>{file.name}</span>
                      </a>
                    ))}</div>}
                </section>
                <section className="detail-section"><h3>Discussion</h3>
                  <div className="message-list">
                    {selectedIncident.messages.length === 0 ? <p className="muted">No messages yet. Start the conversation.</p> :
                      selectedIncident.messages.map((message) => (
                        <div className="message" key={message.id}><div className="message-top"><strong>{message.author}</strong><span>{message.role}</span><time>{new Date(message.time).toLocaleString()}</time></div><p>{message.body}</p></div>
                      ))}
                  </div>
                  <form className="message-form" onSubmit={(e) => { e.preventDefault(); const form = e.currentTarget; const body = new FormData(form).get("message"); addMessage(selectedIncident.id, String(body || "")); form.reset(); }}>
                    <input name="message" required placeholder="Write a message to the team..." /><button className="primary-button">Send</button>
                  </form>
                </section>
              </div>
              <aside className="detail-sidebar">
                <section className="detail-section"><h3>Status</h3>
                  <span className={`status-badge ${selectedIncident.status.toLowerCase().replaceAll(" ", "-")}`}>{selectedIncident.status}</span>
                  {(currentUser.role === "Technician" || currentUser.role === "Admin") && <>
                    <label>Update status</label>
                    <select value={selectedIncident.status} onChange={(e) => updateIncident(selectedIncident.id, { status: e.target.value as Status })}>
                      <option>Open</option><option>Assigned</option><option>In Progress</option><option>Resolved</option>
                    </select>
                  </>}
                </section>
                <section className="detail-section"><h3>Assignment</h3>
                  <p className="muted">Assigned technician</p>
                  <strong>{technicians.find((t) => t.id === selectedIncident.assignedTo)?.name || "Not assigned"}</strong>
                  {currentUser.role === "Admin" && <>
                    <label>Assign technician</label>
                    <select value={selectedIncident.assignedTo} onChange={(e) => updateIncident(selectedIncident.id, { assignedTo: e.target.value, status: e.target.value ? "Assigned" : "Open" })}>
                      <option value="">Unassigned</option>
                      {technicians.map((tech) => <option value={tech.id} key={tech.id}>{tech.name}</option>)}
                    </select>
                  </>}
                </section>
              </aside>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function IncidentList({ incidents, technicians, onSelect }: {
  incidents: Incident[];
  technicians: User[];
  onSelect: (incident: Incident) => void;
}) {
  if (!incidents.length) {
    return <div className="empty-state"><div className="empty-icon">▤</div><h3>No incidents found</h3><p>There are no incidents matching this view.</p></div>;
  }

  return (
    <div className="incident-list">
      {incidents.map((incident) => {
        const assigned = technicians.find((t) => t.id === incident.assignedTo);
        return (
          <button className="incident-card" key={incident.id} onClick={() => onSelect(incident)}>
            <div className="incident-card-main">
              <div className="incident-card-title"><span className="incident-id">{incident.id}</span>
                <span className={`status-badge ${incident.status.toLowerCase().replaceAll(" ", "-")}`}>{incident.status}</span></div>
              <h3>{incident.title}</h3><p>{incident.description}</p>
              <div className="incident-details-row">
                <span>◈ {incident.category}</span><span className={`priority-text ${incident.priority.toLowerCase()}`}>● {incident.priority} priority</span>
                <span>Reported by {incident.reporter}</span><span>{assigned ? `Assigned: ${assigned.name}` : "Unassigned"}</span>
              </div>
            </div>
            <span className="card-arrow">→</span>
          </button>
        );
      })}
    </div>
  );
}
