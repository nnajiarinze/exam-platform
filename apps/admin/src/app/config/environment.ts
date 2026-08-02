export type AppEnvironment = 'LOCAL' | 'HOSTED';
export const environmentStorageKey = 'exam-platform.admin.environment';

export interface AdminEnvironment {
  appEnvironment: AppEnvironment;
  displayLabel: string;
  warning?: string;
  environmentSwitcherEnabled: boolean;
  contentServiceBaseUrl?: string;
  aiServiceBaseUrl: string;
  learningServiceBaseUrl: string;
  gatewayBaseUrl: string;
  developmentAuthEnabled: boolean;
  developmentAdminId?: string;
  developmentAdminName?: string;
  developmentAdminRoles: string[];
  developmentReviewerId?: string;
  developmentReviewerName?: string;
  developmentReviewerRoles: string[];
  oidcAuthority: string;
  oidcClientId: string;
  requiredScopes: string[];
}

function value(source: Record<string, string | boolean | undefined>, key: string): string {
  return typeof source[key] === 'string' ? source[key].trim().replace(/\/+$/, '') : '';
}
function bool(source: Record<string, string | boolean | undefined>, key: string): boolean {
  return source[key] === true || source[key] === 'true';
}
function validEnvironment(candidate?: string | null): candidate is AppEnvironment {
  return candidate === 'LOCAL' || candidate === 'HOSTED';
}

