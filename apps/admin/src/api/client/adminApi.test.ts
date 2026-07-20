import { unwrap, unwrapVoid } from './adminApi';

describe('unwrap', () => {
  it('normalizes a fetch failure that has no HTTP response', async () => {
    await expect(unwrap(Promise.resolve({ error: new TypeError('Failed to fetch') })))
      .rejects.toMatchObject({ kind: 'NETWORK' });
  });

  it('preserves a structured HTTP error', async () => {
    const response = new Response('', { status: 409 });
    await expect(unwrap(Promise.resolve({ error: { code: 'CONFLICT', message: 'Duplicate code' }, response })))
      .rejects.toMatchObject({ kind: 'CONFLICT', status: 409, message: 'Duplicate code' });
  });

  it('accepts a successful no-content response', async () => {
    await expect(unwrapVoid(Promise.resolve({ response: new Response(null, { status: 204 }) })))
      .resolves.toBeUndefined();
  });
});
