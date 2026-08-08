import { providerAuthorizationUrl } from './AuthContext';

const base = 'https://identity.example.test/realms/exam-platform/protocol/openid-connect/auth?client_id=mobile-app&state=state-1';

describe('provider-specific authorization routing', () => {
  it('uses exact broker hints without changing PKCE and state parameters', () => {
    const google = new URL(providerAuthorizationUrl(base, 'google'));
    const apple = new URL(providerAuthorizationUrl(base, 'apple'));
    expect(google.searchParams.get('kc_idp_hint')).toBe('google');
    expect(apple.searchParams.get('kc_idp_hint')).toBe('apple');
    expect(google.searchParams.get('state')).toBe('state-1');
  });

  it('uses Keycloak registration and required-action endpoints without collecting a password', () => {
    expect(new URL(providerAuthorizationUrl(base, 'register')).pathname).toMatch(/\/protocol\/openid-connect\/registrations$/);
    expect(new URL(providerAuthorizationUrl(base, 'password-reset')).searchParams.get('kc_action')).toBe('UPDATE_PASSWORD');
  });

  it('links directly while reserving forced authentication for destructive flows', () => {
    const googleLink = new URL(providerAuthorizationUrl(base, 'link-google'));
    const appleLink = new URL(providerAuthorizationUrl(base, 'link-apple'));
    expect(googleLink.searchParams.get('kc_action')).toBe('idp_link:google');
    expect(appleLink.searchParams.get('kc_action')).toBe('idp_link:apple');
    expect(googleLink.searchParams.get('prompt')).toBeNull();
    expect(googleLink.searchParams.get('max_age')).toBeNull();
    const recent = new URL(providerAuthorizationUrl(base, 'reauthenticate'));
    expect(recent.searchParams.get('prompt')).toBe('login');
    expect(recent.searchParams.get('max_age')).toBe('0');
    expect(recent.searchParams.get('kc_idp_hint')).toBeNull();
  });
});
