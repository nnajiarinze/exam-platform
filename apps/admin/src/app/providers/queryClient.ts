import { QueryClient } from '@tanstack/react-query';
import { ApiError } from '../../api/errors/ApiError';

export function shouldRetry(failureCount: number, error: unknown): boolean {
  if (failureCount >= 2) return false;
  return !(error instanceof ApiError) || error.kind === 'NETWORK' || (error.kind === 'SERVER' && error.status !== undefined && error.status >= 500);
}
export function createQueryClient() { return new QueryClient({ defaultOptions: { queries: { retry: shouldRetry, staleTime: 30_000, refetchOnWindowFocus: false }, mutations: { retry: false } } }); }
