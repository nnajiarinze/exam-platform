import type { PracticeMode } from '../api/generated/types.gen';

export type RootStackParamList = {
  Splash: undefined;
  Onboarding: undefined;
  Home: undefined;
  Topics: undefined;
  PracticeSetup: { mode: Extract<PracticeMode, 'TOPIC' | 'MIXED'>; topicId?: string; topicName?: string };
  Question: { sessionId: string };
  SessionComplete: { total: number };
  Progress: undefined;
  Settings: undefined;
  MockExam: undefined;
  MockQuestion: { attemptId: string; sequenceNumber: number };
  MockResults: { attemptId: string };
  MockHistory: undefined;
};

export type InitialRouteInput = { hydrated: boolean; onboardingComplete: boolean };
export function initialRouteFor({ hydrated, onboardingComplete }: InitialRouteInput): 'Splash' | 'Onboarding' | 'Home' {
  if (!hydrated) return 'Splash';
  return onboardingComplete ? 'Home' : 'Onboarding';
}
