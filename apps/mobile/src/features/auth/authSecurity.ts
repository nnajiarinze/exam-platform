export type JwtClaims = {
  sub?: string;
  iss?: string;
  aud?: string | string[];
  azp?: string;
  nonce?: string;
  exp?: number;
  email?: string;
  email_verified?: boolean;
  name?: string;
};

export class AuthValidationError extends Error {
  constructor(public readonly code: string) {
    super(code);
    this.name = 'AuthValidationError';
  }
}

export function decodeJwt(token: string): JwtClaims {
  try {
    const encoded = token.split('.')[1];
    if (!encoded) throw new Error();
    const normalized = encoded.replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(globalThis.atob(normalized)) as JwtClaims;
  } catch {
    throw new AuthValidationError('AUTH_TOKEN_MALFORMED');
  }
}

function hasAudience(claims: JwtClaims, expected: string): boolean {
  const audiences = Array.isArray(claims.aud) ? claims.aud : claims.aud ? [claims.aud] : [];
  return audiences.includes(expected) || claims.azp === expected;
}

export function validateAccessToken(token: string, issuer: string, audience: string, now = Date.now()): JwtClaims {
  const value = decodeJwt(token);
  if (value.iss !== issuer) throw new AuthValidationError('AUTH_ISSUER_MISMATCH');
  if (!hasAudience(value, audience)) throw new AuthValidationError('AUTH_AUDIENCE_MISMATCH');
  if (!value.sub) throw new AuthValidationError('AUTH_SUBJECT_MISSING');
  if (!value.exp || value.exp * 1000 <= now) throw new AuthValidationError('AUTH_TOKEN_EXPIRED');
  return value;
}

export function validateIdToken(token: string, issuer: string, clientId: string, nonce: string, subject: string, now = Date.now()): JwtClaims {
  const value = decodeJwt(token);
  if (value.iss !== issuer) throw new AuthValidationError('AUTH_ISSUER_MISMATCH');
  if (!hasAudience(value, clientId)) throw new AuthValidationError('AUTH_AUDIENCE_MISMATCH');
  if (value.nonce !== nonce) throw new AuthValidationError('AUTH_NONCE_MISMATCH');
  if (value.sub !== subject) throw new AuthValidationError('AUTH_SUBJECT_MISMATCH');
  if (!value.exp || value.exp * 1000 <= now) throw new AuthValidationError('AUTH_TOKEN_EXPIRED');
  return value;
}
