import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../app/auth/AuthContext';
import { environment } from '../../app/config/environment';

export function LoginPage() {
  const { admin, signIn, login } = useAuth(); const location = useLocation();
  if (admin) return <Navigate to={(location.state as { from?: string } | null)?.from ?? '/dashboard'} replace />;
  return <main className="centered-page"><section className="card auth-card"><span className="eyebrow">Secure administration</span><h1>Admin sign in</h1><p>Sign in with an authorised content administration account.</p>{environment.developmentAuthEnabled ? <div className="actions"><button type="button" onClick={()=>signIn('administrator')}>Continue as development administrator</button><button type="button" onClick={()=>signIn('reviewer')}>Continue as content reviewer</button></div> : <div className="actions"><button type="button" onClick={()=>void login()}>Continue with Svea Study identity</button></div>}</section></main>;
}
