# Admin Phase 4 validation

## Scope

Phase 4 adds question management without publishing or release creation. Questions are authored against one learning objective and one or more approved, active knowledge facts. Links point to the exact approved fact version used during authoring.

## Implemented workflow

- Authors create and edit draft questions, configure two to six options, preview them, and submit them for review.
- Single-choice questions require exactly one correct answer; multiple-choice questions require at least one.
- True/false questions generate immutable `True` and `False` options from the selected correct value.
- Reviewers approve, reject, require updates, or retire questions. An author cannot approve their own question.
- Editing an approved question creates a new draft version while preserving the approved version and its fact links.
- The list and `/questions/search` endpoint search question text, code, and linked fact statements and support pagination and filters.

## Automated validation

Run from the repository root:

```bash
./gradlew :services:content-service:test :services:content-service:build
cd apps/admin
npm run generate:api
npm run lint
npm run typecheck
npm test
npm run build
```

Backend integration coverage proves the endpoint is registered and exercises creation, exact approved-fact linkage, search, submission, author/reviewer separation, approval, and creation of a draft version from an approved question. Unit tests cover option and type validation. Admin component tests cover the generated-client list route and editor composition.

## Manual validation

1. Sign in as the development content administrator and open **Questions**.
2. Create a question under an active learning objective that has at least one approved active fact.
3. Verify invalid correct-answer combinations are rejected and true/false options are generated automatically.
4. Submit the saved question.
5. Sign out and sign in as the development content reviewer.
6. Open the question, review its facts/options/preview, and approve it.
7. Sign back in as the author, edit the approved question, and verify a new draft appears while version history retains the approved version.

## Limitations

- Publishing and release assembly remain Phase 6 concerns.
- Translation, media, bulk import, and AI generation are intentionally excluded.
- Stitch was required by repository guidance for new UI design but was unavailable in the implementation environment; the UI reuses the established Phase 3 forms, tables, cards, and actions.
