import { Link } from 'react-router-dom';
export function NotFoundPage() { return <main className="centered-page"><section className="card auth-card"><h1>Route not found</h1><p>The requested admin page does not exist.</p><Link to="/dashboard">Go to dashboard</Link></section></main>; }
