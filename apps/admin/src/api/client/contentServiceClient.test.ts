import { getContentServiceStatus } from '../generated';
import { AdminRole, type AdminIdentity } from '../../app/permissions/permissions';
import { createContentServiceClient } from './contentServiceClient';

const readyResponse = () => new Response(JSON.stringify({ service: 'content-service', status: 'READY', timestamp: '2026-07-20T08:00:00Z' }), { status: 200, headers: { 'content-type': 'application/json' } });

async function requestWith(options: { enabled: boolean; admin: AdminIdentity | null }) {
  let captured: Request | undefined;
  const fetchMock: typeof fetch = async (request) => { captured = request as Request; return readyResponse(); };
  const client = createContentServiceClient({ baseUrl: 'http://content.test', developmentAuthEnabled: options.enabled, currentAdmin: () => options.admin, fetch: fetchMock });
  await getContentServiceStatus({ client });
  return captured!;
}

describe('Content Service shared client interceptor', () => {
  it('attaches signed-in development identity and comma-separated roles to generated calls', async () => {
    const request = await requestWith({ enabled: true, admin: { id: 'admin-7', displayName: 'Admin Seven', roles: [AdminRole.ContentAuthor, AdminRole.ContentReviewer] } });
    expect(request.url).toBe('http://content.test/api/v1/status');
    expect(request.headers.get('X-Admin-Identity')).toBe('admin-7');
    expect(request.headers.get('X-Admin-Roles')).toBe('CONTENT_AUTHOR,CONTENT_REVIEWER');
  });

  it('does not attach development headers when signed out', async () => {
    const request = await requestWith({ enabled: true, admin: null });
    expect(request.headers.has('X-Admin-Identity')).toBe(false);
    expect(request.headers.has('X-Admin-Roles')).toBe(false);
  });

  it('does not attach development headers when development authentication is disabled', async () => {
    const request = await requestWith({ enabled: false, admin: { id: 'admin-7', displayName: 'Admin Seven', roles: [AdminRole.Admin] } });
    expect(request.headers.has('X-Admin-Identity')).toBe(false);
    expect(request.headers.has('X-Admin-Roles')).toBe(false);
  });
});
