import { useState } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../app/auth/AuthContext';
import { AdminIcon } from './AdminIcon';

const links = [
  ['/dashboard', 'Dashboard', 'dashboard'], ['/exam-structure', 'Exam Structure', 'exams'], ['/sources', 'Sources', 'source'], ['/knowledge', 'Knowledge Base', 'knowledge'], ['/questions', 'Questions', 'content'], ['/reviews', 'Review Queue', 'review'], ['/releases', 'Releases', 'releases'], ['/reports', 'Reports', 'reports'], ['/audit', 'Audit Log', 'audit'], ['/help/content-system', 'How the Content System Works', 'help'],
] as const;

export function AdminLayout() {
  const { admin, signOut } = useAuth(); const [open, setOpen] = useState(false);
  const initials = admin?.displayName.split(/\s+/).slice(0, 2).map((part) => part[0]).join('').toUpperCase() || 'A';
  return <div className={`app-shell ${open ? 'nav-open' : ''}`}>
    <a className="skip-link" href="#main-content">Skip to content</a>
    <aside className="sidebar">
      <header className="brand"><span className="brand-mark" aria-hidden="true">S</span><span><strong>Svea Study</strong><small>Admin Console</small></span><button className="mobile-close icon-button" type="button" aria-label="Close navigation" onClick={() => setOpen(false)}><AdminIcon name="close" /></button></header>
      <nav aria-label="Admin sections">{links.map(([to, label, icon]) => <NavLink key={to} to={to} onClick={() => setOpen(false)} className={({ isActive }) => isActive ? 'active' : undefined}><AdminIcon name={icon} /><span>{label}</span></NavLink>)}</nav>
      <section className="identity-summary" aria-label="Signed-in administrator"><span className="identity-avatar" aria-hidden="true">{initials}</span><span className="identity-copy"><strong>{admin?.displayName}</strong><small>{admin?.roles.join(', ').replaceAll('_', ' ')}</small></span><button className="icon-button" type="button" onClick={signOut} aria-label="Sign out"><AdminIcon name="signout" /></button></section>
    </aside>
    <div className="workspace">
      <header className="topbar"><button className="mobile-menu icon-button" type="button" aria-label="Open navigation" aria-expanded={open} onClick={() => setOpen(true)}><AdminIcon name="menu" /></button><div className="top-search"><AdminIcon name="search"/><span>Content administration</span></div><div className="topbar-title"><AdminIcon name="user"/><span>{admin?.displayName}</span></div></header>
      <main id="main-content" tabIndex={-1}><Outlet /></main>
    </div>
    {open && <button className="nav-scrim" aria-label="Close navigation" onClick={() => setOpen(false)} />}
  </div>;
}
