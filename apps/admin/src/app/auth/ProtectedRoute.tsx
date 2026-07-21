import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { Permission, can } from '../permissions/permissions';
import { useAuth } from './AuthContext';

export function ProtectedRoute() {
  const { admin,loading } = useAuth(); const location = useLocation();
  if(loading)return <main className="centered-page"><section className="card auth-card" role="status"><h1>Restoring secure session…</h1></section></main>;
  if (!admin) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (!can(admin, Permission.ViewContent)) return <Navigate to="/unauthorized" replace />;
  return <Outlet />;
}
