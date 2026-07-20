import { Link } from 'react-router-dom';
import { useAuth } from '../../app/auth/AuthContext';
export function UnauthorizedPage() { const { admin, signOut } = useAuth(); return <main className="centered-page"><section className="card auth-card"><h1>Access denied</h1><p>You do not have permission to access content administration.</p>{admin && <p>Signed in as <strong>{admin.displayName}</strong>.</p>}<div className="actions"><Link to="/dashboard">Return to dashboard</Link>{admin && <button type="button" onClick={signOut}>Sign out</button>}</div></section></main>; }
