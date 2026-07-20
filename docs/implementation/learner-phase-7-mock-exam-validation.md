# Learner Phase 7 mock-exam validation

## Runtime model

Mock exams and practice sessions are separate Learning Service runtimes. Practice gives immediate feedback; mock exams reveal correctness only after finalization. Creation resolves the current active release once, then persists the release, selected questions, question order, option order, configuration values, start time, and expiry. Later activation changes do not alter an existing attempt.

The initial concurrency policy is one `ACTIVE` attempt per learner and exam. Repeated start returns that attempt. Manual submission is idempotent. Server-side lazy expiry and the existing periodic scanner both finalize an expired attempt. `SINGLE_CHOICE` and `TRUE_FALSE` are eligible; other types are excluded explicitly. Topic allocations in the active database configuration determine distribution, and the generator rejects shortages without duplicating questions.

## Local validation

1. Start PostgreSQL and the Learning Service with `docker compose up -d learning-db learning-service`, or run `./gradlew :services:learning-service:bootRun` with its environment configured.
2. Publish and deliver a Content Service release, then activate its imported release in Learning Service. The release must contain enough approved, active questions for every configured topic allocation.
3. Ensure `mock_exam_blueprint` has one active row for exam `swedish-citizenship`; for expiry testing temporarily set a short `duration_minutes`. Configuration values are product defaults, not official government rules.
4. Set the mobile `EXPO_PUBLIC_LEARNING_BASE_URL` to the machine-reachable Learning Service URL, set `EXPO_PUBLIC_EXAM_ID=swedish-citizenship`, and use development identity `dev-learner-001` when development learner authentication is enabled.
5. Run `cd apps/mobile && npm start`. Open Mock examination. Verify name, question count, duration, and threshold load from the service.
6. Start, answer, change an answer, flag, jump using the numbered navigator, and move backward/forward. No correctness or explanation may appear.
7. Close and reopen Expo. Resume and verify question/option order, selections, flags, and expiry are unchanged.
8. Submit. Confirm correct/incorrect/unanswered counts, pass threshold, subject/topic results, and answer review. Submit the same attempt by API again; the same result must return.
9. For expiry, use a short configuration, wait past `expiresAt`, and try to answer. Expect `409 MOCK_EXAM_NOT_ACTIVE`; fetching results returns the auto-submitted result.
10. Activate a second release while an attempt is active. The old attempt retains its `releaseId`. After finalizing it, a new attempt uses the new release.
11. Restart Learning Service and resume an active attempt to verify persistence.
12. Request an attempt/result as another development learner. Expect `404 MOCK_EXAM_NOT_FOUND`, with no data disclosure.
13. Configure more questions than eligible content. Expect controlled `422` from the existing generator. Unsupported question types are not selected.

## Known limitations

History uses the existing unpaginated `/api/v1/mock-exams/history` convention. Multiple-choice, full offline mode, abandonment, production rate limiting, difficulty quotas, and metrics are not implemented. Stitch was unavailable, so new mobile UI follows the existing minimal component and accessibility conventions.
