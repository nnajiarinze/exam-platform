import type { PracticeMode } from '../api/generated/types.gen';

export type RootStackParamList = {
  Welcome: undefined;
  Login: undefined;
  Register: undefined;
  ForgotPassword: undefined;
  VerificationPending: undefined;
  SessionExpired: undefined;
  Splash: undefined;
  Onboarding: undefined;
  Home: undefined;
  Topics: undefined;
  StudySubjects: undefined;
  StudyTopics: { subjectId: string; subjectTitle: string };
  TopicLesson: { topicId: string; topicTitle: string; sectionId?: string };
  PracticeSetup: { mode: Extract<PracticeMode, 'TOPIC' | 'MIXED'>; topicId?: string; topicName?: string };
  Question: { sessionId: string };
  SessionComplete: { total: number };
  Progress: undefined;
  Settings: undefined;
  Profile: undefined;
  EditProfile: undefined;
  ChangePassword: undefined;
  StudyGoals: undefined;
  NotificationPreferences: undefined;
  PrivacyLegal: undefined;
  DeleteAccount: undefined;
  MockExam: undefined;
  MockQuestion: { attemptId: string; sequenceNumber: number };
  MockResults: { attemptId: string };
  MockAnswerReview: { attemptId: string };
  MockHistory: undefined;
};

export type InitialRouteInput = { hydrated: boolean; onboardingComplete: boolean };
export function initialRouteFor({ hydrated, onboardingComplete }: InitialRouteInput): 'Splash' | 'Onboarding' | 'Home' {
  if (!hydrated) return 'Splash';
  return onboardingComplete ? 'Home' : 'Onboarding';
}
