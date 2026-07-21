CREATE TABLE learner_settings (
    learner_id UUID PRIMARY KEY REFERENCES learner_profile(id),
    daily_question_goal INTEGER NOT NULL DEFAULT 20,
    weekly_study_days_goal INTEGER NOT NULL DEFAULT 5,
    study_reminder_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    preferred_reminder_time TIME NOT NULL DEFAULT TIME '19:00',
    timezone VARCHAR(64) NOT NULL DEFAULT 'Europe/Stockholm',
    progress_summary_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    achievement_notifications_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_learner_settings_daily_goal CHECK (daily_question_goal BETWEEN 5 AND 100),
    CONSTRAINT ck_learner_settings_weekly_goal CHECK (weekly_study_days_goal BETWEEN 1 AND 7),
    CONSTRAINT ck_learner_settings_timezone CHECK (length(trim(timezone)) BETWEEN 1 AND 64),
    CONSTRAINT ck_learner_settings_version CHECK (version >= 0)
);

INSERT INTO learner_settings (learner_id)
SELECT id FROM learner_profile
ON CONFLICT (learner_id) DO NOTHING;
