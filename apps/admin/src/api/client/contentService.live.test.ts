import { storeDevelopmentAdmin } from '../../app/auth/authSession';
import { AdminRole } from '../../app/permissions/permissions';
import { fetchContentServiceStatus } from './contentService';

const liveTest = import.meta.env.VITE_RUN_LIVE_CONTENT_SERVICE_TEST === 'true' ? it : it.skip;

describe('running Content Service integration', () => {
  liveTest('returns READY through the Admin Portal generated client and session interceptor', async () => {
    storeDevelopmentAdmin({ id: 'dev-content-admin', displayName: 'Local content administrator', roles: [AdminRole.Admin] });
    await expect(fetchContentServiceStatus()).resolves.toMatchObject({ service: 'content-service', status: 'READY' });
  });
});
