import { Link, useLocation } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import type { Role } from "../types/incident";

type Item = { to: string; label: string };
type Section = { title: string; items: Item[] };

const NAV: Record<Role, Section[]> = {
  EMPLOYEE: [
    { title: "Radar", items: [
      { to: "/", label: "Today" },
      { to: "/notifications", label: "Notifications" },
    ]},
    { title: "Incidents", items: [
      { to: "/incidents", label: "My Incidents" },
      { to: "/incidents?status=CLOSED", label: "Closed" },
    ]},
  ],
  TECHNICIAN: [
    { title: "Radar", items: [
      { to: "/", label: "Today" },
      { to: "/notifications", label: "Notifications" },
    ]},
    { title: "Incidents", items: [
      { to: "/incidents?mine=1", label: "My Assigned" },
      { to: "/incidents?status=PRIORITIZED", label: "Unassigned Queue" },
      { to: "/incidents", label: "All Incidents" },
    ]},
    { title: "Team", items: [{ to: "/workload", label: "Workload" }] },
  ],
  ADMIN: [
    { title: "Radar", items: [
      { to: "/", label: "Today" },
      { to: "/notifications", label: "Notifications" },
    ]},
    { title: "Incidents", items: [{ to: "/incidents", label: "All Incidents" }] },
    { title: "Administration", items: [
      { to: "/admin/users", label: "Users" },
      { to: "/admin/technicians", label: "Technicians & Skills" },
      { to: "/admin/strategies", label: "Strategies" },
      { to: "/workload", label: "Workload" },
    ]},
  ],
};

export default function Sidebar() {
  const { user } = useAuth();
  const loc = useLocation();

  // Links with a query string (?status=...) must match exactly,
  // otherwise every "Incidents" link would highlight at once
  const isActive = (to: string) => {
    if (to.includes("?")) return loc.pathname + loc.search === to;
    if (loc.search) return false;
    if (to === "/incidents") {
      return (
        loc.pathname === "/incidents" ||
        (loc.pathname.startsWith("/incidents/") && loc.pathname !== "/incidents/new")
      );
    }
    return loc.pathname === to;
  };

  return (
    <nav className="sidebar">
      <Link to="/incidents/new" className="new-btn">＋ New Incident</Link>
      {NAV[user!.role].map((sec) => (
        <div key={sec.title}>
          <h4>{sec.title}</h4>
          {sec.items.map((i) => (
            <Link key={i.label} to={i.to} className={isActive(i.to) ? "active" : ""}>
              {i.label}
            </Link>
          ))}
        </div>
      ))}
      <h4>Account</h4>
      <Link to="/profile" className={loc.pathname === "/profile" ? "active" : ""}>
        Profile
      </Link>
    </nav>
  );
}