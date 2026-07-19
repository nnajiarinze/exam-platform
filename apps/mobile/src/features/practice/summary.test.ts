import { sessionAccuracy } from './summary';
it('calculates the displayed session accuracy', () => { expect(sessionAccuracy(2, 3)).toBe(67); expect(sessionAccuracy(0, 0)).toBe(0); });
