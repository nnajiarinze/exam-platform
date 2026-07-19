import { resetStoreForTests, useAppStore } from './store';

beforeEach(resetStoreForTests);
it('tracks only correctness returned by the backend', () => {
  useAppStore.getState().setSession('session-1');
  useAppStore.getState().recordAnswer(true); useAppStore.getState().recordAnswer(false);
  expect(useAppStore.getState().correctAnswers).toBe(1);
});

it('has a usable learner identity after hydration', () => {
  expect(useAppStore.getState().learnerIdentity).toBe('test-learner');
});
