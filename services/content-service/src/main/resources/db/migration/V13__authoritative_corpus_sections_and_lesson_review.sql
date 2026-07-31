ALTER TABLE source_reference
  ADD COLUMN original_filename VARCHAR(500),
  ADD COLUMN file_checksum CHAR(64),
  ADD COLUMN language_code VARCHAR(20),
  ADD COLUMN official_study_material BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN attribution TEXT,
  ADD COLUMN licensing_review_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  ADD COLUMN imported_at TIMESTAMPTZ;

ALTER TABLE source_reference
  ADD CONSTRAINT ck_source_file_checksum
    CHECK(file_checksum IS NULL OR file_checksum ~ '^[a-f0-9]{64}$'),
  ADD CONSTRAINT ck_source_licensing_review
    CHECK(licensing_review_status IN ('PENDING','APPROVED','RESTRICTED','REJECTED'));

CREATE UNIQUE INDEX uq_source_file_checksum
  ON source_reference(file_checksum) WHERE file_checksum IS NOT NULL;

CREATE TABLE source_section (
  id UUID PRIMARY KEY,
  source_reference_id UUID NOT NULL REFERENCES source_reference(id),
  parent_section_id UUID REFERENCES source_section(id),
  code VARCHAR(160) NOT NULL,
  chapter_title VARCHAR(500) NOT NULL,
  subsection_title VARCHAR(500) NOT NULL,
  structural_path TEXT NOT NULL,
  page_start INTEGER NOT NULL,
  page_end INTEGER NOT NULL,
  display_order INTEGER NOT NULL,
  exact_text TEXT NOT NULL,
  normalized_text TEXT NOT NULL,
  section_checksum CHAR(64) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_source_section_code UNIQUE(source_reference_id,code),
  CONSTRAINT uq_source_section_order UNIQUE(source_reference_id,display_order),
  CONSTRAINT uq_source_section_checksum UNIQUE(source_reference_id,section_checksum),
  CONSTRAINT ck_source_section_pages CHECK(page_start>=1 AND page_end>=page_start),
  CONSTRAINT ck_source_section_order CHECK(display_order>=0),
  CONSTRAINT ck_source_section_text CHECK(length(trim(exact_text))>0 AND length(trim(normalized_text))>0),
  CONSTRAINT ck_source_section_checksum CHECK(section_checksum ~ '^[a-f0-9]{64}$')
);
CREATE INDEX idx_source_section_source_order
  ON source_section(source_reference_id,display_order);
CREATE INDEX idx_source_section_pages
  ON source_section(source_reference_id,page_start,page_end);

CREATE TABLE learning_objective_source_section (
  learning_objective_id UUID NOT NULL REFERENCES learning_objective(id),
  source_section_id UUID NOT NULL REFERENCES source_section(id),
  PRIMARY KEY(learning_objective_id,source_section_id)
);
CREATE INDEX idx_objective_source_section_section
  ON learning_objective_source_section(source_section_id);

ALTER TABLE knowledge_fact_ai_provenance
  ADD COLUMN source_section_id UUID REFERENCES source_section(id);
CREATE INDEX idx_fact_ai_source_section
  ON knowledge_fact_ai_provenance(source_section_id)
  WHERE source_section_id IS NOT NULL;

CREATE TABLE question_source_section (
  question_version_id UUID NOT NULL REFERENCES question_version(id) ON DELETE CASCADE,
  source_section_id UUID NOT NULL REFERENCES source_section(id),
  PRIMARY KEY(question_version_id,source_section_id)
);
CREATE INDEX idx_question_source_section_section
  ON question_source_section(source_section_id);

CREATE TABLE lesson_draft (
  id UUID PRIMARY KEY,
  topic_id UUID NOT NULL REFERENCES topic(id),
  version_number INTEGER NOT NULL,
  title VARCHAR(500) NOT NULL,
  introduction TEXT NOT NULL,
  summary TEXT NOT NULL,
  important_points JSONB NOT NULL DEFAULT '[]'::jsonb,
  review_status VARCHAR(30) NOT NULL,
  source_checksum CHAR(64) NOT NULL,
  created_by VARCHAR(200) NOT NULL,
  reviewed_by VARCHAR(200),
  review_note TEXT,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  reviewed_at TIMESTAMPTZ,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_lesson_draft_version UNIQUE(topic_id,version_number),
  CONSTRAINT ck_lesson_draft_review CHECK(
    review_status IN ('DRAFT','UNDER_REVIEW','REVIEWED','REQUIRES_UPDATE','REJECTED')
  ),
  CONSTRAINT ck_lesson_draft_checksum CHECK(source_checksum ~ '^[a-f0-9]{64}$'),
  CONSTRAINT ck_lesson_draft_text CHECK(
    length(trim(title))>0 AND length(trim(introduction))>0 AND length(trim(summary))>0
  )
);
CREATE INDEX idx_lesson_draft_topic_review
  ON lesson_draft(topic_id,review_status,version_number DESC);

CREATE TABLE lesson_draft_section (
  id UUID PRIMARY KEY,
  lesson_draft_id UUID NOT NULL REFERENCES lesson_draft(id) ON DELETE CASCADE,
  source_section_id UUID NOT NULL REFERENCES source_section(id),
  title VARCHAR(500) NOT NULL,
  explanation TEXT NOT NULL,
  key_terms JSONB NOT NULL DEFAULT '[]'::jsonb,
  supported_examples JSONB NOT NULL DEFAULT '[]'::jsonb,
  display_order INTEGER NOT NULL,
  section_checksum CHAR(64) NOT NULL,
  CONSTRAINT uq_lesson_draft_section_order UNIQUE(lesson_draft_id,display_order),
  CONSTRAINT ck_lesson_draft_section_order CHECK(display_order>=0),
  CONSTRAINT ck_lesson_draft_section_checksum CHECK(section_checksum ~ '^[a-f0-9]{64}$'),
  CONSTRAINT ck_lesson_draft_section_text CHECK(
    length(trim(title))>0 AND length(trim(explanation))>0
  )
);
CREATE INDEX idx_lesson_draft_section_source
  ON lesson_draft_section(source_section_id);

CREATE TABLE lesson_draft_section_fact (
  lesson_draft_section_id UUID NOT NULL
    REFERENCES lesson_draft_section(id) ON DELETE CASCADE,
  knowledge_fact_version_id UUID NOT NULL REFERENCES knowledge_fact_version(id),
  PRIMARY KEY(lesson_draft_section_id,knowledge_fact_version_id)
);
CREATE INDEX idx_lesson_draft_fact_version
  ON lesson_draft_section_fact(knowledge_fact_version_id);

CREATE TRIGGER audit_source_section
  AFTER INSERT OR UPDATE OR DELETE ON source_section
  FOR EACH ROW EXECUTE FUNCTION append_entity_audit('SourceSection');
CREATE TRIGGER audit_lesson_draft
  AFTER INSERT OR UPDATE OR DELETE ON lesson_draft
  FOR EACH ROW EXECUTE FUNCTION append_entity_audit('LessonDraft');
