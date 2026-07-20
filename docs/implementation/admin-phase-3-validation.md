# Admin Phase 3 validation

## Prerequisites

Start the Content Service and Admin Portal. Create an exam hierarchy with a
topic and create an active source. Use different development identities for the
author and reviewer checks.

## Endpoints and roles

- `/api/v1/admin/learning-objectives`: list and create
- `/api/v1/admin/learning-objectives/{id}`: retrieve, update, and `/archive`
- `/api/v1/admin/knowledge-facts`: search/list and create
- `/api/v1/admin/knowledge-facts/{id}`: retrieve and update
- `/api/v1/admin/knowledge-facts/{id}/versions`: immutable history
- Fact actions: `/submit`, `/approve`, `/reject`, `/require-update`, `/retire`

Authors create/edit drafts and submit. Reviewers approve, reject, require
updates, and retire. `ADMIN` has both permission sets, but an identity cannot
approve a fact version it authored.

## Manual end-to-end validation

1. Under **Knowledge Base → Learning objectives**, create an objective for an
   existing topic UUID.
2. Create a fact, select the objective and an active source, set validity dates,
   and save.
3. Search by statement and filter by review, lifecycle, validity, and publisher.
4. Submit as an author; verify `UNDER_REVIEW` and that editing is blocked.
5. Sign in as a different reviewer, approve, and verify `APPROVED / ACTIVE`.
6. Edit the approved fact as an author; verify a new `UNREVIEWED / DRAFT`
   version and preserved approved history.
7. Validate reject, require-update, stale conflict, retired-source rejection,
   invalid dates, and retirement.

Expected failures are structured `401`, `403`, `409`, or `422` responses and a
friendly portal error.

## Deferred and production blockers

Questions, answer options, publishing, releases, AI, and learner reports are
not implemented. Production identity, tamper-resistant audit storage, final
gateway/CORS policy, and a richer hierarchy picker remain deferred. Development
identity headers must never be enabled in production.