export function readEnvironment(
  source: Record<string, string | boolean | undefined>,
  storedSelection?: string | null,
): AdminEnvironment {
  const switcherEnabled = bool(source, 'VITE_ENABLE_ENVIRONMENT_SWITCHER');
  const configuredDefault = value(source, 'VITE_DEFAULT_APP_ENV') || (source.DEV === true ? 'LOCAL' : 'HOSTED');
  if (!validEnvironment(configuredDefault)) throw new Error('VITE_DEFAULT_APP_ENV must be LOCAL or HOSTED');
  const appEnvironment = switcherEnabled && validEnvironment(storedSelection) ? storedSelection : configuredDefault;
  const browserOrigin = typeof globalThis.location?.origin === 'string' && /^https?:\/\//.test(globalThis.location.origin)
    ? globalThis.location.origin.replace(/\/+$/, '')
    : '';
  const legacyGateway = value(source, 'VITE_API_BASE_URL');
  const gateway = appEnvironment === 'LOCAL'
    ? value(source, 'VITE_LOCAL_API_BASE_URL') || legacyGateway || 'http://localhost:8088'
    : value(source, 'VITE_HOSTED_API_BASE_URL') || legacyGateway || (source.PROD === true ? browserOrigin : 'http://localhost:8088');
  if (!gateway) throw new Error('Hosted Admin requires an explicit gateway URL or browser origin');
  const directLegacy = value(source, 'VITE_CONTENT_SERVICE_BASE_URL');
  const contentServiceBaseUrl = appEnvironment === 'LOCAL' && directLegacy
    ? directLegacy
    : `${gateway}/content`;
  const explicitIssuer = appEnvironment === 'LOCAL'
    ? value(source, 'VITE_LOCAL_OIDC_ISSUER') || value(source, 'VITE_OIDC_AUTHORITY')
    : value(source, 'VITE_HOSTED_OIDC_ISSUER');
  const oidcAuthority = explicitIssuer || `${gateway}/auth/realms/exam-platform`;
  const contentApiBaseUrl=value(source, appEnvironment === 'LOCAL' ? 'VITE_LOCAL_CONTENT_API_BASE_URL' : 'VITE_HOSTED_CONTENT_API_BASE_URL') || contentServiceBaseUrl;
  // AI and Learning administration remain behind the Content Service BFF. These
  // URLs describe the browser-reachable capability routes, never private service URLs.
  const aiServiceBaseUrl=value(source, appEnvironment === 'LOCAL' ? 'VITE_LOCAL_AI_API_BASE_URL' : 'VITE_HOSTED_AI_API_BASE_URL') || `${contentApiBaseUrl}/api/v1/admin/ai`;
  const learningServiceBaseUrl=value(source, appEnvironment === 'LOCAL' ? 'VITE_LOCAL_LEARNING_API_BASE_URL' : 'VITE_HOSTED_LEARNING_API_BASE_URL') || `${contentApiBaseUrl}/api/v1/admin/reports`;
  for (const url of [contentApiBaseUrl, aiServiceBaseUrl, learningServiceBaseUrl, oidcAuthority]) {
    try {
      const parsed = new URL(url);
      if (!['http:', 'https:'].includes(parsed.protocol)) throw new Error();
    } catch { throw new Error('Admin API and OIDC endpoints must be absolute HTTP(S) URLs'); }
  }
  if (source.PROD === true && appEnvironment === 'LOCAL') throw new Error('Production requires the HOSTED environment');

  // Development headers are a LOCAL-only authentication mechanism. Keeping them enabled after
  // selecting HOSTED prevents OIDC initialization and sends unauthenticated hosted requests.
  const enabled = appEnvironment === 'LOCAL' && bool(source, 'VITE_DEV_ADMIN_AUTH_ENABLED');
  const roles = value(source, 'VITE_DEV_ADMIN_ROLES').split(',').map((role) => role.trim()).filter(Boolean);
  const reviewerRoles = value(source, 'VITE_DEV_REVIEWER_ROLES').split(',').map((role) => role.trim()).filter(Boolean);
  if (enabled && (!source.VITE_DEV_ADMIN_ID || !source.VITE_DEV_ADMIN_NAME || roles.length === 0)) throw new Error('Development authentication requires an admin id, name, and at least one role');
  if (enabled && (!source.VITE_DEV_REVIEWER_ID || !source.VITE_DEV_REVIEWER_NAME || reviewerRoles.length === 0)) throw new Error('Development authentication requires a reviewer id, name, and at least one role');
  return {
    appEnvironment,
    displayLabel: appEnvironment === 'LOCAL' ? 'Local' : 'Hosted',
    warning: appEnvironment === 'HOSTED' && gateway.startsWith('http://') ? 'Hosted testing — insecure HTTP' : undefined,
    environmentSwitcherEnabled: switcherEnabled,
    gatewayBaseUrl: gateway,
    contentServiceBaseUrl: contentApiBaseUrl,
    aiServiceBaseUrl,
    learningServiceBaseUrl,
    developmentAuthEnabled: enabled,
    developmentAdminId: typeof source.VITE_DEV_ADMIN_ID === 'string' ? source.VITE_DEV_ADMIN_ID : undefined,
    developmentAdminName: typeof source.VITE_DEV_ADMIN_NAME === 'string' ? source.VITE_DEV_ADMIN_NAME : undefined,
    developmentAdminRoles: roles,
    developmentReviewerId: typeof source.VITE_DEV_REVIEWER_ID === 'string' ? source.VITE_DEV_REVIEWER_ID : undefined,
    developmentReviewerName: typeof source.VITE_DEV_REVIEWER_NAME === 'string' ? source.VITE_DEV_REVIEWER_NAME : undefined,
    developmentReviewerRoles: reviewerRoles,
    oidcAuthority,
    oidcClientId: value(source, 'VITE_OIDC_CLIENT_ID') || 'admin-portal',
    requiredScopes: (value(source,'VITE_OIDC_SCOPES') || 'openid profile email').split(/\s+/).filter(Boolean),
  };
}

const storedSelection = typeof localStorage?.getItem === 'function' ? localStorage.getItem(environmentStorageKey) : null;
export const environment = readEnvironment(import.meta.env, storedSelection);

export function persistEnvironment(selection: AppEnvironment): void {
  if (typeof localStorage?.setItem === 'function') localStorage.setItem(environmentStorageKey, selection);
}
