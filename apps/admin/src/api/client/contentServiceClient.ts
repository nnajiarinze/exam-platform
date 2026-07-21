import { environment } from '../../app/config/environment';
import { readDevelopmentAdmin } from '../../app/auth/authSession';
import type { AdminIdentity } from '../../app/permissions/permissions';
import { createClient, type Client } from '../generated/client';
import { currentAccessToken } from '../../app/auth/oidc';

interface ContentServiceClientOptions {
  baseUrl?: string;
  developmentAuthEnabled: boolean;
  currentAdmin: () => AdminIdentity | null;
  fetch: typeof globalThis.fetch;
}

export function createContentServiceClient(options: ContentServiceClientOptions): Client {
  const client = createClient({ baseUrl: options.baseUrl, fetch: options.fetch });
  client.interceptors.request.use((request) => {
    if (!options.developmentAuthEnabled) {
      const token=currentAccessToken();if(!token)return request;const headers=new Headers(request.headers);headers.set('Authorization',`Bearer ${token}`);return new Request(request,{headers});
    }
    const admin = options.currentAdmin();
    if (!admin) return request;
    const headers = new Headers(request.headers);
    headers.set('X-Admin-Identity', admin.id);
    headers.set('X-Admin-Roles', admin.roles.join(','));
    return new Request(request, { headers });
  });
  return client;
}

export const contentServiceClient = createContentServiceClient({
  baseUrl: environment.contentServiceBaseUrl,
  developmentAuthEnabled: environment.developmentAuthEnabled,
  currentAdmin: readDevelopmentAdmin,
  fetch: (...args) => globalThis.fetch(...args),
});
