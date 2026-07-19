import type { Error as ApiError } from './generated/types.gen';

export function friendlyError(error: unknown): string {
  const apiError = (error as { error?: ApiError })?.error ?? error as ApiError;
  if (apiError?.code === 'AUTHENTICATION_REQUIRED') return 'Your learner session has expired. Check the configured learner identity.';
  if (apiError?.code === 'PRACTICE_SESSION_NOT_FOUND') return 'This practice session is no longer available.';
  if (error instanceof TypeError) return 'Cannot reach the Learning Service. Check your connection and service URL.';
  return apiError?.message ?? 'Something went wrong. Please try again.';
}
