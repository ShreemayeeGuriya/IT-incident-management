const items = [
  { label: "Dashboard" },
  { label: "My Reports" },
  { label: "Notifications" },
  { label: "Profile" },
];

export default function Sidebar() {
  return (
    <nav
      style={{
        width: 220,
        background: "var(--sidebar-bg)",
        borderRight: "1px solid var(--border)",
        padding: "12px 8px",
        display: "flex",
        flexDirection: "column",
        gap: 2,
      }}
    >
      {items.map((item) => (
        <div key={item.label} style={{ padding: "6px 10px", borderRadius: 6 }}>
          {item.label}
        </div>
      ))}
    </nav>
  );
}