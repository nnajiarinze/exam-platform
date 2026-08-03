# Screen Mapping

## 01 — Home Dashboard

Reference: `screens/01-home-dashboard`

Purpose:
- Primary landing screen after authentication.
- Show greeting, current level, XP, exam readiness, streak, daily goal, recommended actions, recommended lesson, and achievement progress.

Expected existing integrations:
- User profile/name.
- Learning progress and question statistics.
- Continue-learning destination.
- Weak-topic practice.
- Mock-exam entry.
- Recommendation data or a deterministic client-side fallback.

## 02 — Study Topics

Reference: `screens/02-study-topics`

Purpose:
- Search and browse curriculum topics.
- Highlight recommended next lesson.
- Show mastery, last-studied date, estimated remaining duration, locked modules, weekly activity, streak, and mastered-topic count.

Expected existing integrations:
- Curriculum subjects/topics.
- Lesson progress.
- Continue/start lesson navigation.
- Search/filtering.

## 03 — Practice Question

Reference: `screens/03-practice-question`

Purpose:
- Present one practice question with clear answer selection, progress, feedback, explanation, and next action.

Expected existing integrations:
- Existing practice session and question-answer APIs.
- Single-choice and multiple-choice behavior.
- Answer submission and explanation rendering.
- Progress recording.

## 04 — Mock Exam Setup

Reference: `screens/04-mock-exam-setup`

Purpose:
- Prepare the learner before starting an exam.
- Show duration, question count, pass threshold, previous performance/readiness, and exam rules.

Expected existing integrations:
- Mock-exam definition.
- Existing attempt/history data.
- Start/resume behavior.

## 05 — Results Summary

Reference: `screens/05-results-summary`

Purpose:
- Summarize pass/fail, score, readiness change, subject performance, missed answers, recommendations, and next actions.

Expected existing integrations:
- Submitted attempt result.
- Subject breakdown.
- Review answers.
- Retake exam.
- History and home navigation.

## 06 — Progress Analytics

Reference: `screens/06-progress-analytics`

Purpose:
- Give a useful progress overview rather than a raw list.
- Show readiness, accuracy, questions answered, streak, study activity, strong/weak topics, and actionable next step.

Expected existing integrations:
- Existing progress endpoint(s).
- Practice and mock-exam history.
- Client-side aggregation when the backend already returns sufficient raw data.

## Superseded Reference

`screens/99-progress-analytics-original-superseded` is intentionally not the implementation target.
