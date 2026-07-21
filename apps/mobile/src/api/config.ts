export function normalizeBaseUrl(value: string): string {
  const normalized = value.trim().replace(/\/+$/, '');
  const parsed = new URL(normalized);
  if (!['http:', 'https:'].includes(parsed.protocol)) throw new Error('API base URL must use HTTP(S)');
  return normalized;
}

export function joinBaseUrl(baseUrl: string, path: string): string {
  return `${normalizeBaseUrl(baseUrl)}/${path.replace(/^\/+/, '')}`;
}

const publicApiBaseUrl = process.env.EXPO_PUBLIC_API_BASE_URL;
const legacyLearningBaseUrl = process.env.EXPO_PUBLIC_LEARNING_BASE_URL;
const appEnvironment = process.env.EXPO_PUBLIC_APP_ENV ?? 'development';
const learningBaseUrl = publicApiBaseUrl
  ? joinBaseUrl(publicApiBaseUrl, 'learning')
  : legacyLearningBaseUrl
    ? normalizeBaseUrl(legacyLearningBaseUrl)
    : '';

if (!learningBaseUrl && process.env.NODE_ENV !== 'test') {
  console.warn('EXPO_PUBLIC_API_BASE_URL is not configured.');
}
if (appEnvironment === 'production' && (!publicApiBaseUrl || /localhost|127\.0\.0\.1/i.test(publicApiBaseUrl))) {
  throw new Error('Production requires a non-local EXPO_PUBLIC_API_BASE_URL');
}

export const appConfig = {
  publicApiBaseUrl: publicApiBaseUrl ? normalizeBaseUrl(publicApiBaseUrl) : '',
  learningBaseUrl,
  defaultLearnerIdentity: process.env.EXPO_PUBLIC_LEARNER_IDENTITY ?? '',
  examId: process.env.EXPO_PUBLIC_EXAM_ID ?? 'swedish-citizenship',
  oidcIssuer: process.env.EXPO_PUBLIC_OIDC_ISSUER ?? (publicApiBaseUrl ? joinBaseUrl(publicApiBaseUrl, 'auth/realms/exam-platform') : ''),
  oidcClientId: process.env.EXPO_PUBLIC_OIDC_CLIENT_ID ?? 'mobile-app',
  privacyUrl: process.env.EXPO_PUBLIC_PRIVACY_URL ?? '',
  termsUrl: process.env.EXPO_PUBLIC_TERMS_URL ?? '',
  helpUrl: process.env.EXPO_PUBLIC_HELP_URL ?? '',
};
