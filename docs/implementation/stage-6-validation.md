# Stage 6 validation

Stage 6 delivers the first Expo learner client for the existing Learning Service practice flow. The application targets Expo SDK 54 so it can run in the current App Store version of Expo Go on a physical iOS device during the SDK 57 transition period.

## Configuration

Copy `apps/mobile/.env.example` to `apps/mobile/.env` and set:

- `EXPO_PUBLIC_LEARNING_BASE_URL` to the Learning Service URL reachable by the device or simulator. `localhost` works for iOS Simulator; a physical device normally needs the host machine's LAN address.
- `EXPO_PUBLIC_LEARNER_IDENTITY` to an existing development learner identity, such as `dev-learner-001`.
- `EXPO_PUBLIC_EXAM_ID` to `swedish-citizenship` for the current content.

The client sends the configured identity in the existing `X-Learner-Identity` header. This is development authentication, not a production identity implementation.

## Automated validation

From `apps/mobile`:

```bash
npm run generate:api
npm run typecheck
npm test
npm run build
```

From the repository root:

```bash
./gradlew :services:learning-service:test :services:learning-service:build
```

The mobile tests cover startup navigation, persisted practice state, summary calculation, question/answer feedback rendering, topic selection, and progress rendering. The Learning Service integration test proves that subjects and topics are read from the active imported projection.

## Manual validation

1. Start PostgreSQL and the Learning Service, import a published content snapshot, and ensure the configured development learner exists.
2. Rebuild the Learning Service after this stage because it adds `GET /api/v1/content/subjects`.
3. In `apps/mobile`, run `npm start` and open the app in Expo Go, an emulator, or a simulator.
4. Complete onboarding and confirm the language choices survive an app restart.
5. Start topic practice, choose a question count supported by the imported content, and answer every question.
6. Confirm no correctness appears before submission, the server result and explanation appear after submission, and answer options become read-only.
7. Confirm the completion screen reports total, correct answers, and accuracy, then finish back to Home.
8. Open Progress and verify the practised topic, accuracy, answer count, and date.
9. Stop the Learning Service and confirm a friendly retryable error is displayed instead of an application crash.

## Known limitations

- Production authentication is not part of Stage 6; the app uses the Learning Service's development identity header.
- Interface strings are currently English. The selected interface and explanation languages are persisted, but the Learning Service contract does not yet accept an explanation-language preference and the imported snapshot currently exposes one explanation.
- The backend session resource does not expose a correct-answer total. The completion screen therefore aggregates the backend's answer results for the current locally persisted session.
- No offline synchronization, tablet-specific layout, analytics, premium capability, readiness prediction, or other explicitly excluded feature is included.
