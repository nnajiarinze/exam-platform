# Mobile Settings/Profile validation

The approved source was the uploaded `stitch_design_enhancement_pro (7)` screenshot, HTML export, and `DESIGN.md`. Stitch MCP was explicitly not required. The implementation continues the existing React Native theme and shared components.

## Local validation

1. Start Compose and confirm Keycloak, Mailpit, Learning, and Content health.
2. Build/recreate Learning so Flyway V11 is applied.
3. Run `ALLOW_DESTRUCTIVE_DEV_RESET=true DEMO_DATA_ENVIRONMENT=local ./scripts/dev-reset-and-seed-all.sh --confirm-reset`.
4. Start Expo and sign in as the demo learner.
5. Open Settings from Home and verify real name, email, verification, status, and build version.
6. Edit the display name, save, reopen, and confirm persistence. Email remains read-only.
7. Open Change Password and confirm the hosted Keycloak action opens.
8. Save valid and invalid daily/weekly goals. Complete Practice answers and confirm today's count and ISO-week study-day progress.
9. Enable a study reminder, grant permission, save a time, and inspect scheduled notifications. Change the time and confirm only one schedule remains. Disable and confirm cancellation.
10. Deny permission on a clean device, verify the denied state, and use Open device settings.
11. Log out and confirm the reminder, secure session, query cache, and learner store are cleared. Sign in as another learner and verify no previous settings appear.
12. Open Privacy and Legal; configured URLs open externally and absent URLs show a controlled unavailable message.
13. Test Delete Account using a disposable local account by typing `DELETE`; confirm anonymisation, logout, and reminder removal.
14. Recheck Practice, Mock Exam, Progress, navigation, and physical-device safe areas.

## Commands

Run Learning tests, mobile API generation, TypeScript, Jest, Expo Android export, and the guarded reset/seed command. Expo has no lint script in this repository.
