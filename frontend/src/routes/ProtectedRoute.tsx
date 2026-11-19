import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import type { Role } from "../types/user";

interface Props {
  // If given, only these roles may see the page
  roles?: Role[];
}

export default function ProtectedRoute({ roles }: Props) {
  const { user, loading } = useAuth();

  if (loading) {
    return <div style={{ padding: 24 }}>Loading...</div>;
  }
  if (!user) {
    return <Navigate to="/login" replace />;
  }
  if (roles && !roles.includes(user.role)) {
    return <div style={{ padding: 24 }}>You do not have permission to view this page.</div>;
  }
  return <Outlet />;
}