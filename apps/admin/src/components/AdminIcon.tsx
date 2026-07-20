const glyphs: Record<string, string> = {
  dashboard: '▦', content: '▤', exams: '▣', review: '▱', releases: '◇', reports: '▥', audit: '≡', help: '?', search: '⌕', signout: '↪', user: '●', plus: '+', status: '●', retry: '↻', menu: '☰', close: '×', arrow: '›', check: '✓', warning: '!', info: 'i', edit: '✎', source: '⌁', knowledge: '◎', question: '?', settings: '⚙',
};

export function AdminIcon({ name, label }: { name: string; label?: string }) {
  return <span className="admin-icon" aria-hidden={label ? undefined : true} aria-label={label}>{glyphs[name] ?? name}</span>;
}
