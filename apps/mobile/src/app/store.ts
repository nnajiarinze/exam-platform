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
  correctAnswers: number;
  setHydrated: (value: boolean) => void;
  completeOnboarding: (interfaceLanguage: Language, explanationLanguage: Language) => void;
  setSession: (sessionId?: string) => void;
  recordAnswer: (correct: boolean) => void;
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
  setSession: (currentSessionId) => set({ currentSessionId, correctAnswers: currentSessionId ? 0 : 0 }),
  recordAnswer: (correct) => set((state) => ({ correctAnswers: state.correctAnswers + (correct ? 1 : 0) })),
  reset: () => set({ onboardingComplete: false, currentSessionId: undefined, correctAnswers: 0 }),
}), {
  name: 'medbo-mobile-state', storage: createJSONStorage(() => AsyncStorage),
  partialize: ({ onboardingComplete, interfaceLanguage, explanationLanguage, learnerIdentity, currentSessionId, correctAnswers }) => ({ onboardingComplete, interfaceLanguage, explanationLanguage, learnerIdentity, currentSessionId, correctAnswers }),
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
