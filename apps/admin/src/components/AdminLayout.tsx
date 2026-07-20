import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../app/auth/AuthContext';

const links = [['/dashboard', 'Dashboard'], ['/exam-structure', 'Exam Structure'], ['/sources', 'Sources'], ['/knowledge', 'Knowledge Base'], ['/questions', 'Questions'], ['/reviews', 'Review Queue'], ['/releases', 'Releases'], ['/reports', 'Reports'], ['/audit', 'Audit Log']] as const;
export function AdminLayout() {
  const { admin, signOut } = useAuth();
  return <div className="app-shell">
    <aside className="sidebar">
      <header><span className="eyebrow">Exam platform</span><strong>Content administration</strong></header>
      <nav aria-label="Admin sections">{links.map(([to, label]) => <NavLink key={to} to={to} className={({ isActive }) => isActive ? 'active' : undefined}>{label}</NavLink>)}</nav>
      <section className="identity-summary" aria-label="Signed-in administrator"><strong>{admin?.displayName}</strong><span>{admin?.id}</span><button type="button" onClick={signOut}>Sign out</button></section>
    </aside>
    <main id="main-content" tabIndex={-1}><Outlet /></main>
  </div>;
}
