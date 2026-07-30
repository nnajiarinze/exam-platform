# Mobile Redesign Gaps

This document records Stitch modules that cannot be backed honestly by the current Learning Service contracts. The mobile app does not display fabricated learner data.

## Derived metric: learning readiness

The redesign uses the label **Learning readiness**, not “probability of passing” or an official exam prediction.

The deterministic score is implemented in `apps/mobile/src/features/progress/analytics.ts`:

- curriculum completion: completed published topics / published topics, weighted 35%;
- practice accuracy: correct answers / answered questions, weighted 35%, included only after at least five answers;
- recent mock performance: mean percentage of up to three submitted mocks, weighted 30%.

Available component weights are normalized. At least two components must exist; otherwise the UI shows that more activity is required. Topic ranking requires at least three answers in that topic. The formula is unit tested and does not claim statistical calibration.

## Missing backend capabilities

| Design concept                        | Current capability                                                                                 | Redesign behavior                                                                        |
| ------------------------------------- | -------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| XP and learner levels                 | No XP/level fields or synchronization model                                                        | Hidden. Real question counts, accuracy and curriculum completion replace them.           |
| Streak / consecutive study days       | Weekly study-day count exists, but no dated activity or consecutive-day streak                     | Streak chips and claims are hidden.                                                      |
| Achievement badges                    | Notification preference exists, but no achievement events or badge state                           | Achievement cards are hidden.                                                            |
| 30-day activity heatmap               | No dated study/activity series                                                                     | Hidden; no random or decorative activity is rendered.                                    |
| Weekly activity chart                 | Only aggregate `studyDaysThisWeek` is available                                                    | Hidden because per-day values cannot be derived.                                         |
| Lesson/topic imagery                  | No image asset or media URL in curriculum contracts                                                | Tonal native cards are used; Stitch remote images are not copied.                        |
| Last-studied date in curriculum cards | Lesson progress has last-accessed data only on the lesson-detail response, not the topic list      | Topic list uses completion state and real reading time instead.                          |
| Locked curriculum modules             | No lock flag or unlock rule                                                                        | No fake locked topics are shown. All published topics remain reachable.                  |
| Topic difficulty                      | No difficulty field                                                                                | Hidden.                                                                                  |
| Formal readiness / pass likelihood    | No calibrated readiness endpoint                                                                   | Uses the documented learning-readiness score above.                                      |
| Practice question timer               | Practice sessions expose no remaining time                                                         | Hidden. Mock examination timers remain unchanged.                                        |
| Previous practice trend               | Practice history endpoint is absent                                                                | Mock trend is shown only from real mock history.                                         |
| Results sharing                       | No product/privacy decision or prepared share payload                                              | Not added.                                                                               |
| Retake exact prior mock               | Creation API starts/resumes according to backend rules but has no “clone attempt” operation        | “Retake” returns to mock setup and uses normal creation semantics.                       |
| Localization catalog                  | Interface language preference is persisted, but UI strings are not backed by a translation catalog | Existing language behavior is preserved; full string localization remains separate work. |

## Intentionally deferred

- Dark mode was not added because the current application has no dark-theme mechanism. The redesigned light theme preserves existing behavior.
- Hanken Grotesk was not bundled because the handoff includes no licensed font assets and the app had no custom-font loading path. Native system fonts use the specified scale, weights and line heights.
- No confetti, haptics or third-party animation package was added. Progress rings use restrained native animation and respect the operating-system reduced-motion setting.
- The superseded progress reference in `99-progress-analytics-original-superseded` was reviewed only for traceability; `06-progress-analytics` is the implementation target.
