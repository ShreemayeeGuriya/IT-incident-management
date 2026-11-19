import TopBar from "./components/TopBar";
import Sidebar from "./components/Sidebar";

export default function App() {
  return (
    <div style={{ display: "flex", flexDirection: "column", height: "100vh" }}>
      <TopBar />
      <div style={{ display: "flex", flex: 1, minHeight: 0 }}>
        <Sidebar />
        <main style={{ flex: 1, padding: 16 }}>
          <h2>Content area</h2>
        </main>
      </div>
    </div>
  );
}