# Mobile learner application

Expo/React Native client for practice, progress, and timed mock examinations.

Backend routing is centralized in `src/config/environment.ts`. `CurrentEnvironment` defaults to `Environment.REMOTE`; change that single value to `Environment.LOCAL` to use the existing physical-device development gateway. The same file contains iOS simulator and Android emulator alternatives if the local network setup changes. No URL environment variables are required before scanning the Expo QR code.

Configure the non-routing values described in `.env.example`. Run `npm start` for Expo, `npm test` for component tests, and `npm run generate:api` after changing the Learning Service OpenAPI contract.

Mock exams use the server-provided configuration and expiry timestamp. The Learning Service selects and pins the release, questions, question order, and option order. The app never calculates scores or displays correctness before submission. An active attempt ID is persisted locally for convenient resume; starting again is also server-idempotent and returns the learner's active attempt.

Full offline examinations are not supported. A failed answer save is displayed and must be retried; the server remains authoritative.
