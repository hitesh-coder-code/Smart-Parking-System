import { createContext, useContext, useState, ReactNode } from 'react';
import api from '../api/client';

export interface AuthUser {
  userId: string;
  name: string;
  email: string;
  role: string | null;
  orgId: string | null;
}

interface AuthContextValue {
  user: AuthUser | null;
  login: (email: string, password: string) => Promise<AuthUser>;
  logout: () => void;
  refreshUser: () => Promise<AuthUser>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(() => {
    const stored = localStorage.getItem('user');
    return stored ? JSON.parse(stored) : null;
  });

  async function login(email: string, password: string): Promise<AuthUser> {
    const res = await api.post('/api/auth/login', { email, password });
    const { token, ...userData } = res.data;
    localStorage.setItem('token', token);
    localStorage.setItem('user', JSON.stringify(userData));
    setUser(userData);
    return userData;
  }

  function logout() {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    setUser(null);
  }

  /** Re-fetches the current profile — call after joining/creating an organization. */
  async function refreshUser(): Promise<AuthUser> {
    const res = await api.get('/api/users/me');
    const userData: AuthUser = {
      userId: res.data.id,
      name: res.data.name,
      email: res.data.email,
      role: res.data.role,
      orgId: res.data.orgId,
    };
    localStorage.setItem('user', JSON.stringify(userData));
    setUser(userData);
    return userData;
  }

  return (
    <AuthContext.Provider value={{ user, login, logout, refreshUser }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
