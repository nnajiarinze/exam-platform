let accessToken: string | undefined;
let expiresAt = 0;
let refresh: (() => Promise<string | undefined>) | undefined;
let expired: (() => void) | undefined;

export function configureAuthTokens(config: { accessToken?: string; expiresAt?: number; refresh: () => Promise<string | undefined>; expired: () => void }) {
  accessToken = config.accessToken; expiresAt = config.expiresAt ?? 0; refresh = config.refresh; expired = config.expired;
}
export function updateAccessToken(token?: string, expiration?: number) { accessToken = token; expiresAt = expiration ?? 0; }
export function clearAccessToken() { accessToken = undefined; expiresAt = 0; }
export async function validAccessToken() {
  if (accessToken && expiresAt - Date.now() > 60_000) return accessToken;
  return refresh?.();
}
export function authenticationExpired() { expired?.(); }
