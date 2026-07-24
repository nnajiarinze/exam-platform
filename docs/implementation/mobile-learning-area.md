# Mobile learning area

## Scope and information architecture

The learner flow is `Study → Subject → Topic → Lesson → related topic practice`. It is a short,
exam-focused reading experience, not a course or textbook system. The existing practice and mock
exam engines remain unchanged.

## Ownership and lesson projection

Content Service remains authoritative. Publishing release snapshot schema `1.2` projects only
Knowledge Fact versions explicitly selected into the published release. Each projected lesson
section contains the learning-objective title, canonical statement, release display order, and
active source links with public URLs. Draft facts, AI provenance, checksums, lifecycle fields, and
internal identifiers are not exposed through the mobile API.

Learning Service imports those sections alongside the existing question projection. Lesson and
practice content therefore use the same active `imported_content_release`. Reading time is
calculated in Learning Service at 200 words per minute with a 15-second minimum.

## APIs

- `GET /api/v1/learning/exams/{examId}/subjects`
- `GET /api/v1/learning/subjects/{subjectId}/topics?examId=…`
- `GET /api/v1/learning/topics/{topicId}/lesson?examId=…`
- `PUT /api/v1/learning/topics/{topicId}/progress?examId=…`
- `GET /api/v1/learning/continue?examId=…`

All routes use normal learner authentication. Topic practice continues through
`POST /api/v1/practice-sessions`.

## Progress and versioning

Progress is keyed by learner, imported content release, and imported topic. Updates are idempotent:
completed section count is monotonic, while the last viewed section remains the resume position.
Only completing the final section sets `completed_at`. A new active release starts a separate
progress record; historical records remain intact and cannot incorrectly mark newly added sections
complete.

## Mobile behaviour, caching, and accessibility

TanStack Query owns server state and retains recently loaded lesson responses in its in-memory
cache during normal navigation. Progress mutations retry transient failures twice and are
idempotent server-side. This phase does not add persistent offline synchronization.

The UI reuses the current cards, buttons, typography, spacing, progress indicators, safe-area
screen, error states, and bottom navigation. Headings, progress text, source links, and navigation
buttons have semantic accessibility roles or labels. Progress is always expressed in text as well
as visually.

## Related practice and incorrect answers

Lesson screens open the existing topic-practice setup with the published topic identifier. The
current practice answer response does not reliably expose a topic identifier to the question
screen, so an incorrect-answer lesson link is intentionally deferred rather than guessing a
mapping.

## Tests and manual verification

Backend integration coverage verifies route registration, published section ordering, release
identity, related-question counts, idempotent progress, and Continue Learning. Mobile component
coverage verifies topic metadata, optional reading time, accessibility navigation, learner-facing
section rendering, and absence of administrative metadata.

Manual flow: publish and deliver a release containing Knowledge Fact and Question items; sign in;
open Study; select a subject and topic; advance, leave, and resume; complete the final section;
start topic practice; restart the app and confirm backend progress remains. Also verify a topic
without fact items is absent, unavailable services show retry UI, and large system text leaves
actions reachable.

## Known limitations

- No persistent offline lesson cache or offline progress queue.
- No incorrect-answer deep link until the practice response contract exposes reliable topic/fact
  context.
- The minimal section uses existing learning-objective titles and canonical Knowledge Fact text;
  examples, tips, and common mistakes require deliberate future authoring fields.
