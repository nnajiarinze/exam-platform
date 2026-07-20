import type { ReactNode } from 'react';
import { AdminIcon } from './AdminIcon';

export function PageHeader({ eyebrow, title, description, actions }: { eyebrow?: string; title: string; description?: string; actions?: ReactNode }) {
  return <header className="page-header"><div>{eyebrow && <span className="eyebrow">{eyebrow}</span>}<h1>{title}</h1>{description && <p>{description}</p>}</div>{actions && <div className="page-actions">{actions}</div>}</header>;
}

export function StatusBadge({ value }: { value: string }) {
  const key = value.toLowerCase().replaceAll('_', '-');
  return <span className={`status-badge status-${key}`}>{value.replaceAll('_', ' ')}</span>;
}

export function MetricCard({ label, value, tone = 'blue', note }: { label: string; value: ReactNode; tone?: 'blue' | 'yellow' | 'green' | 'neutral'; note?: ReactNode }) {
  return <article className={`metric-card metric-${tone}`}><span>{label}</span><strong>{value}</strong>{note && <small>{note}</small>}</article>;
}

export function EmptyState({ title, description }: { title: string; description?: string }) {
  return <div className="empty-state" role="status"><AdminIcon name="info" /><strong>{title}</strong>{description && <p>{description}</p>}</div>;
}

export function TableFrame({ children }: { children: ReactNode }) { return <div className="table-frame"><div className="table-scroll">{children}</div></div>; }
