import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../../app/auth/AuthContext';
import { permissionsFor } from '../../app/permissions/permissions';
import { fetchContentServiceStatus } from '../../api/client/contentService';
import { ApiError } from '../../api/errors/ApiError';

function ConnectivityStatus({ admin }: { admin: NonNullable<ReturnType<typeof useAuth>['admin']> }) {
  const query = useQuery({ queryKey: ['content-service', 'status', admin.id, admin.roles], queryFn: ({ signal }) => fetchContentServiceStatus(signal) });
  if (query.isPending) return <p className="status" role="status">Checking Content Service…</p>;
  if (query.isSuccess) return <><p className="status connected" role="status"><span aria-hidden="true">●</span> Connected</p><p>{query.data.service} is {query.data.status.toLowerCase()}.</p></>;
  const error = query.error instanceof ApiError ? query.error : new ApiError('SERVER', 'Content Service status is unavailable.');
  const label = error.kind === 'AUTHENTICATION' ? 'Authentication required' : error.kind === 'FORBIDDEN' ? 'Access forbidden' : error.kind === 'CONFIGURATION' ? 'Misconfigured' : 'Unavailable';
  return <><p className="status error" role="alert"><span aria-hidden="true">●</span> {label}</p><p>{error.message}</p></>;
}

export function DashboardPage() {
  const { admin } = useAuth(); if (!admin) return null;
  return <><header className="page-header"><span className="eyebrow">Admin Phase 1</span><h1>Dashboard</h1><p>The technical foundation is ready for future editorial workflows.</p></header>
    <div className="dashboard-grid">
      <section className="card"><h2>Current administrator</h2><dl><div><dt>Name</dt><dd>{admin.displayName}</dd></div><div><dt>Identifier</dt><dd>{admin.id}</dd></div></dl></section>
      <section className="card"><h2>Content Service</h2><ConnectivityStatus admin={admin} /></section>
      <section className="card span-two"><h2>Assigned roles</h2><ul className="tag-list">{admin.roles.map((role) => <li key={role}>{role}</li>)}</ul></section>
      <section className="card span-two"><h2>Effective permissions</h2><ul className="tag-list">{permissionsFor(admin).map((permission) => <li key={permission}>{permission}</li>)}</ul></section>
      <section className="card span-two"><h2>Future operational sections</h2><p>Editorial content, review, release, reporting, and audit functions will be implemented in later admin phases.</p></section>
    </div></>;
}
