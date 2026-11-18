
import { useMemo, useState } from "react";
import type { FormEvent } from "react";
import type {
  Attachment,
  Incident,
  Priority,
  User,
} from "../models/types";
import { IncidentDetailView } from "./IncidentDetailView";

interface Props {
  user: User;
  incidents: Incident[];
  technicians: User[];
  onLogout: () => void;
  onCreateIncident: (data: {
    title: string;
    description: string;
    category: string;
    priority: Priority;
    attachments: Attachment[];
  }) => void;
  onMessage: (id: string, body: string) => void;
  onStatus: (id: string, status: Incident["status"]) => void;
  onAssign: (id: string, techId: string) => void;
  onCreateTechnician: (
    name: string,
    email: string,
    skills: string
  ) => void;
  onToggleAvailability: (id: string) => void;
}

const formatDate = (date: string) =>
  new Date(date).toLocaleString([], {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });

export function DashboardView(props: Props) {
  const {
    user,
    incidents,
    technicians,
  } = props;

  const [selected, setSelected] = useState<Incident | null>(null);
  const [showRaise, setShowRaise] = useState(false);
  const [showTech, setShowTech] = useState(false);

  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("All");
  const [categoryFilter, setCategoryFilter] = useState("All");
  const [tab, setTab] = useState("Overview");

  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [category, setCategory] = useState("Network");
  const [priority, setPriority] = useState<Priority>("Medium");
  const [attachments, setAttachments] = useState<Attachment[]>([]);

  const [techName, setTechName] = useState("");
  const [techEmail, setTechEmail] = useState("");
  const [techSkills, setTechSkills] = useState("Network, Hardware");

  const visible = useMemo(() => {
    return incidents.filter(i => {
      const roleMatch =
        user.role === "Admin" ||
        (user.role === "Employee"
          ? i.reporterId === user.id
          : i.assignedTo === user.id || !i.assignedTo);

      const searchMatch = (
        `${i.id} ${i.title} ${i.description} ${i.reporterName} ${i.assignedName || ""}`
      ).toLowerCase().includes(search.toLowerCase());

      return (
        roleMatch &&
        searchMatch &&
        (statusFilter === "All" || i.status === statusFilter) &&
        (categoryFilter === "All" || i.category === categoryFilter)
      );
    });
  }, [incidents, user, search, statusFilter, categoryFilter]);

  const stats = [
    ["Total incidents", visible.length],
    ["Open", visible.filter(i => i.status === "Open").length],
    [
      "Being handled",
      visible.filter(i =>
        ["Assigned", "In Progress"].includes(i.status)
      ).length,
    ],
    [
      "Resolved",
      visible.filter(i => i.status === "Resolved").length,
    ],
  ];

  async function handleFiles(files: FileList | null) {
    if (!files) return;

    const loaded = await Promise.all(
      Array.from(files).map(file =>
        new Promise<Attachment>((resolve, reject) => {
          const reader = new FileReader();

          reader.onload = () =>
            resolve({
              name: file.name,
              type: file.type || "application/octet-stream",
              dataUrl: String(reader.result),
            });

          reader.onerror = () => reject(reader.error);
          reader.readAsDataURL(file);
        })
      )
    );

    setAttachments(prev => [...prev, ...loaded]);
  }

  function submitIncident(e: FormEvent) {
    e.preventDefault();

    props.onCreateIncident({
      title,
      description,
      category,
      priority,
      attachments,
    });

    setTitle("");
    setDescription("");
    setAttachments([]);
    setShowRaise(false);
  }

  const heading =
    user.role === "Employee"
      ? "My workspace"
      : user.role === "Technician"
      ? "Technician workspace"
      : "Admin workspace";

  return (
    <div className="app-shell">

      {/* SIDEBAR */}

      <aside className="sidebar">

        <div className="brand">
          <span className="brand-mark">R</span>
          <b>ResolveIT</b>
        </div>

        <div className="side-label">WORKSPACE</div>

        {[
          "Overview",
          "Incidents",
          ...(user.role === "Admin" ? ["Technicians"] : []),
        ].map(item => (
          <button
            key={item}
            className={`nav-item ${tab === item ? "active" : ""}`}
            onClick={() => setTab(item)}
          >
            <span>
              {item === "Overview"
                ? "▦"
                : item === "Incidents"
                ? "▤"
                : "♙"}
            </span>

            {item}
          </button>
        ))}

        <div className="sidebar-bottom">
          <div className="avatar">
            {user.name.slice(0, 2).toUpperCase()}
          </div>

          <div className="user-mini">
            <b>{user.name}</b>
            <span>{user.role}</span>
          </div>

          <button
            className="logout"
            onClick={props.onLogout}
            title="Sign out"
          >
            ↪
          </button>
        </div>

      </aside>

      {/* MAIN AREA */}

      <main className="main-area">

        <header className="topbar">
          <div className="crumb">
            Workspace <span>/</span> {user.role} view
          </div>

          <div className="top-actions">
            <input
              className="global-search"
              value={search}
              onChange={e => setSearch(e.target.value)}
              placeholder="Search incidents..."
            />

            <span className="top-avatar">
              {user.name.slice(0, 2).toUpperCase()}
            </span>
          </div>
        </header>

        <div className="page-content">

          {/* PAGE HEADING */}

          <div className="page-heading">
            <div>
              <div className="eyebrow">
                INCIDENT MANAGEMENT
              </div>

              <h1>
                {tab === "Technicians"
                  ? "Technician management"
                  : tab === "Incidents"
                  ? "Incidents"
                  : heading}
              </h1>

              <p>
                {user.role === "Employee"
                  ? "Report an issue and track it until it is resolved."
                  : user.role === "Technician"
                  ? "Review assigned incidents and update employees."
                  : "Monitor incidents and manage your support team."}
              </p>
            </div>

            <div className="heading-actions">
              {user.role === "Employee" && (
                <button
                  className="primary"
                  onClick={() => setShowRaise(true)}
                >
                  ＋ Raise an issue
                </button>
              )}

              {user.role === "Admin" && (
                <button
                  className="primary"
                  onClick={() => setShowTech(true)}
                >
                  ＋ Create technician
                </button>
              )}
            </div>
          </div>

          {/* TECHNICIAN MANAGEMENT */}

          {tab === "Technicians" && user.role === "Admin" ? (

            <section className="panel">

              <div className="panel-head">
                <div>
                  <h2>Technician directory</h2>
                  <p>{technicians.length} technicians registered</p>
                </div>
              </div>

              {technicians.map(t => (
                <div className="tech-row" key={t.id}>

                  <div className="avatar">
                    {t.name.slice(0, 2).toUpperCase()}
                  </div>

                  <div className="tech-info">
                    <b>{t.name}</b>
                    <span>
                      {t.email} · {(t.skills || []).join(", ")}
                    </span>
                  </div>

                  <span
                    className={
                      t.available
                        ? "availability available"
                        : "availability busy"
                    }
                  >
                    {t.available ? "Available" : "Busy"}
                  </span>

                  <button
                    className="secondary small"
                    onClick={() => props.onToggleAvailability(t.id)}
                  >
                    Toggle availability
                  </button>

                </div>
              ))}

            </section>

          ) : (

            <>

              {/* STATISTICS */}

              <section className="stats-grid">

                {stats.map(([label, value]) => (
                  <div className="stat-card" key={String(label)}>
                    <span>{label}</span>
                    <strong>{value}</strong>
                    <small>Current workspace</small>
                  </div>
                ))}

              </section>

              {/* INCIDENTS */}

              <div className="content-grid">

                <section className="panel incident-panel">

                  <div className="panel-head">

                    <div>
                      <h2>
                        {user.role === "Employee"
                          ? "Your reported incidents"
                          : user.role === "Technician"
                          ? "Incidents to handle"
                          : "All incidents"}
                      </h2>

                      <p>{visible.length} records found</p>
                    </div>

                    <div className="filters">

                      <select
                        value={statusFilter}
                        onChange={e => setStatusFilter(e.target.value)}
                      >
                        <option>All</option>
                        {[
                          "Open",
                          "Assigned",
                          "In Progress",
                          "Resolved",
                        ].map(s => (
                          <option key={s}>{s}</option>
                        ))}
                      </select>

                      <select
                        value={categoryFilter}
                        onChange={e => setCategoryFilter(e.target.value)}
                      >
                        <option>All</option>
                        {[
                          "Network",
                          "Hardware",
                          "Software",
                          "Access",
                          "Other",
                        ].map(c => (
                          <option key={c}>{c}</option>
                        ))}
                      </select>

                    </div>
                  </div>

                  <div className="incident-list">

                    {visible.map(i => (
                      <button
                        className="incident-row"
                        key={i.id}
                        onClick={() => setSelected(i)}
                      >

                        <div className="incident-row-top">
                          <b>{i.title}</b>

                          <span
                            className={`status ${i.status
                              .toLowerCase()
                              .replace(" ", "-")}`}
                          >
                            {i.status}
                          </span>
                        </div>

                        <div className="incident-meta">
                          <span>{i.id}</span>
                          <i>·</i>
                          <span>{i.category}</span>
                          <i>·</i>
                          <span>{formatDate(i.createdAt)}</span>

                          <span
                            className={`priority ${i.priority.toLowerCase()}`}
                          >
                            {i.priority}
                          </span>
                        </div>

                        <p>{i.description}</p>

                        <div className="incident-foot">
                          <span>
                            {user.role === "Admin"
                              ? `Reported by: ${i.reporterName}`
                              : `Assigned: ${i.assignedName || "Awaiting assignment"}`}
                          </span>

                          <span>
                            {i.messages.length} messages　↗
                          </span>
                        </div>

                      </button>
                    ))}

                    {visible.length === 0 && (
                      <div className="empty-state">
                        No incidents match these filters.
                      </div>
                    )}

                  </div>
                </section>

                {/* RIGHT PANEL */}

                <aside className="side-panel">

                  {user.role === "Admin" ? (

                    <>
                      <h2>Technician availability</h2>
                      <p className="muted tiny">Current support team</p>

                      {technicians.map(t => (
                        <div className="mini-tech" key={t.id}>

                          <div className="avatar">
                            {t.name.slice(0, 2).toUpperCase()}
                          </div>

                          <div>
                            <b>{t.name}</b>
                            <span>{(t.skills || []).join(", ")}</span>
                          </div>

                          <i
                            className={
                              t.available ? "dot green" : "dot"
                            }
                          />

                        </div>
                      ))}
                    </>

                  ) : (

                    <>
                      <div className="detail-preview">
                        <div className="eyebrow">QUICK GUIDE</div>

                        <h2>
                          {user.role === "Employee"
                            ? "Need help?"
                            : "Keep incidents moving"}
                        </h2>

                        <p>
                          Select an incident to view its details,
                          attachments and discussion.
                        </p>
                      </div>

                      <div className="guide-line">
                        <span>01</span>
                        <p>Open an incident</p>
                      </div>

                      <div className="guide-line">
                        <span>02</span>
                        <p>Review details and updates</p>
                      </div>

                      <div className="guide-line">
                        <span>03</span>
                        <p>Discuss the issue</p>
                      </div>
                    </>

                  )}

                </aside>

              </div>
            </>
          )}

        </div>
      </main>

      {/* INCIDENT DETAIL WINDOW */}

      {selected && (
        <IncidentDetailView
          incident={
            incidents.find(i => i.id === selected.id) || selected
          }
          role={user.role}
          technicians={technicians}
          onClose={() => setSelected(null)}
          onMessage={props.onMessage}
          onStatus={props.onStatus}
          onAssign={props.onAssign}
        />
      )}

      {/* RAISE ISSUE WINDOW */}

      {showRaise && (

        <div
          className="modal-backdrop"
          onMouseDown={e => {
            if (e.target === e.currentTarget) {
              setShowRaise(false);
            }
          }}
        >

          <section className="form-modal">

            <header className="detail-header">
              <div>
                <div className="eyebrow">NEW REQUEST</div>
                <h2>Raise an issue</h2>
              </div>

              <button
                className="icon-button"
                onClick={() => setShowRaise(false)}
              >
                ✕
              </button>
            </header>

            <form onSubmit={submitIncident} className="stack-form">

              <label>Issue title</label>

              <input
                value={title}
                onChange={e => setTitle(e.target.value)}
                required
                placeholder="Briefly describe the issue"
              />

              <div className="two-fields">

                <div>
                  <label>Category</label>
                  <select
                    value={category}
                    onChange={e => setCategory(e.target.value)}
                  >
                    {[
                      "Network",
                      "Hardware",
                      "Software",
                      "Access",
                      "Other",
                    ].map(c => (
                      <option key={c}>{c}</option>
                    ))}
                  </select>
                </div>

                <div>
                  <label>Priority</label>
                  <select
                    value={priority}
                    onChange={e =>
                      setPriority(e.target.value as Priority)
                    }
                  >
                    {["Low", "Medium", "High", "Critical"].map(p => (
                      <option key={p}>{p}</option>
                    ))}
                  </select>
                </div>

              </div>

              <label>Description</label>

              <textarea
                value={description}
                onChange={e => setDescription(e.target.value)}
                required
                rows={4}
                placeholder="Describe what happened..."
              />

              <label>Attachments / screenshots</label>

              <label className="upload-zone">
                <span className="upload-symbol">＋</span>
                <b>Choose files to attach</b>
                <small>Images, screenshots or documents</small>

                <input
                  type="file"
                  multiple
                  accept="image/*,.pdf,.txt,.doc,.docx"
                  onChange={e => void handleFiles(e.target.files)}
                />
              </label>

              {attachments.map((a, i) => (
                <div className="attached-list" key={i}>
                  <div>
                    <span>▧ {a.name}</span>
                    <button
                      type="button"
                      onClick={() =>
                        setAttachments(prev =>
                          prev.filter((_, n) => n !== i)
                        )
                      }
                    >
                      Remove
                    </button>
                  </div>
                </div>
              ))}

              <div className="form-actions">

                <button
                  type="button"
                  className="secondary"
                  onClick={() => setShowRaise(false)}
                >
                  Cancel
                </button>

                <button className="primary" type="submit">
                  Submit issue
                </button>

              </div>

            </form>
          </section>
        </div>
      )}

      {/* CREATE TECHNICIAN WINDOW */}

      {showTech && (

        <div
          className="modal-backdrop"
          onMouseDown={e => {
            if (e.target === e.currentTarget) {
              setShowTech(false);
            }
          }}
        >

          <section className="form-modal narrow">

            <header className="detail-header">
              <div>
                <div className="eyebrow">TEAM MANAGEMENT</div>
                <h2>Create technician</h2>
              </div>

              <button
                className="icon-button"
                onClick={() => setShowTech(false)}
              >
                ✕
              </button>
            </header>

            <form
              className="stack-form"
              onSubmit={e => {
                e.preventDefault();

                props.onCreateTechnician(
                  techName,
                  techEmail,
                  techSkills
                );

                setTechName("");
                setTechEmail("");
                setTechSkills("Network, Hardware");
                setShowTech(false);
              }}
            >

              <label>Full name</label>
              <input
                value={techName}
                onChange={e => setTechName(e.target.value)}
                required
                placeholder="Technician name"
              />

              <label>Email address</label>
              <input
                type="email"
                value={techEmail}
                onChange={e => setTechEmail(e.target.value)}
                required
                placeholder="name@company.com"
              />

              <label>Skills (comma separated)</label>
              <input
                value={techSkills}
                onChange={e => setTechSkills(e.target.value)}
                placeholder="Network, Hardware"
              />

              <div className="form-actions">

                <button
                  type="button"
                  className="secondary"
                  onClick={() => setShowTech(false)}
                >
                  Cancel
                </button>

                <button className="primary" type="submit">
                  Create technician
                </button>

              </div>

            </form>
          </section>
        </div>
      )}

    </div>
  );
}
