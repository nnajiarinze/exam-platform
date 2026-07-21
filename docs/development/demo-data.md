# Deterministic development demonstration data

This dataset demonstrates the complete editorial-to-learner flow for Swedish
citizenship preparation. **This is demonstration preparation content and not an
official Swedish citizenship examination.** It is not affiliated with a public
authority and does not guarantee success in any future examination.

## Safety and commands

The reset commands are destructive and restricted to explicit local,
development, test, or seed environments. They fail closed unless both the guard
and confirmation are present, reject production profiles and protected/unknown
database hosts, and print both database targets before deleting anything.

```bash
export ALLOW_DESTRUCTIVE_DEV_RESET=true
export DEMO_DATA_ENVIRONMENT=local
./scripts/dev-reset-and-seed-all.sh --confirm-reset
```

Owned-service commands are also available:

```bash
./scripts/dev-reset-and-seed-content.sh --confirm-reset
./scripts/dev-reset-and-seed-learning.sh --confirm-reset
python3 scripts/demo_data.py validate
```

The full command resets Learning runtime data first, resets and seeds Content,
delivers the immutable current release through the real Content-to-Learning HTTP
flow, activates it through the normal APIs, creates a local mock blueprint, and
prints validation counts. The Learning-only command resets data but does not
import content; normally use the full command.

## Dataset

- One exam and two versions: historical `2026.1 Demo` and current `2026.2 Demo`
- Five subjects, 20 topics, 40 learning objectives, and 20 institutional sources
- 100 atomic knowledge facts and 100 questions
- 60 single-choice, 20 true/false, and 20 multiple-choice questions
- Approved, under-review, requires-update, unreviewed, active, draft, and retired examples
- Reviewer history using separate author and reviewer identities
- Historical retired, current active, and next draft releases
- A 20-question local mock blueprint; its numbers are demonstration settings, not official rules

The current release contains 96 approved active questions. Stable UUIDv5 IDs,
fixed option ordering, and a fixed timeline make the checksum repeatable.

## Local identities

- `demo-content-author` — `CONTENT_AUTHOR`
- `demo-content-reviewer` — `CONTENT_REVIEWER`
- `demo-content-publisher` — `CONTENT_PUBLISHER`
- Existing `dev-content-admin` — `ADMIN`

No production credential or internal service secret is stored in the dataset.

## Inspecting and changing the result

In Admin, follow Exam Structure through subjects, topics, objectives, facts,
questions, review history, and Releases. Sources use institutional landing pages
and a fixed sample access date; editors must reverify them before production.

In mobile, use exam `swedish-citizenship`, start practice, and start the local
demonstration mock. All three question types are included in the active release.

Edit the curated `SUBJECTS` definitions in `scripts/demo_data.py`. Keep codes
stable to preserve UUIDs. Each topic must retain five atomic facts and three
unambiguously false distractors. Verify with:

```bash
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
python3 scripts/demo_data.py validate
```

If a run partially fails, correct the reported issue and rerun the full command.
The process is deterministic and idempotent. Flyway history, database users,
extensions, and infrastructure configuration are never deleted.
