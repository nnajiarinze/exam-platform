import type { User } from 'oidc-client-ts';
import { identityFromUser } from './oidc';

function token(payload: object): string {
  return `header.${btoa(JSON.stringify(payload))}.signature`;
}

describe('OIDC admin identity', () => {
  it('uses realm roles from the access token when the ID-token profile omits them', () => {
    const user = {
      profile: { sub: 'adminari', name: 'Arinze Nnaji' },
      access_token: token({ realm_access: { roles: ['ADMIN', 'CONTENT_AUTHOR', 'LEARNER'] } }),
    } as User;

    expect(identityFromUser(user)).toEqual({
      id: 'adminari',
      displayName: 'Arinze Nnaji',
      roles: ['ADMIN', 'CONTENT_AUTHOR'],
    });
  });

  it('ignores malformed access tokens', () => {
    const user = { profile: { sub: 'adminari' }, access_token: 'invalid' } as User;
    expect(identityFromUser(user).roles).toEqual([]);
  });
});
