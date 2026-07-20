import { formatCountdown } from './countdown';
it('formats the server-derived countdown', () => expect(formatCountdown(3661)).toBe('01:01:01'));
