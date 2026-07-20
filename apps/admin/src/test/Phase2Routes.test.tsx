import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from '../app/auth/AuthContext';
import { AppRouter } from '../app/router/AppRouter';
import { AdminRole } from '../app/permissions/permissions';
import { adminSessionStorageKey } from '../app/auth/authSession';

function renderRoute(path: string) {
  sessionStorage.setItem(adminSessionStorageKey, JSON.stringify({
    id: 'phase-2-admin', displayName: 'Phase 2 Admin', roles: [AdminRole.Admin],
  }));
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}><AuthProvider><AppRouter /></AuthProvider></MemoryRouter>
    </QueryClientProvider>,
  );
}

function json(body: object) {
  return new Response(JSON.stringify(body), { status: 200, headers: { 'content-type': 'application/json' } });
}

describe('Phase 2 routes', () => {
  it('loads the empty exam list through the generated client', async () => {
    const fetchMock = vi.fn().mockResolvedValue(json({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 }));
    vi.stubGlobal('fetch', fetchMock);
    renderRoute('/exam-structure');
    expect(screen.getByRole('heading', { name: 'Exam Structure' })).toBeInTheDocument();
    expect(await screen.findByText('No exams match these filters.')).toBeInTheDocument();
    expect((fetchMock.mock.calls[0][0] as Request).url).toContain('/api/v1/admin/exams');
  });

  it('loads the empty source list through the generated client', async () => {
    const fetchMock = vi.fn().mockResolvedValue(json({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 }));
    vi.stubGlobal('fetch', fetchMock);
    renderRoute('/sources');
    expect(screen.getByRole('heading', { name: 'Sources' })).toBeInTheDocument();
    expect(await screen.findByText('No sources match these filters.')).toBeInTheDocument();
    expect((fetchMock.mock.calls[0][0] as Request).url).toContain('/api/v1/admin/sources');
  });
});
