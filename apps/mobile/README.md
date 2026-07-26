# Mobile learner application

Expo/React Native client for practice, progress, and timed mock examinations.

Backend routing is centralized in `src/config/environment.ts`. Ordinary Expo development defaults to `LOCAL`, using the existing physical-device gateway; iOS simulator and Android emulator alternatives remain isolated there. Select `HOSTED` with an EAS profile or `EXPO_PUBLIC_APP_ENV=HOSTED`; it uses the temporary internal-testing gateway `http://46.224.221.7`.

Profiles `development-local`, `development-hosted`, and `preview-hosted` are internal builds. `production` deliberately fails while the hosted gateway is HTTP and must be changed to HTTPS before store publication. Android cleartext access remains limited to debug manifests; the main/release manifest is not weakened. iOS keeps `NSAllowsArbitraryLoads=false` and local networking support, so hosted HTTP may require an internal development build and is never accepted for production.

Configure the non-routing values described in `.env.example`. Run `npm start` for Expo, `npm test` for component tests, and `npm run generate:api` after changing the Learning Service OpenAPI contract.

Mock exams use the server-provided configuration and expiry timestamp. The Learning Service selects and pins the release, questions, question order, and option order. The app never calculates scores or displays correctness before submission. An active attempt ID is persisted locally for convenient resume; starting again is also server-idempotent and returns the learner's active attempt.

Full offline examinations are not supported. A failed answer save is displayed and must be retried; the server remains authoritative.
