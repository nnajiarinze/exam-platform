# Authentication end-to-end validation

1. Start Keycloak and all local infrastructure.
2. Rebuild and start Content and Learning Services.
3. Start Admin Portal with development authentication disabled.
4. Start mobile with LAN-accessible OIDC and Learning URLs.
5. Run the guarded deterministic reset-and-seed workflow.
6. Register a learner and complete provider-managed email verification.
7. Sign in, complete onboarding, and confirm `/api/v1/me` created one profile.
8. Complete Practice items of all three question types and inspect progress.
9. Start a Mock Exam, save answers, restart mobile, and resume it.
10. Log out and confirm protected requests return 401 and cached data is gone.
11. Sign in as a second learner and confirm the first learner's sessions are inaccessible.
12. Test access-token expiry, one refresh, rotated refresh persistence, and forced logout after failed refresh.
13. Exercise neutral forgotten-password response, expired/used links and return to login.
14. Delete the learner account and confirm it is anonymised and subsequently forbidden.
15. Sign in to Admin as author, reviewer, publisher, and administrator; verify navigation and server permissions.
16. Confirm a learner token is rejected by Content Admin and internal import endpoints.
17. Publish, deliver and activate a release; confirm the internal service credential still succeeds.
18. Restart services and confirm profile ownership and release state persist.
19. Run backend, Admin, mobile, generation, export, and deterministic seed tests.
20. Visually compare auth states with existing cards, typography, spacing, focus/touch targets, alerts and responsive shells.

The existing mobile and Admin implementations were the visual source of truth.
Stitch MCP was explicitly not required for this phase.
