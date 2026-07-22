import { environmentConfig } from '../config/environment';

export { joinBaseUrl, normalizeBaseUrl } from '../config/environment';

export const appConfig = {
  environment: environmentConfig.environment,
  publicApiBaseUrl: environmentConfig.apiBaseUrl,
  learningBaseUrl: environmentConfig.learningBaseUrl,
  authBaseUrl: environmentConfig.authBaseUrl,
  defaultLearnerIdentity: process.env.EXPO_PUBLIC_LEARNER_IDENTITY ?? '',
  examId: process.env.EXPO_PUBLIC_EXAM_ID ?? 'swedish-citizenship',
  oidcIssuer: environmentConfig.oidcIssuer,
  oidcClientId: process.env.EXPO_PUBLIC_OIDC_CLIENT_ID ?? 'mobile-app',
  privacyUrl: process.env.EXPO_PUBLIC_PRIVACY_URL ?? '',
  termsUrl: process.env.EXPO_PUBLIC_TERMS_URL ?? '',
  helpUrl: process.env.EXPO_PUBLIC_HELP_URL ?? '',
};
