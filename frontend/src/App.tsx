import { Navigate, Route, Routes, Link, useLocation } from 'react-router-dom';
import { ReactNode } from 'react';
import { useAuth } from './context/AuthContext';
import Login from './pages/Login';
import Signup from './pages/Signup';
import VerifyEmail from './pages/VerifyEmail';
import OrgSelect from './pages/OrgSelect';
import Dashboard from './pages/Dashboard';
import Slots from './pages/Slots';
import Events from './pages/Events';
import Swaps from './pages/Swaps';
import Admin from './pages/Admin';

function ProtectedRoute({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const location = useLocation();
  if (!user) return <Navigate to="/login" state={{ from: location }} replace />;
  return <>{children}</>;
}

/** Like ProtectedRoute, but also requires the user to have joined/created an organization. */
function RequireOrg({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const location = useLocation();
  if (!user) return <Navigate to="/login" state={{ from: location }} replace />;
  if (!user.orgId) return <Navigate to="/org-select" replace />;
  return <>{children}</>;
}

function NavBar() {
  const { user, logout } = useAuth();
  if (!user) return null;
  return (
    <nav className="navbar">
      <span className="brand">Parking Reallocation</span>
      {user.orgId && (
        <>
          <Link to="/dashboard">Dashboard</Link>
          <Link to="/slots">Slots</Link>
          <Link to="/events">My Events</Link>
          <Link to="/swaps">Swaps</Link>
          {user.role === 'ADMIN' && <Link to="/admin">Admin</Link>}
        </>
      )}
      <span className="spacer" />
      <span className="user-chip">{user.name} · {user.role ?? 'no org yet'}</span>
      <button onClick={logout}>Log out</button>
    </nav>
  );
}

export default function App() {
  return (
    <>
      <NavBar />
      <main className="page">
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/signup" element={<Signup />} />
          <Route path="/verify-email" element={<VerifyEmail />} />
          <Route path="/org-select" element={<ProtectedRoute><OrgSelect /></ProtectedRoute>} />
          <Route path="/dashboard" element={<RequireOrg><Dashboard /></RequireOrg>} />
          <Route path="/slots" element={<RequireOrg><Slots /></RequireOrg>} />
          <Route path="/events" element={<RequireOrg><Events /></RequireOrg>} />
          <Route path="/swaps" element={<RequireOrg><Swaps /></RequireOrg>} />
          <Route path="/admin" element={<RequireOrg><Admin /></RequireOrg>} />
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </main>
    </>
  );
}
