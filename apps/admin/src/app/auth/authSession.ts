import { environment } from '../config/environment';
import { isAdminRole, type AdminIdentity } from '../permissions/permissions';

export const adminSessionStorageKey = 'exam-platform.admin.development-identity';

export function readDevelopmentAdmin(): AdminIdentity | null {
  if (!environment.developmentAuthEnabled) return null;
  try {
    const value = sessionStorage.getItem(adminSessionStorageKey);
    if (!value) return null;
    const parsed = JSON.parse(value) as Partial<AdminIdentity>;
    if (typeof parsed.id !== 'string' || !parsed.id || typeof parsed.displayName !== 'string' || !parsed.displayName || !Array.isArray(parsed.roles)) return null;
    return { id: parsed.id, displayName: parsed.displayName, roles: parsed.roles.filter(isAdminRole) };
  } catch {
    sessionStorage.removeItem(adminSessionStorageKey);
    return null;
  }
}

export function storeDevelopmentAdmin(admin: AdminIdentity): void {
  sessionStorage.setItem(adminSessionStorageKey, JSON.stringify(admin));
}

export function clearDevelopmentAdmin(): void {
  sessionStorage.removeItem(adminSessionStorageKey);
}
