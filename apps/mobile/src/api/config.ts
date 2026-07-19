const learningBaseUrl = process.env.EXPO_PUBLIC_LEARNING_BASE_URL;

if (!learningBaseUrl && process.env.NODE_ENV !== 'test') {
  console.warn('EXPO_PUBLIC_LEARNING_BASE_URL is not configured.');
}

export const appConfig = {
  learningBaseUrl: learningBaseUrl ?? '',
  defaultLearnerIdentity: process.env.EXPO_PUBLIC_LEARNER_IDENTITY ?? '',
  examId: process.env.EXPO_PUBLIC_EXAM_ID ?? 'swedish-citizenship',
};
