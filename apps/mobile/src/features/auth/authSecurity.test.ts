import { AuthValidationError, validateAccessToken, validateIdToken } from './authSecurity';

function jwt(payload: Record<string, unknown>) {
  const encode = (value: object) => globalThis.btoa(JSON.stringify(value)).replaceAll('+', '-').replaceAll('/', '_').replaceAll('=', '');
  return `${encode({ alg: 'none' })}.${encode(payload)}.`;
}

const now = 1_700_000_000_000;
const issuer = 'https://identity.example.test/realms/exam-platform';

describe('mobile OIDC claim validation', () => {
  it('accepts the configured issuer and learning audience', () => {
    expect(validateAccessToken(jwt({ sub: 'learner-1', iss: issuer, aud: ['account', 'learning-api'], exp: now / 1000 + 60 }), issuer, 'learning-api', now).sub).toBe('learner-1');
  });

  it.each([
    ['AUTH_ISSUER_MISMATCH', { sub: 'learner-1', iss: 'https://wrong.example', aud: 'learning-api', exp: now / 1000 + 60 }],
    ['AUTH_AUDIENCE_MISMATCH', { sub: 'learner-1', iss: issuer, aud: 'other-api', exp: now / 1000 + 60 }],
    ['AUTH_TOKEN_EXPIRED', { sub: 'learner-1', iss: issuer, aud: 'learning-api', exp: now / 1000 - 1 }],
  ])('rejects %s', (code, payload) => {
    expect(() => validateAccessToken(jwt(payload), issuer, 'learning-api', now)).toThrow(new AuthValidationError(code));
  });

  it('binds the ID token to client, nonce, issuer, subject and expiry', () => {
    expect(validateIdToken(jwt({ sub: 'learner-1', iss: issuer, aud: 'mobile-app', nonce: 'nonce-1', exp: now / 1000 + 60 }), issuer, 'mobile-app', 'nonce-1', 'learner-1', now).nonce).toBe('nonce-1');
    expect(() => validateIdToken(jwt({ sub: 'learner-1', iss: issuer, aud: 'mobile-app', nonce: 'replayed', exp: now / 1000 + 60 }), issuer, 'mobile-app', 'nonce-1', 'learner-1', now)).toThrow(new AuthValidationError('AUTH_NONCE_MISMATCH'));
  });
});
