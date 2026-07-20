import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { Permission, can } from '../permissions/permissions';
import { useAuth } from './AuthContext';

export function ProtectedRoute() {
  const { admin } = useAuth(); const location = useLocation();
  if (!admin) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (!can(admin, Permission.ViewContent)) return <Navigate to="/unauthorized" replace />;
  return <Outlet />;
}
