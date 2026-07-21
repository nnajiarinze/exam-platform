# Mobile learner application

Expo/React Native client for practice, progress, and timed mock examinations.

Configure `EXPO_PUBLIC_API_BASE_URL`, `EXPO_PUBLIC_EXAM_ID`, and the OIDC values described in `.env.example`. Hosted builds derive Learning and authentication routes from one HTTPS gateway. `EXPO_PUBLIC_LEARNING_BASE_URL` remains a local-only compatibility override. Run `npm start` for Expo, `npm test` for component tests, and `npm run generate:api` after changing the Learning Service OpenAPI contract.

Mock exams use the server-provided configuration and expiry timestamp. The Learning Service selects and pins the release, questions, question order, and option order. The app never calculates scores or displays correctness before submission. An active attempt ID is persisted locally for convenient resume; starting again is also server-idempotent and returns the learner's active attempt.

Full offline examinations are not supported. A failed answer save is displayed and must be retried; the server remains authoritative.
