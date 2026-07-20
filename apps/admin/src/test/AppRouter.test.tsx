import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from '../app/auth/AuthContext';
import { AppRouter } from '../app/router/AppRouter';
import { AdminRole, type AdminIdentity } from '../app/permissions/permissions';
import { adminSessionStorageKey } from '../app/auth/authSession';

function renderRouter(path: string, identity?: AdminIdentity) {
  if (identity) sessionStorage.setItem(adminSessionStorageKey, JSON.stringify(identity));
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={queryClient}><MemoryRouter initialEntries={[path]}><AuthProvider><AppRouter /></AuthProvider></MemoryRouter></QueryClientProvider>);
}

function statusResponse(status = 200, body: object = { service: 'content-service', status: 'READY', timestamp: '2026-07-20T08:00:00Z' }) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

describe('admin routing', () => {
  it('blocks unauthenticated admin routes', () => {
    renderRouter('/dashboard');
    expect(screen.getByRole('heading', { name: 'Admin sign in' })).toBeInTheDocument();
  });

  it('signs in with the separate development reviewer identity', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(statusResponse()));
    renderRouter('/login');
    await userEvent.click(screen.getByRole('button', { name: 'Continue as content reviewer' }));
    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument();
    expect(screen.getAllByText('test-reviewer').length).toBeGreaterThan(0);
    expect(screen.getByText(AdminRole.ContentReviewer)).toBeInTheDocument();
  });

  it('shows unauthorized for an authenticated identity without admin permissions', () => {
    renderRouter('/dashboard', { id: 'restricted', displayName: 'Restricted User', roles: [] });
    expect(screen.getByRole('heading', { name: 'Access denied' })).toBeInTheDocument();
    expect(screen.getByText(/Restricted User/)).toBeInTheDocument();
  });

  it('renders authorized identity, navigation, roles, permissions, and connected service status', async () => {
    const fetchMock = vi.fn().mockResolvedValue(statusResponse());
    vi.stubGlobal('fetch', fetchMock);
    renderRouter('/dashboard', { id: 'author-1', displayName: 'Content Author', roles: [AdminRole.ContentAuthor] });
    expect(screen.getByRole('heading', { name: 'Dashboard' })).toBeInTheDocument();
    expect(screen.getAllByText('Content Author').length).toBeGreaterThan(0);
    expect(screen.getByText(AdminRole.ContentAuthor)).toBeInTheDocument();
    expect(screen.getByText('CREATE_DRAFT_CONTENT')).toBeInTheDocument();
    expect(await screen.findByText(/Connected/)).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Admin sections' })).toBeInTheDocument();
    const request = fetchMock.mock.calls[0][0] as Request;
    expect(request.url).toBe('http://content.test/api/v1/status');
    expect(request.headers.get('X-Admin-Identity')).toBe('author-1');
    expect(request.headers.get('X-Admin-Roles')).toBe('CONTENT_AUTHOR');
  });

  it.each([
    [401, 'AUTHENTICATION_REQUIRED', 'Authentication required'],
    [403, 'FORBIDDEN', 'Access forbidden'],
  ])('displays %s contract errors safely', async (status, code, label) => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(statusResponse(status, { code, message: 'Safe backend message', timestamp: '2026-07-20T08:00:00Z', errors: [] })));
    renderRouter('/dashboard', { id: 'admin', displayName: 'Admin', roles: [AdminRole.Admin] });
    expect(await screen.findByRole('alert')).toHaveTextContent(label);
    expect(screen.getByText('Safe backend message')).toBeInTheDocument();
  });

  it('displays network unavailability without crashing', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('connection refused')));
    renderRouter('/dashboard', { id: 'admin', displayName: 'Admin', roles: [AdminRole.Admin] });
    expect(await screen.findByRole('alert')).toHaveTextContent('Unavailable');
    expect(screen.getByText('The Content Service could not be reached.')).toBeInTheDocument();
  });

  it('navigates to an explicit feature placeholder', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(statusResponse()));
    renderRouter('/dashboard', { id: 'admin', displayName: 'Admin', roles: [AdminRole.Admin] });
    await userEvent.click(screen.getByRole('link', { name: 'Review Queue' }));
    expect(screen.getByRole('heading', { name: 'Review Queue' })).toBeInTheDocument();
    expect(screen.getByText('Not implemented in Admin Phase 1.')).toBeInTheDocument();
  });

  it('renders the not-found page safely', () => {
    renderRouter('/does-not-exist');
    expect(screen.getByRole('heading', { name: 'Route not found' })).toBeInTheDocument();
  });
});
