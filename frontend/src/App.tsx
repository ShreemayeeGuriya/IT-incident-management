import { Routes, Route, Navigate } from "react-router-dom";
import ProtectedRoute from "./routes/ProtectedRoute";
import AppShell from "./components/AppShell";
import LoginPage from "./pages/LoginPage";
import RegisterPage from "./pages/RegisterPage";
import DashboardPage from "./pages/DashboardPage";
import IncidentsPage from "./pages/IncidentsPage";
import NewIncidentPage from "./pages/NewIncidentPage";
import IncidentDetailPage from "./pages/IncidentDetailPage";
import NotificationsPage from "./pages/NotificationsPage";
import ProfilePage from "./pages/ProfilePage";
import WorkloadPage from "./pages/WorkloadPage";
import UsersPage from "./pages/UsersPage";
import TechniciansPage from "./pages/TechniciansPage";
import StrategiesPage from "./pages/StrategiesPage";
import ReferenceDataPage from "./pages/ReferenceDataPage";

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />

      <Route
        element={
          <ProtectedRoute>
            <AppShell />
          </ProtectedRoute>
        }
      >
        <Route index element={<DashboardPage />} />
        <Route path="incidents" element={<IncidentsPage />} />
        <Route path="incidents/new" element={<NewIncidentPage />} />
        <Route path="incidents/:id" element={<IncidentDetailPage />} />
        <Route path="notifications" element={<NotificationsPage />} />
        <Route path="profile" element={<ProfilePage />} />
        <Route
          path="workload"
          element={
            <ProtectedRoute roles={["TECHNICIAN", "ADMIN"]}>
              <WorkloadPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="admin/users"
          element={
            <ProtectedRoute roles={["ADMIN"]}>
              <UsersPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="admin/reference-data"
          element={
            <ProtectedRoute roles={["ADMIN"]}>
              <ReferenceDataPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="admin/technicians"
          element={
            <ProtectedRoute roles={["ADMIN"]}>
              <TechniciansPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="admin/strategies"
          element={
            <ProtectedRoute roles={["ADMIN"]}>
              <StrategiesPage />
            </ProtectedRoute>
          }
        />
      </Route>
    </Routes>
  );
}
