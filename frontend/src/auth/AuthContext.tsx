import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { authApi, usersApi } from "../api/endpoints";
import type { Me, Technician, User } from "../types/incident";
import { normalizeRole } from "../utils/format";

interface AuthContextValue {
  user: Me | null;
  techProfile: Technician | null; // only set for technicians
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (name: string, email: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue>(null!);
export const useAuth = () => useContext(AuthContext);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Me | null>(null);
  const [techProfile, setTechProfile] = useState<Technician | null>(null);
  const [loading, setLoading] = useState(true);

  async function hydrate(u: User) {
    const me: Me = { ...u, role: normalizeRole(u.role) };
    setUser(me);
    if (me.role === "TECHNICIAN") {
      // Technician APIs need the profile ID, which is different from the user ID
      const list = await usersApi.assignees().catch(() => [] as Technician[]);
      setTechProfile(list.find((t) => t.userId === me.id) ?? null);
    } else {
      setTechProfile(null);
    }
  }

  // On page load: if a token exists, restore the session
  useEffect(() => {
    if (!localStorage.getItem("token")) {
      setLoading(false);
      return;
    }
    authApi
      .me()
      .then(hydrate)
      .catch(() => localStorage.removeItem("token"))
      .finally(() => setLoading(false));
  }, []);

  const login = async (email: string, password: string) => {
    const r = await authApi.login({ email, password });
    localStorage.setItem("token", r.token);
    await hydrate(r.user);
  };

  const register = async (name: string, email: string, password: string) => {
    const r = await authApi.register({ name, email, password });
    localStorage.setItem("token", r.token);
    await hydrate(r.user);
  };

  const logout = () => {
    localStorage.removeItem("token");
    setUser(null);
    setTechProfile(null);
  };

  return (
    <AuthContext.Provider value={{ user, techProfile, loading, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
}