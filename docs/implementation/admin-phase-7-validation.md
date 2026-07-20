# Admin Phase 7 validation

Build and start both services with separate databases and the same server-only `LEARNING_INTERNAL_API_KEY`.

1. Open `/reports` and verify all five health areas. Stop Learning Service and confirm learner health fails safely without breaking Content reports.
2. Create/update governed entities and inspect `/audit` filters plus previous/new JSON.
3. Attempt to update/delete `audit_event`; the database must reject it.
4. Approve/reject, publish/deliver/activate, and verify audit/report updates.
5. Complete/expire mock exams and verify learner aggregates contain no PII.
6. Exceed configured sensitive-operation limits and expect 429 plus `Retry-After`.
7. Verify report/audit authorization and internal report service authentication.
8. Keyboard-test navigation, filters, details, pagination, focus, loading, errors, and empty states.

Stitch was unavailable, so the UI reuses established Admin patterns without a visual redesign.
