export interface AdminEnvironment {
  contentServiceBaseUrl?: string;
  developmentAuthEnabled: boolean;
  developmentAdminId?: string;
  developmentAdminName?: string;
  developmentAdminRoles: string[];
  developmentReviewerId?: string;
  developmentReviewerName?: string;
  developmentReviewerRoles: string[];
  oidcAuthority: string;
  oidcClientId: string;
}

export function readEnvironment(source: Record<string, string | boolean | undefined>): AdminEnvironment {
  const publicApiBaseUrl = typeof source.VITE_API_BASE_URL === 'string' ? source.VITE_API_BASE_URL.trim().replace(/\/+$/, '') : '';
  const legacyBaseUrl = typeof source.VITE_CONTENT_SERVICE_BASE_URL === 'string' ? source.VITE_CONTENT_SERVICE_BASE_URL.trim().replace(/\/+$/, '') : '';
  const baseUrl = publicApiBaseUrl ? `${publicApiBaseUrl}/content` : legacyBaseUrl;
  if (baseUrl) {
    try {
      const parsed = new URL(baseUrl);
      if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') throw new Error('unsupported protocol');
    } catch { throw new Error('VITE_API_BASE_URL must be an absolute URL using HTTP(S)'); }
  }
  if (source.PROD === true && (!publicApiBaseUrl || /localhost|127\.0\.0\.1/i.test(publicApiBaseUrl))) throw new Error('Production requires a non-local VITE_API_BASE_URL');
  const enabled = source.VITE_DEV_ADMIN_AUTH_ENABLED === true || source.VITE_DEV_ADMIN_AUTH_ENABLED === 'true';
  const roles = typeof source.VITE_DEV_ADMIN_ROLES === 'string' ? source.VITE_DEV_ADMIN_ROLES.split(',').map((role) => role.trim()).filter(Boolean) : [];
  const reviewerRoles = typeof source.VITE_DEV_REVIEWER_ROLES === 'string' ? source.VITE_DEV_REVIEWER_ROLES.split(',').map((role) => role.trim()).filter(Boolean) : [];
  if (enabled && (!source.VITE_DEV_ADMIN_ID || !source.VITE_DEV_ADMIN_NAME || roles.length === 0)) {
    throw new Error('Development authentication requires an admin id, name, and at least one role');
  }
  if (enabled && (!source.VITE_DEV_REVIEWER_ID || !source.VITE_DEV_REVIEWER_NAME || reviewerRoles.length === 0)) {
    throw new Error('Development authentication requires a reviewer id, name, and at least one role');
  }
  return {
    contentServiceBaseUrl: baseUrl || undefined,
    developmentAuthEnabled: enabled,
    developmentAdminId: typeof source.VITE_DEV_ADMIN_ID === 'string' ? source.VITE_DEV_ADMIN_ID : undefined,
    developmentAdminName: typeof source.VITE_DEV_ADMIN_NAME === 'string' ? source.VITE_DEV_ADMIN_NAME : undefined,
    developmentAdminRoles: roles,
    developmentReviewerId: typeof source.VITE_DEV_REVIEWER_ID === 'string' ? source.VITE_DEV_REVIEWER_ID : undefined,
    developmentReviewerName: typeof source.VITE_DEV_REVIEWER_NAME === 'string' ? source.VITE_DEV_REVIEWER_NAME : undefined,
    developmentReviewerRoles: reviewerRoles,
    oidcAuthority: typeof source.VITE_OIDC_AUTHORITY === 'string' ? source.VITE_OIDC_AUTHORITY.replace(/\/$/,'') : publicApiBaseUrl ? `${publicApiBaseUrl}/auth/realms/exam-platform` : '',
    oidcClientId: typeof source.VITE_OIDC_CLIENT_ID === 'string' ? source.VITE_OIDC_CLIENT_ID : 'admin-portal',
  };
}

export const environment = readEnvironment(import.meta.env);
