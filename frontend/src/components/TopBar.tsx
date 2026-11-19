export default function TopBar() {
  return (
    <header
      style={{
        background: "var(--bar)",
        color: "var(--bar-text)",
        height: 36,
        display: "flex",
        alignItems: "center",
        padding: "0 16px",
        gap: 24,
        fontWeight: 600,
      }}
    >
      <span>Radar</span>
    </header>
  );
}