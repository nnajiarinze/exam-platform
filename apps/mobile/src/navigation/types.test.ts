import { initialRouteFor } from './types';

describe('initialRouteFor', () => {
  it('keeps the splash while settings load', () => expect(initialRouteFor({ hydrated: false, onboardingComplete: false })).toBe('Splash'));
  it('opens onboarding for a new learner', () => expect(initialRouteFor({ hydrated: true, onboardingComplete: false })).toBe('Onboarding'));
  it('opens home for an onboarded learner', () => expect(initialRouteFor({ hydrated: true, onboardingComplete: true })).toBe('Home'));
});
