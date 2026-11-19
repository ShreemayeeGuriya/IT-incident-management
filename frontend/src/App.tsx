import { Routes, Route } from "react-router-dom";
import ProtectedRoute from "./routes/ProtectedRoute";
import AppShell from "./components/AppShell";
import LoginPage from "./pages/LoginPage";
import RegisterPage from "./pages/RegisterPage";

const Soon = ({ name }: { name: string }) => (
  <div style={{ padding: 24 }}>
    <h2>{name}</h2>
    <p style={{ color: "var(--muted)" }}>Coming in a later step.</p>
  </div>
);

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />

      <Route
        element={
          <ProtectedRoute>
            <AppShell />
          </ProtectedRoute>
        }
      >
        <Route index element={<Soon name="Dashboard" />} />
        <Route path="incidents" element={<Soon name="Incidents" />} />
        <Route path="incidents/new" element={<Soon name="New Incident" />} />
        <Route path="incidents/:id" element={<Soon name="Incident Detail" />} />
        <Route path="notifications" element={<Soon name="Notifications" />} />
        <Route path="profile" element={<Soon name="Profile" />} />
        <Route
          path="workload"
          element={
            <ProtectedRoute roles={["TECHNICIAN", "ADMIN"]}>
              <Soon name="Workload" />
            </ProtectedRoute>
          }
        />
        <Route
          path="admin/users"
          element={
            <ProtectedRoute roles={["ADMIN"]}>
              <Soon name="Users" />
            </ProtectedRoute>
          }
        />
        <Route
          path="admin/technicians"
          element={
            <ProtectedRoute roles={["ADMIN"]}>
              <Soon name="Technicians & Skills" />
            </ProtectedRoute>
          }
        />
        <Route
          path="admin/strategies"
          element={
            <ProtectedRoute roles={["ADMIN"]}>
              <Soon name="Strategies" />
            </ProtectedRoute>
          }
        />
      </Route>
    </Routes>
  );
}