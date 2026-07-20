import { AdminRole, Permission, can, permissionsFor, type AdminIdentity } from './permissions';

describe('admin permissions', () => {
  const identity = (roles: AdminRole[]): AdminIdentity => ({ id: 'admin-1', displayName: 'Admin One', roles });

  it('maps author permissions without granting publishing', () => {
    expect(permissionsFor(identity([AdminRole.ContentAuthor]))).toContain(Permission.CreateDraftContent);
    expect(can(identity([AdminRole.ContentAuthor]), Permission.PublishRelease)).toBe(false);
  });

  it('combines permissions for multiple roles without duplicates', () => {
    const permissions = permissionsFor(identity([AdminRole.ContentReviewer, AdminRole.ContentPublisher]));
    expect(permissions).toContain(Permission.ApproveContent);
    expect(permissions).toContain(Permission.PublishRelease);
    expect(new Set(permissions).size).toBe(permissions.length);
  });

  it('grants all defined permissions to ADMIN and nothing to no identity', () => {
    expect(permissionsFor(identity([AdminRole.Admin]))).toEqual(expect.arrayContaining(Object.values(Permission)));
    expect(can(null, Permission.ViewContent)).toBe(false);
  });
});
