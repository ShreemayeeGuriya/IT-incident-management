import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { notificationsApi } from "../api/endpoints";

export default function TopBar() {
  const { user, logout } = useAuth();
  const nav = useNavigate();
  const [unread, setUnread] = useState(0);
  const [q, setQ] = useState("");

  // Poll the unread count every 30 seconds
  useEffect(() => {
    const load = () =>
      notificationsApi.unreadCount().then((r) => setUnread(r.unreadCount)).catch(() => {});
    load();
    const t = setInterval(load, 30000);
    return () => clearInterval(t);
  }, []);

  return (
    <header className="topbar">
      <strong>Radar · Incidents</strong>
      <input
        className="search"
        placeholder="Search incidents"
        value={q}
        onChange={(e) => setQ(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === "Enter") nav(`/incidents?search=${encodeURIComponent(q)}`);
        }}
      />
      <span style={{ flex: 1 }} />
      <button className="bell" onClick={() => nav("/notifications")}>
        🔔{unread > 0 && <b className="badge">{unread}</b>}
      </button>
      <button className="link" onClick={() => nav("/profile")}>
        {user?.name} ({user?.role})
      </button>
      <button
        className="link"
        onClick={() => {
          logout();
          nav("/login");
        }}
      >
        Sign out
      </button>
    </header>
  );
}