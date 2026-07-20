# Backup, recovery, and incidents

Use encrypted managed PostgreSQL daily snapshots plus continuous WAL/PITR, cross-account copies, documented retention, and quarterly restore drills. Content and Learning databases are restored independently. Define and test RPO/RTO before launch.

Audit data must also be exported to tamper-resistant storage before production because database superusers can bypass application immutability. Restrict and audit superuser access.

Restore into isolation, validate Flyway history, start the matching artifact without traffic, verify constraints/counts/checksums/active releases/audit continuity/historical sessions, then reopen traffic. Reconcile service state through APIs, never cross-service SQL. Content snapshots recover releases; only Learning backups recover practice and mock-exam history.

For incidents, classify security/privacy exposure, data loss, learner outage, editorial outage, or degraded reporting; assign an incident commander; preserve request IDs and sanitized evidence; communicate status; and record corrective owners and dates.
