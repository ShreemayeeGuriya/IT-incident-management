import { Outlet } from "react-router-dom";
import TopBar from "./TopBar";
import Sidebar from "./Sidebar";

export default function AppShell() {
  return (
    <div className="shell">
      <TopBar />
      <div className="body">
        <Sidebar />
        <main className="content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}