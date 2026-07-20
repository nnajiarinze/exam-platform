import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../app/auth/AuthContext';
import { environment } from '../../app/config/environment';

export function LoginPage() {
  const { admin, signIn } = useAuth(); const location = useLocation();
  if (admin) return <Navigate to={(location.state as { from?: string } | null)?.from ?? '/dashboard'} replace />;
  return <main className="centered-page"><section className="card auth-card"><span className="eyebrow">Development access</span><h1>Admin sign in</h1><p>Production identity is not configured. Local access uses the development identity supplied through environment configuration.</p>{environment.developmentAuthEnabled ? <button type="button" onClick={signIn}>Continue as development administrator</button> : <p role="alert" className="error">Development authentication is disabled. Configure a production identity provider before access can be granted.</p>}</section></main>;
}
