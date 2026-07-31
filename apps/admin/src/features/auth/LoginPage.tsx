import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../app/auth/AuthContext';
import { environment, type AppEnvironment } from '../../app/config/environment';

export function LoginPage() {
  const { admin, signIn, login, switchEnvironment } = useAuth(); const location = useLocation();
  if (admin) return <Navigate to={(location.state as { from?: string } | null)?.from ?? '/dashboard'} replace />;
  return <main className="centered-page"><section className="card auth-card"><span className="eyebrow">Secure administration</span><h1>Admin sign in</h1><p>Sign in with an authorised content administration account.</p>{environment.environmentSwitcherEnabled&&<label className="environment-selector"><span>Backend environment</span><select aria-label="Backend environment" value={environment.appEnvironment} onChange={(event)=>{const next=event.target.value as AppEnvironment;if(window.confirm('Changing backend environment will clear authentication and reload the Admin Portal. Continue?'))void switchEnvironment(next);}}><option value="LOCAL">Local</option><option value="HOSTED">Hosted</option></select>{environment.warning&&<small>{environment.warning}</small>}</label>}{environment.developmentAuthEnabled ? <div className="actions"><button type="button" onClick={()=>signIn('administrator')}>Continue as development administrator</button><button type="button" onClick={()=>signIn('reviewer')}>Continue as content reviewer</button></div> : <div className="actions"><button type="button" onClick={()=>void login()}>Continue with Svea Study identity</button></div>}</section></main>;
}
