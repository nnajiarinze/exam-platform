# Codex Implementation Prompt — Svea Study Mobile Redesign

You are continuing an existing React Native mobile application called **Svea Study** for Swedish citizenship-test learning and mock examinations.

The repository already contains working authentication, navigation, content/learning flows, practice questions, mixed practice, mock examinations, answer review, mock history, progress tracking, settings, localization choices, backend integrations, generated clients or API wrappers, and existing reusable UI patterns.

Your task is to migrate the current mobile UI to the consolidated Stitch design references contained in this handoff package without breaking working behavior.

## Reference package

Read these files before changing code:

1. `README.md`
2. `design-system/SVEA_STUDY_DESIGN_SYSTEM.md`
3. `docs/SCREEN_MAPPING.md`
4. Every `screens/*/reference.png`
5. The corresponding `screens/*/reference.html`

The PNG files are the visual target. The HTML files are browser-oriented reference implementations used only to understand layout, typography, spacing, colors, hierarchy, and component behavior. Do not copy browser-only implementation details into React Native.

## Core objective

Update the existing application so the major learner flows visually and behaviorally match the supplied design system and screen references:

- Home dashboard
- Study topics
- Practice question
- Mock exam setup
- Results summary
- Progress analytics

Preserve all existing functionality and data contracts. This is a front-end redesign and UX refinement, not a backend rewrite.

## Non-negotiable constraints

1. Inspect the repository before implementation. Determine:
   - React Native framework and version.
   - Expo or bare workflow.
   - Navigation library and route structure.
   - State-management approach.
   - Existing theme/token system.
   - Existing component library.
   - Localization implementation.
   - API clients and generated models.
   - Existing test setup.

2. Reuse existing architecture and patterns. Do not introduce a second navigation system, state manager, styling framework, or networking layer.

3. Do not rewrite working domain logic. Preserve:
   - Authentication and session handling.
   - Language selection and persisted preferences.
   - Continue-learning behavior.
   - Topic and lesson navigation.
   - Practice-session creation and answer submission.
   - Single-choice and multiple-choice questions.
   - Mock-exam timers, question navigation, flagging, submission, auto-submission, and history.
   - Progress recording and analytics.
   - Account/settings flows.

4. Do not invent backend fields. When a design contains data that is not currently available, use one of these approaches in order:
   - Derive it safely from existing response data.
   - Hide the module until the required data exists.
   - Use a clearly isolated deterministic presentation fallback for development only.
   - Document the missing backend capability in `docs/mobile-redesign-gaps.md`.

5. Do not hardcode the user's name, scores, dates, XP, streak, readiness, lessons, categories, or recommendations.

6. Do not copy Tailwind CDN, HTML, web CSS, Google Fonts links, or Material Symbols web fonts into React Native.

7. Maintain accessibility:
   - Minimum 44x44-point touch targets.
   - WCAG AA contrast.
   - Dynamic text support where the app already supports it.
   - Screen-reader labels and roles for controls, progress indicators, answer options, timers, and navigation.
   - Do not rely on color alone for selected, correct, incorrect, completed, or locked states.

8. Preserve Swedish characters and localization. Do not mix Swedish and English in one screen unless the selected interface/explanation-language settings explicitly require it.

## Implementation strategy

### Phase 1 — Repository audit

Before editing, produce a concise implementation map in the working notes:

- Current screen component -> target reference screen.
- Existing reusable components that can be retained.
- New shared components required.
- Existing data available for each design module.
- Data gaps.
- Routes and navigation implications.

Do not start by replacing entire screens blindly.

### Phase 2 — Design tokens

Create or extend one centralized mobile theme based on `SVEA_STUDY_DESIGN_SYSTEM.md`.

Include typed tokens for:

- Colors.
- Typography.
- Spacing based on an 8-point grid.
- Border radii.
- Shadows/elevation.
- Icon sizes.
- Minimum control heights.
- Screen gutters.

Use the existing theme mechanism if one exists. Avoid raw repeated hex values and arbitrary spacing inside screens.

Primary visual direction:

- Background: cool off-white.
- Cards: white with subtle navy-tinted ambient shadow or accessible border.
- Primary: deep navy/blue.
- Accent: achievement gold/yellow.
- Text: near-black navy.
- Secondary text: muted slate.
- Success: restrained green with accessible text.
- Error: accessible red.
- Card radius: approximately 16px.
- Inputs/buttons: approximately 12px radius.
- Main spacing rhythm: 8, 16, 24, 32, 48.

