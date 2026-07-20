import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../app/auth/AuthContext';
import { environment } from '../../app/config/environment';

export function LoginPage() {
  const { admin, signIn } = useAuth(); const location = useLocation();
  if (admin) return <Navigate to={(location.state as { from?: string } | null)?.from ?? '/dashboard'} replace />;
  return <main className="centered-page"><section className="card auth-card"><span className="eyebrow">Development access</span><h1>Admin sign in</h1><p>Choose a local development identity. Use the reviewer identity to review content created by the administrator identity.</p>{environment.developmentAuthEnabled ? <div className="actions"><button type="button" onClick={()=>signIn('administrator')}>Continue as development administrator</button><button type="button" onClick={()=>signIn('reviewer')}>Continue as content reviewer</button></div> : <p role="alert" className="error">Development authentication is disabled. Configure a production identity provider before access can be granted.</p>}</section></main>;
}
