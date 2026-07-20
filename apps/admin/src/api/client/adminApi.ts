import { normalizeApiPayload, ApiError } from '../errors/ApiError';

export async function unwrap<T>(promise: Promise<{ data?: T; error?: unknown; response?: Response }>): Promise<T> {
  const result=await promise;
  if(result.error) {
    if (!result.response) {
      throw new ApiError('NETWORK', 'The Content Service could not be reached. Check the service and browser origin.');
    }
    const payload = typeof result.error === 'object' && result.error !== null
      ? result.error as {code?: string;message?: string;errors?: unknown[]}
      : {};
    throw normalizeApiPayload(payload,result.response.status,result.response.headers.get('x-correlation-id')??undefined);
  }
  if(result.data===undefined) throw new ApiError('SERVER','The Content Service returned no data.',result.response?.status);
  return result.data;
}

export async function unwrapVoid(promise: Promise<{ error?: unknown; response?: Response }>): Promise<void> {
  const result = await promise;
  if (!result.error) return;
  if (!result.response) {
    throw new ApiError('NETWORK', 'The Content Service could not be reached. Check the service and browser origin.');
  }
  const payload = typeof result.error === 'object' && result.error !== null
    ? result.error as {code?: string;message?: string;errors?: unknown[]}
    : {};
  throw normalizeApiPayload(payload,result.response.status,result.response.headers.get('x-correlation-id')??undefined);
}
