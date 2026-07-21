# Learner settings

Learning Service owns app-level learner settings. A single `learner_settings` row is keyed by the internal learner profile, created with deterministic defaults, and updated using a client-supplied optimistic-lock version. APIs use `/api/v1/me/settings`; the authenticated token subject is resolved server-side and no learner identifier is accepted.

The current goals are 5–100 questions per day and 1–7 study days per ISO week (Monday–Sunday). Progress counts submitted Practice answers in the learner's stored IANA timezone. Study minutes are intentionally absent because current activity does not measure active study duration reliably.

Keycloak remains authoritative for email, verification, and passwords. Learning owns display name, languages, study goals, local-reminder preference, and future progress/achievement notification preferences.

The deterministic demo learner receives a 20-question daily goal, five study days per week, Europe/Stockholm timezone, and reminders disabled. Deleted and disabled accounts cannot resolve `/me` settings.
