import { readEnvironment } from './environment';

describe('environment configuration', () => {
  it('normalizes the service URL and development roles', () => {
    expect(readEnvironment({ VITE_CONTENT_SERVICE_BASE_URL: 'https://content.example/', VITE_DEV_ADMIN_AUTH_ENABLED: 'true', VITE_DEV_ADMIN_ID: 'a', VITE_DEV_ADMIN_NAME: 'Admin', VITE_DEV_ADMIN_ROLES: 'CONTENT_AUTHOR, CONTENT_REVIEWER' })).toMatchObject({ contentServiceBaseUrl: 'https://content.example', developmentAdminRoles: ['CONTENT_AUTHOR', 'CONTENT_REVIEWER'] });
  });

  it('rejects invalid URLs', () => {
    expect(() => readEnvironment({ VITE_CONTENT_SERVICE_BASE_URL: 'localhost:8081' })).toThrow(/absolute URL/);
  });

  it('requires complete development identity configuration', () => {
    expect(() => readEnvironment({ VITE_DEV_ADMIN_AUTH_ENABLED: 'true' })).toThrow(/requires an admin id/);
  });
});
