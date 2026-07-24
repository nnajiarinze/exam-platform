CREATE TABLE imported_lesson_section (
    id UUID PRIMARY KEY,
    external_section_id VARCHAR(200) NOT NULL,
    external_section_version_id VARCHAR(200) NOT NULL,
    content_release_id UUID NOT NULL REFERENCES imported_content_release(id) ON DELETE CASCADE,
    topic_id UUID NOT NULL REFERENCES imported_topic(id) ON DELETE CASCADE,
    title VARCHAR(500) NOT NULL,
    explanation TEXT NOT NULL,
    display_order INTEGER NOT NULL CHECK (display_order >= 0),
    UNIQUE (content_release_id, external_section_version_id),
    UNIQUE (topic_id, display_order)
);
CREATE INDEX idx_lesson_section_topic_order ON imported_lesson_section(topic_id, display_order);

CREATE TABLE imported_lesson_source (
    id UUID PRIMARY KEY,
    lesson_section_id UUID NOT NULL REFERENCES imported_lesson_section(id) ON DELETE CASCADE,
    title VARCHAR(500) NOT NULL,
    url TEXT NOT NULL,
    UNIQUE (lesson_section_id, url)
);

CREATE TABLE lesson_progress (
    id UUID PRIMARY KEY,
    learner_id UUID NOT NULL REFERENCES learner_profile(id) ON DELETE CASCADE,
    content_release_id UUID NOT NULL REFERENCES imported_content_release(id),
    topic_id UUID NOT NULL REFERENCES imported_topic(id),
    last_section_id UUID NOT NULL REFERENCES imported_lesson_section(id),
    completed_section_count INTEGER NOT NULL CHECK (completed_section_count >= 0),
    started_at TIMESTAMPTZ NOT NULL,
    last_accessed_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    UNIQUE (learner_id, content_release_id, topic_id)
);
CREATE INDEX idx_lesson_progress_continue
    ON lesson_progress(learner_id, last_accessed_at DESC) WHERE completed_at IS NULL;