Use the project's current font when replacing it would be risky. Use Hanken Grotesk only if font loading is already supported or can be added cleanly without creating a fragile startup dependency. Never bundle or expose font files outside the app.

### Phase 3 — Shared components

Create or refactor reusable native components instead of duplicating markup:

- `AppScreen` or equivalent safe-area screen shell.
- `AppHeader`.
- `BottomTabBar` styling or existing tab customization.
- `Card`.
- `PrimaryButton`, `SecondaryButton`, and `TextButton`.
- `ProgressBar`.
- `CircularProgress` or readiness ring using an already installed SVG library.
- `StatPill` or stat tile.
- `SectionHeader`.
- `TopicCard`.
- `RecommendationCard`.
- `AchievementCard`.
- `AnswerOption` supporting selected/correct/incorrect/disabled states.
- `QuestionProgress`.
- `EmptyState`, `LoadingState`, and `ErrorState`.
- `Skeleton` only if the project already has an animation approach suitable for it.

Avoid adding dependencies when a small native implementation is sufficient. When a dependency is necessary, justify it in the final summary.

### Phase 4 — Screen migration

#### A. Home dashboard

Implement the visual hierarchy from `screens/01-home-dashboard`.

Required behavior:

- Personalized time-aware greeting using the current user profile.
- Current level and total XP only when those concepts exist in the domain. Otherwise map existing progress into honest labels or hide these fields.
- Exam-readiness ring derived from an existing readiness value or a documented deterministic calculation.
- Streak chip.
- Primary `Continue Learning` action.
- Today's goal progress.
- `Practice Weak Topics` action based on existing weak-topic data or lowest-performing subject.
- `Mock Exam` action.
- Recommended lesson card using real curriculum/progress data.
- Achievement progress based on real events or hide until implemented.

Do not turn the home screen into an endless feed. Keep the primary action visible without scrolling on common phone sizes where possible.

#### B. Study topics

Implement `screens/02-study-topics`.

Required behavior:

- Search input that filters current topics locally unless server search already exists.
- Recommended next lesson based on incomplete/current content.
- Curriculum-topic cards showing title, mastery/progress, last-studied metadata when available, and estimated time when available.
- Cards are fully tappable.
- Locked states must explain the unlock condition and must not trap accessibility focus.
- Preserve existing route to topic/lesson detail.
- Weekly progress and streak summary may be included only with real or safely derived data.

#### C. Practice question

Implement `screens/03-practice-question` while preserving practice-domain behavior.

Required behavior:

- Compact progress indicator.
- Question type label where useful.
- Large readable question text.
- Fully accessible answer cards.
- Correct multi-select behavior for multiple-choice questions.
- Submission disabled until the answer meets the question requirements.
- Correct/incorrect feedback only in practice mode, never during mock exam mode.
- Explanation content after submission.
- Sticky primary next/continue action that respects safe-area insets and keyboard behavior.
- Preserve existing session completion and progress recording.

#### D. Mock exam setup

Implement `screens/04-mock-exam-setup`.

Required behavior:

- Real exam title, description, question count, duration, pass threshold, and rules.
- Previous best/recent attempt only when available.
- Readiness indication only when supported.
- Clear start/resume logic.
- Prevent accidental creation of duplicate active attempts.

#### E. Results summary

Implement `screens/05-results-summary`.

Required behavior:

- Pass/fail outcome.
- Score, percentage, pass threshold, answered/unanswered counts, time spent, and submission reason.
- Subject breakdown.
- Missed or incorrect answer review.
- Clear recommended next action based on weakest subject.
- Review answers, retake, history, and home routes.
- Celebratory animation must be subtle, optional, and respect reduced-motion preferences.

#### F. Progress analytics

Implement `screens/06-progress-analytics` as the target. Ignore the superseded screen except for traceability.

Required behavior:

- Exam readiness or equivalent high-level status.
- Accuracy and question volume.
- Practice/mock trend using available attempt data.
- Strongest and weakest categories.
- Study activity visualization when sufficient dated data exists.
- Actionable recommended next topic.
- Replace the current raw vertical list with summarized analytics plus expandable details when needed.

