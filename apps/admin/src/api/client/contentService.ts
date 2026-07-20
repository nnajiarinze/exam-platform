import { environment } from '../../app/config/environment';
import { ApiError, normalizeApiError, normalizeApiPayload } from '../errors/ApiError';
import { getContentServiceStatus, type ContentServiceStatus } from '../generated';
import { contentServiceClient } from './contentServiceClient';

export async function fetchContentServiceStatus(signal?: AbortSignal): Promise<ContentServiceStatus> {
  if (!environment.contentServiceBaseUrl) {
    throw new ApiError('CONFIGURATION', 'Content Service URL is not configured.');
  }
  try {
    const result = await getContentServiceStatus({
      client: contentServiceClient,
      signal,
    });
    if (result.error) throw normalizeApiPayload(result.error, result.response.status, result.response.headers.get('x-correlation-id') ?? undefined);
    return result.data;
  } catch (error) {
    throw await normalizeApiError(error);
  }
}
