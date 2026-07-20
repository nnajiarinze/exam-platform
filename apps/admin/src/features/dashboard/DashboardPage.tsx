import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { useAuth } from '../../app/auth/AuthContext';
import { permissionsFor } from '../../app/permissions/permissions';
import { fetchContentServiceStatus } from '../../api/client/contentService';
import { ApiError } from '../../api/errors/ApiError';
import { AdminIcon } from '../../components/AdminIcon';
import { MetricCard, PageHeader, StatusBadge } from '../../components/AdminUi';

function ConnectivityStatus({ admin }: { admin: NonNullable<ReturnType<typeof useAuth>['admin']> }) {
  const query = useQuery({ queryKey: ['content-service', 'status', admin.id, admin.roles], queryFn: ({ signal }) => fetchContentServiceStatus(signal) });
  if (query.isPending) return <MetricCard label="Content Service" value="Checking…" tone="neutral" note="Verifying service availability" />;
  if (query.isSuccess) return <MetricCard label="Content Service" value={<StatusBadge value="Connected" />} tone="green" note={`${query.data.service} is ${query.data.status.toLowerCase()}.`} />;
  const error = query.error instanceof ApiError ? query.error : new ApiError('SERVER', 'Content Service status is unavailable.');
  const label = error.kind === 'AUTHENTICATION' ? 'Authentication required' : error.kind === 'FORBIDDEN' ? 'Access forbidden' : error.kind === 'CONFIGURATION' ? 'Misconfigured' : 'Unavailable';
  return <div role="alert"><MetricCard label="Content Service" value={<StatusBadge value={label} />} tone="neutral" note={error.message} /></div>;
}

export function DashboardPage() {
  const { admin } = useAuth(); if (!admin) return null; const permissions = permissionsFor(admin);
  return <><PageHeader eyebrow="Operational overview" title="Dashboard" description="Manage Swedish citizenship content, governance, releases, and operational health." />
    <section className="report-grid" aria-label="Administration overview"><ConnectivityStatus admin={admin} /><MetricCard label="Assigned roles" value={admin.roles.length} tone="blue" note="Current signed-in identity" /><MetricCard label="Effective permissions" value={permissions.length} tone="yellow" note="Calculated from active roles" /></section>
    <div className="dashboard-grid">
      <section className="card"><h2>Current administrator</h2><dl><div><dt>Name</dt><dd>{admin.displayName}</dd></div><div><dt>Identifier</dt><dd>{admin.id}</dd></div></dl><h3>Roles</h3><ul className="tag-list">{admin.roles.map((role) => <li key={role}>{role}</li>)}</ul></section>
      <section className="card"><h2>Operational areas</h2><div className="dashboard-links"><Link className="dashboard-link" to="/questions"><AdminIcon name="content"/><span><strong>Question bank</strong><small>Create and maintain assessment content</small></span><AdminIcon name="arrow"/></Link><Link className="dashboard-link" to="/reviews"><AdminIcon name="review"/><span><strong>Review queue</strong><small>Review submitted facts and questions</small></span><AdminIcon name="arrow"/></Link><Link className="dashboard-link" to="/releases"><AdminIcon name="releases"/><span><strong>Releases</strong><small>Validate, publish, deliver, and activate</small></span><AdminIcon name="arrow"/></Link></div></section>
      <section className="card span-two"><div className="section-heading"><div><h2>Effective permissions</h2><p>Permissions are derived from the authenticated administrator roles.</p></div><Link to="/help/content-system">Understand the content workflow</Link></div><ul className="tag-list">{permissions.map((permission) => <li key={permission}>{permission}</li>)}</ul></section>
    </div></>;
}
