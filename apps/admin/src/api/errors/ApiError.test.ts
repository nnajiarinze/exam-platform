import { ApiError, normalizeApiError } from './ApiError';

describe('API error normalization', () => {
  it('preserves safe backend details and correlation id', async () => {
    const response = new Response(JSON.stringify({ code: 'INVALID_CONTENT', message: 'Content is invalid', errors: [{ field: 'title', message: 'required' }] }), { status: 422, headers: { 'content-type': 'application/json', 'x-correlation-id': 'trace-1' } });
    await expect(normalizeApiError(response)).resolves.toMatchObject({ kind: 'VALIDATION', status: 422, code: 'INVALID_CONTENT', message: 'Content is invalid', fieldErrors: [{ field: 'title', message: 'required' }], correlationId: 'trace-1' });
  });

  it('does not expose unstructured server response bodies', async () => {
    const error = await normalizeApiError(new Response('database stack trace', { status: 500 }));
    expect(error.message).toBe('The service encountered an unexpected error.');
    expect(error.message).not.toContain('database');
  });

  it('normalizes fetch failures as network errors', async () => {
    await expect(normalizeApiError(new TypeError('secret request details'))).resolves.toEqual(expect.objectContaining({ kind: 'NETWORK' }));
    expect(await normalizeApiError(new ApiError('FORBIDDEN', 'No access', 403))).toMatchObject({ kind: 'FORBIDDEN' });
  });
});
