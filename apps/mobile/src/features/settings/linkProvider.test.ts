import { linkIdentityProvider } from './linkProvider';

const methods = { methods: [{ id: 'google', linked: true }, { id: 'apple', linked: true }] };

function flow(provider: 'apple' | 'google' = 'apple', overrides: Record<string, unknown> = {}) {
  return {
    provider,
    initiate: jest.fn(async () => ({ keycloakAction: `idp_link:${provider}` })),
    link: jest.fn(async () => true),
    reload: jest.fn(async () => methods),
    ...overrides,
  };
}

it.each(['apple', 'google'] as const)('links %s directly after explicit provider authentication', async provider => {
  const input = flow(provider);
  await expect(linkIdentityProvider(input)).resolves.toBe(methods);
  expect(input.initiate).toHaveBeenCalledTimes(1);
  expect(input.link).toHaveBeenCalledTimes(1);
  expect(input.reload).toHaveBeenCalledTimes(1);
});

it('returns cleanly when provider authentication is cancelled', async () => {
  const input = flow('apple', { link: jest.fn(async () => false) });
  await expect(linkIdentityProvider(input)).resolves.toBeUndefined();
  expect(input.reload).not.toHaveBeenCalled();
});

it('fails closed when the backend action does not match the requested provider', async () => {
  const input = flow('google', { initiate: jest.fn(async () => ({ keycloakAction: 'idp_link:apple' })) });
  await expect(linkIdentityProvider(input)).rejects.toThrow('Invalid identity-link action');
  expect(input.link).not.toHaveBeenCalled();
});

it('propagates initiation conflicts without retrying or reauthenticating', async () => {
  const conflict = { code: 'IDENTITY_LINK_CONFLICT' };
  const initiate = jest.fn(async () => { throw conflict; });
  const input = flow('google', { initiate });
  await expect(linkIdentityProvider(input)).rejects.toBe(conflict);
  expect(initiate).toHaveBeenCalledTimes(1);
  expect(input.link).not.toHaveBeenCalled();
});
