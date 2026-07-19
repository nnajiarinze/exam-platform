# Core user journeys

Regulatory exam details are unresolved; journeys must use configurable product rules rather than assumed official values.

## First-time onboarding

- **Entry point:** First mobile launch.
- **Main steps:** Choose interface and explanation languages; review independence/readiness notices; create or continue without an account where supported; select study goal; enter practice.
- **Expected result:** Preferences are saved and a clear first activity is offered.
- **Failure cases:** Unsupported language, account conflict, unavailable network, or consent/preferences not saved; retain local choices and offer retry.

## Topic-based practice

- **Entry point:** Home, topic list, weak-topic recommendation, or saved activity.
- **Main steps:** Choose topic and session size; Learning Service selects from its published projection; begin session.
- **Expected result:** A session is tied to one immutable content release.
- **Failure cases:** No eligible questions, stale/missing projection, entitlement restriction, or offline content unavailable; explain the state without silently changing scope.

## Answering a question

- **Entry point:** Active practice question.
- **Main steps:** Read localized prompt; select an option; submit once; server evaluates against the pinned question version; record response.
- **Expected result:** Correctness and next action are shown without changing the submitted answer.
- **Failure cases:** Duplicate submission, timeout, connectivity loss, retired-but-pinned content, or malformed question; use idempotency and preserve recoverable state.

## Reviewing an explanation

- **Entry point:** Submitted answer or review list.
- **Main steps:** View reviewed explanation, correct option, related fact, and available source attribution; optionally request an allowed personalized explanation.
- **Expected result:** Learner understands why the answer is correct and can report concerns.
- **Failure cases:** Translation missing, AI unavailable, or source link unavailable; fall back to approved default-language content and never fabricate.

## Reviewing incorrect answers

- **Entry point:** Progress view or completed session.
- **Main steps:** Filter incorrect responses; reopen version-pinned question/explanation; bookmark or practise related topic.
- **Expected result:** Historical context is preserved and the next study action is clear.
- **Failure cases:** Content retired from new sessions or translation later removed; retain the historical version where policy permits and show status.

## Mock examination

- **Entry point:** Mock tab or recommendation.
- **Main steps:** Review configured blueprint and timing; confirm start; pin release/questions; answer and navigate; submit or auto-finish; score deterministically.
- **Expected result:** Attempt, responses, score, and topic breakdown are saved against exact versions.
- **Failure cases:** Start fails before pinning, network interruption, timer/client clock disagreement, duplicate finish, or import inconsistency; server time and idempotent completion are authoritative.

## Progress and weak topics

- **Entry point:** Progress dashboard.
- **Main steps:** View activity, topic mastery, evidence window, weak topics, and readiness limitations; select a recommendation.
- **Expected result:** Metrics are explainable and link to practice.
- **Failure cases:** Insufficient evidence, delayed calculation, or changed content taxonomy; display uncertainty rather than a false score.

## Premium subscription

- **Entry point:** Paywall or subscription settings.
- **Main steps:** Compare entitlement-backed features; start Stripe checkout/store-compliant flow; provider confirms payment; webhook updates entitlement; app refreshes access.
- **Expected result:** Access reflects verified provider state and renewal terms are visible.
- **Failure cases:** Abandoned/failed payment, delayed or replayed webhook, refund, expiry, or provider outage; consumers are idempotent and access follows last verified state.

## Reporting a questionable answer

- **Entry point:** Question or explanation menu.
- **Main steps:** Select reason; add optional comment; submit question/release/version identifiers; receive confirmation; reviewer triages without changing historical content.
- **Expected result:** Traceable issue enters the editorial workflow without exposing reporter identity unnecessarily.
- **Failure cases:** Duplicate report, offline submission, abusive content, or retired item; queue retry, deduplicate where useful, and preserve auditability.

