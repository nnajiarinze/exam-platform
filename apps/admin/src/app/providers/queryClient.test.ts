import { ApiError } from '../../api/errors/ApiError';
import { shouldRetry } from './queryClient';

describe('query retry policy', () => {
  it('retries transient errors but not client or domain errors', () => {
    expect(shouldRetry(0, new ApiError('NETWORK', 'offline'))).toBe(true);
    expect(shouldRetry(0, new ApiError('SERVER', 'down', 503))).toBe(true);
    expect(shouldRetry(0, new ApiError('FORBIDDEN', 'no', 403))).toBe(false);
    expect(shouldRetry(0, new ApiError('CONFLICT', 'changed', 409))).toBe(false);
    expect(shouldRetry(2, new Error('still down'))).toBe(false);
  });
});
