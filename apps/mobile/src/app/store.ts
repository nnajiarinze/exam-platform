import AsyncStorage from '@react-native-async-storage/async-storage';
import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';
import { appConfig } from '../api/config';

export type Language = 'en' | 'sv';
type AppState = {
  hydrated: boolean;
  onboardingComplete: boolean;
  interfaceLanguage: Language;
  explanationLanguage: Language;
  learnerIdentity: string;
  currentSessionId?: string;
  currentPracticeLabel?: string;
  sessionStartedAt?: string;
  currentMockAttemptId?: string;
  correctAnswers: number;
  setHydrated: (value: boolean) => void;
  completeOnboarding: (interfaceLanguage: Language, explanationLanguage: Language) => void;
  setSession: (sessionId?: string, label?: string) => void;
  setMockAttempt: (attemptId?: string) => void;
  recordAnswer: (correct: boolean) => void;
  setLearnerIdentity: (identity: string) => void;
  clearUserData: () => void;
  reset: () => void;
};

export const useAppStore = create<AppState>()(persist((set) => ({
  hydrated: false,
  onboardingComplete: false,
  interfaceLanguage: 'en',
  explanationLanguage: 'en',
  learnerIdentity: appConfig.defaultLearnerIdentity,
  correctAnswers: 0,
  setHydrated: (hydrated) => set({ hydrated }),
  completeOnboarding: (interfaceLanguage, explanationLanguage) => set({ onboardingComplete: true, interfaceLanguage, explanationLanguage }),
  setSession: (currentSessionId, currentPracticeLabel) => set({ currentSessionId, currentPracticeLabel: currentSessionId ? currentPracticeLabel : undefined, sessionStartedAt: currentSessionId ? new Date().toISOString() : undefined, correctAnswers: 0 }),
  setMockAttempt: (currentMockAttemptId) => set({ currentMockAttemptId }),
  recordAnswer: (correct) => set((state) => ({ correctAnswers: state.correctAnswers + (correct ? 1 : 0) })),
  setLearnerIdentity: (learnerIdentity) => set({ learnerIdentity }),
  clearUserData: () => set({ learnerIdentity: '', currentSessionId: undefined, currentPracticeLabel: undefined, sessionStartedAt: undefined, currentMockAttemptId: undefined, correctAnswers: 0 }),
  reset: () => set({ onboardingComplete: false, currentSessionId: undefined, currentPracticeLabel: undefined, sessionStartedAt: undefined, correctAnswers: 0 }),
}), {
  name: 'medbo-mobile-state', storage: createJSONStorage(() => AsyncStorage),
  partialize: ({ onboardingComplete, interfaceLanguage, explanationLanguage, learnerIdentity, currentSessionId, currentPracticeLabel, sessionStartedAt, currentMockAttemptId, correctAnswers }) => ({ onboardingComplete, interfaceLanguage, explanationLanguage, learnerIdentity, currentSessionId, currentPracticeLabel, sessionStartedAt, currentMockAttemptId, correctAnswers }),
  merge: (persistedState, currentState) => {
    const persisted = persistedState as Partial<AppState>;
    return {
      ...currentState,
      ...persisted,
      learnerIdentity: persisted.learnerIdentity || currentState.learnerIdentity,
    };
  },
  onRehydrateStorage: () => (state) => state?.setHydrated(true),
}));

export function resetStoreForTests() {
  useAppStore.setState({ hydrated: true, onboardingComplete: false, interfaceLanguage: 'en', explanationLanguage: 'en', learnerIdentity: 'test-learner', currentSessionId: undefined, correctAnswers: 0 });
}
