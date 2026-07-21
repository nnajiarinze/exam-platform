export type ApiErrorKind =
  | "VALIDATION"
  | "AUTHENTICATION"
  | "FORBIDDEN"
  | "NOT_FOUND"
  | "CONFLICT"
  | "NETWORK"
  | "SERVER"
  | "CONFIGURATION";
export interface FieldError {
  field: string;
  message: string;
}
export interface BlockingReference {
  type: string;
  count: number;
  message: string;
  recommendedAction?: string;
}
export class ApiError extends Error {
  constructor(
    public readonly kind: ApiErrorKind,
    message: string,
    public readonly status?: number,
    public readonly code?: string,
    public readonly fieldErrors: FieldError[] = [],
    public readonly correlationId?: string,
    public readonly blockingReferences: BlockingReference[] = [],
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export async function normalizeApiError(error: unknown): Promise<ApiError> {
  if (error instanceof ApiError) return error;
  if (!isResponse(error))
    return new ApiError("NETWORK", "The Content Service could not be reached.");
  let body: { code?: string; message?: string; errors?: FieldError[] } = {};
  try {
    body = (await error.clone().json()) as typeof body;
  } catch {
    /* Response did not contain safe structured JSON. */
  }
  return normalizeApiPayload(
    body,
    error.status,
    error.headers.get("x-correlation-id") ?? undefined,
  );
}

export function normalizeApiPayload(
  body: { code?: string; message?: string; errors?: unknown[] },
  status: number,
  correlationId?: string,
): ApiError {
  const kind: ApiErrorKind =
    status === 400 || status === 422
      ? "VALIDATION"
      : status === 401
        ? "AUTHENTICATION"
        : status === 403
          ? "FORBIDDEN"
          : status === 404
            ? "NOT_FOUND"
            : status === 409
              ? "CONFLICT"
              : "SERVER";
  const defaults: Record<ApiErrorKind, string> = {
    VALIDATION: "Please correct the invalid information.",
    AUTHENTICATION: "Authentication is required.",
    FORBIDDEN: "You do not have permission to perform this action.",
    NOT_FOUND: "The requested resource was not found.",
    CONFLICT: "The request conflicts with the current resource state.",
    NETWORK: "The service could not be reached.",
    SERVER: "The service encountered an unexpected error.",
    CONFIGURATION: "The service is not configured.",
  };
  const fieldErrors = (body.errors ?? []).filter(
    (item): item is FieldError =>
      typeof item === "object" &&
      item !== null &&
      typeof (item as FieldError).field === "string" &&
      typeof (item as FieldError).message === "string",
  );
  const blockingReferences = (body.errors ?? []).filter(
    (item): item is BlockingReference =>
      typeof item === "object" &&
      item !== null &&
      typeof (item as BlockingReference).type === "string" &&
      typeof (item as BlockingReference).count === "number" &&
      typeof (item as BlockingReference).message === "string",
  );
  return new ApiError(
    kind,
    body.message || defaults[kind],
    status,
    body.code,
    fieldErrors,
    correlationId,
    blockingReferences,
  );
}

function isResponse(value: unknown): value is Response {
  return (
    typeof value === "object" &&
    value !== null &&
    "status" in value &&
    "headers" in value &&
    "clone" in value &&
    typeof value.clone === "function"
  );
}