### Phase 5 — Existing screens not represented in Stitch

Do not leave the rest of the app visually inconsistent.

Apply the same tokens and components incrementally to:

- Onboarding/language selection.
- Sign in/sign up/reset password.
- Lesson/detail screens.
- Mixed practice setup.
- Mock exam in-progress question navigation.
- Mock history.
- Settings/profile/account pages.
- Loading, empty, offline, and error states.

Do not redesign their domain behavior. Align typography, surfaces, buttons, inputs, card radius, spacing, and navigation chrome with the new system.

### Phase 6 — Motion and polish

Use restrained native animation only where it improves comprehension:

- Progress bars and readiness ring animate from previous value.
- Answer-option state transition.
- Button press feedback.
- Small success/achievement feedback.
- Screen transitions remain consistent with the existing navigator.

Respect operating-system reduced-motion settings. Avoid confetti packages unless already installed and justified.

### Phase 7 — Tests and verification

Update or add tests for:

- Navigation from all dashboard actions.
- Topic search and topic selection.
- Practice single-choice submission.
- Practice multiple-choice submission.
- Disabled/enabled submission states.
- Explanation shown only after practice submission.
- Mock-exam setup start/resume behavior.
- Mock-exam mode never showing correctness before submission.
- Results actions.
- Progress empty/loading/error/data states.
- Localization and long Swedish text wrapping.

Run:

- Type checking.
- Linting.
- Unit/component tests.
- Existing integration tests.
- iOS and Android builds or the closest project-provided verification commands.

Check at minimum:

- Small iPhone-sized viewport.
- Large iPhone-sized viewport.
- Common Android viewport.
- Text scaling at 100% and at least one enlarged setting.
- Light mode. Do not add dark mode unless it already exists.

## Data-model guidance

### Exam readiness

Do not present a fabricated probability of passing. If the backend lacks readiness, create a clearly named client-side score such as `learningReadinessScore`, document the formula, and label it as learning progress rather than predictive pass probability.

A safe deterministic score may combine normalized values already available, such as:

- Curriculum completion.
- Practice accuracy with a minimum sample threshold.
- Recent mock-exam performance.
- Coverage across subjects.

The formula must:

- Be deterministic.
- Be unit tested.
- Handle no-data and low-data cases.
- Avoid claiming statistical prediction.
- Be documented in `docs/mobile-redesign-gaps.md`.

### XP and levels

Reuse existing domain concepts if present. If absent, do not silently introduce permanent fake progression. Either hide the fields or implement a small isolated local progression model only after documenting storage, migration, reset, and synchronization behavior.

### Recommendations

Prefer deterministic recommendations:

1. Continue an in-progress lesson.
2. Review the weakest sufficiently sampled topic.
3. Start the next incomplete curriculum topic.
4. Take a mock exam when coverage and recent accuracy meet a defined threshold.

Unit test recommendation ordering.

## Code quality requirements

- TypeScript strictness must not regress.
- No `any` added without explicit justification.
- Keep domain calculations outside presentational components.
- Memoize only where measured or structurally appropriate.
- Use `FlatList`/`SectionList` for long collections.
- Avoid nested unbounded `ScrollView`s.
- Handle safe areas and bottom tabs correctly.
- Keep screen components thin; extract hooks/view-models for orchestration.
- Use stable test IDs for critical flows.
- Remove obsolete styles/components only after proving they are unused.

## Deliverables

1. Updated React Native implementation matching the design references.
2. Centralized typed design tokens/theme.
3. Shared reusable UI components.
4. Updated tests.
5. `docs/mobile-redesign-gaps.md` containing:
   - Missing backend fields.
   - Any derived metrics and formulas.
   - Features hidden because data is unavailable.
   - Deferred screens or interactions.
6. `docs/mobile-redesign-summary.md` containing:
   - Files changed.
   - Architecture decisions.
   - Dependencies added or removed.
   - Commands run and results.
   - Known limitations.

## Completion criteria

The task is complete only when:

- Existing learner flows still work end to end.
- The six target screens closely match the supplied visual references.
- No screen depends on fake hardcoded learner data.
- Accessibility and safe-area behavior are intact.
- Existing tests pass and new critical-flow tests are included.
- The app has a coherent design language across represented and adjacent screens.
- The final summary explicitly distinguishes completed work from deferred data-dependent features.
