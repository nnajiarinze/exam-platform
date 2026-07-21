# Demonstration data validation

1. Start local infrastructure with `docker compose up -d --build`.
2. Export the two safety variables documented in `demo-data.md`.
3. Run `./scripts/dev-reset-and-seed-all.sh --confirm-reset`.
4. Log in as `demo-content-author` with `CONTENT_AUTHOR`.
5. Inspect the exam hierarchy, both versions, all subjects, and sampled topics.
6. Inspect objectives, institutional sources, facts, and their relationships.
7. Inspect approved, draft, under-review, requires-update, and retired content.
8. Inspect single-choice, true/false, and multiple-choice questions.
9. Confirm explanations, option ordering, fact links, and source links.
10. Confirm review history uses a reviewer different from the author.
11. Log in as `demo-content-reviewer` with `CONTENT_REVIEWER`.
12. Inspect submitted and requires-update work.
13. Inspect retired historical, current active, and next draft releases.
14. Open mobile and start mixed practice.
15. Confirm all three question types appear and complete a practice session.
16. Start the demonstration mock, submit it, and review results.
17. Rerun the full reset command.
18. Confirm counts are unchanged, one release is active, and no duplicates exist.
19. Run service tests and the Python tests documented above.

The sample must always be presented as preparation material, never as official
government examination content.
