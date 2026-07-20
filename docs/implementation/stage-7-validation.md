# Stage 7 validation

Stage 7 adds configurable, release-pinned mock examinations to the Learning Service and mobile application. Blueprint values are configuration, not claims about an official Swedish citizenship examination.

## Local blueprint

After Flyway migration V4 and the Stage 6 demonstration content release are present, configure the explicitly non-production blueprint with:

```bash
docker compose exec -T learning-database \
  psql -U learning -d learning < scripts/seed-local-mock-blueprint.sql
```

The local example selects two Democracy questions and eight Sweden Basics questions. It uses a 15-minute duration and 70% threshold solely to exercise configurable behavior.

## Automated validation

```bash
./gradlew clean check build
npx -y @redocly/cli lint contracts/openapi/learning-service-v1.yaml
cd apps/mobile
npm run generate:api
npm run typecheck
npm test
npm run build
npx expo-doctor
```

## Manual flow

1. Build and restart the Learning Service so Flyway applies V4.
2. Apply the local blueprint script above.
3. Start Expo and open **Mock examination** from Home.
4. Start an attempt and confirm the timer decreases.
5. Answer questions, move previous/next, jump using the navigator, and flag a question.
6. Confirm submission and verify score, percentage, configured pass/fail result, topic breakdown, incorrect answers, correct answers, explanations, time spent, and attempt date.
7. Open history and reopen the result.
8. Start another attempt to verify retakes generate a new fixed question set.

## Timing behavior

The server derives remaining time from `startedAt` and the snapshotted blueprint duration. Every attempt request enforces expiry, and a transactional scheduled scan finalizes inactive clients. The mobile countdown is display-only and is reset from server responses.

## Limitations

- Blueprint administration UI/API is intentionally deferred; local development uses the SQL fixture and production configuration requires a later controlled administrative workflow.
- No official question count, duration, or pass threshold is asserted.
- Mobile connectivity loss is reported normally; offline answer synchronization is not implemented.
- The timer is server-authoritative. A backgrounded or restarted client retrieves the server-calculated remaining time rather than maintaining a standalone local timer.
