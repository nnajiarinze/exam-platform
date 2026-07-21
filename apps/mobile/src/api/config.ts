const learningBaseUrl = process.env.EXPO_PUBLIC_LEARNING_BASE_URL;

if (!learningBaseUrl && process.env.NODE_ENV !== 'test') {
  console.warn('EXPO_PUBLIC_LEARNING_BASE_URL is not configured.');
}

export const appConfig = {
  learningBaseUrl: learningBaseUrl ?? '',
  defaultLearnerIdentity: process.env.EXPO_PUBLIC_LEARNER_IDENTITY ?? '',
  examId: process.env.EXPO_PUBLIC_EXAM_ID ?? 'swedish-citizenship',
  oidcIssuer: process.env.EXPO_PUBLIC_OIDC_ISSUER ?? 'http://localhost:8090/realms/exam-platform',
  oidcClientId: process.env.EXPO_PUBLIC_OIDC_CLIENT_ID ?? 'mobile-app',
  privacyUrl: process.env.EXPO_PUBLIC_PRIVACY_URL ?? '',
  termsUrl: process.env.EXPO_PUBLIC_TERMS_URL ?? '',
  helpUrl: process.env.EXPO_PUBLIC_HELP_URL ?? '',
};
