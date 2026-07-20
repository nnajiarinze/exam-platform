CREATE TABLE question (
  id UUID PRIMARY KEY,
  learning_objective_id UUID NOT NULL REFERENCES learning_objective(id),
  current_version_id UUID,
  code VARCHAR(100) NOT NULL UNIQUE,
  question_type VARCHAR(30) NOT NULL,
  question_text TEXT NOT NULL,
  difficulty VARCHAR(20) NOT NULL,
  review_status VARCHAR(30) NOT NULL,
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ck_question_type CHECK (question_type IN ('SINGLE_CHOICE','MULTIPLE_CHOICE','TRUE_FALSE')),
  CONSTRAINT ck_question_difficulty CHECK (difficulty IN ('EASY','MEDIUM','HARD')),
  CONSTRAINT ck_question_review CHECK (review_status IN ('UNREVIEWED','UNDER_REVIEW','APPROVED','REJECTED','REQUIRES_UPDATE')),
  CONSTRAINT ck_question_status CHECK (status IN ('DRAFT','ACTIVE','RETIRED'))
);

CREATE TABLE question_version (
  id UUID PRIMARY KEY,
  question_id UUID NOT NULL REFERENCES question(id),
  version_number INTEGER NOT NULL,
  learning_objective_id UUID NOT NULL REFERENCES learning_objective(id),
  question_type VARCHAR(30) NOT NULL,
  question_text TEXT NOT NULL,
  difficulty VARCHAR(20) NOT NULL,
  explanation TEXT,
  review_status VARCHAR(30) NOT NULL,
  author_id VARCHAR(200) NOT NULL,
  reviewer_id VARCHAR(200),
  review_note TEXT,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_question_version UNIQUE (question_id, version_number),
  CONSTRAINT ck_question_version_type CHECK (question_type IN ('SINGLE_CHOICE','MULTIPLE_CHOICE','TRUE_FALSE')),
  CONSTRAINT ck_question_version_difficulty CHECK (difficulty IN ('EASY','MEDIUM','HARD')),
  CONSTRAINT ck_question_version_review CHECK (review_status IN ('UNREVIEWED','UNDER_REVIEW','APPROVED','REJECTED','REQUIRES_UPDATE'))
);
ALTER TABLE question ADD CONSTRAINT fk_question_current_version FOREIGN KEY (current_version_id) REFERENCES question_version(id);

CREATE TABLE question_option (
  id UUID PRIMARY KEY,
  question_version_id UUID NOT NULL REFERENCES question_version(id) ON DELETE CASCADE,
  display_order INTEGER NOT NULL,
  text TEXT NOT NULL,
  correct BOOLEAN NOT NULL,
  feedback TEXT,
  CONSTRAINT uq_question_option_order UNIQUE (question_version_id, display_order),
  CONSTRAINT ck_question_option_order CHECK (display_order >= 0),
  CONSTRAINT ck_question_option_text CHECK (length(trim(text)) > 0)
);

CREATE TABLE question_knowledge_fact (
  question_version_id UUID NOT NULL REFERENCES question_version(id) ON DELETE CASCADE,
  knowledge_fact_version_id UUID NOT NULL REFERENCES knowledge_fact_version(id),
  PRIMARY KEY (question_version_id, knowledge_fact_version_id)
);

CREATE TABLE question_tag (
  question_version_id UUID NOT NULL REFERENCES question_version(id) ON DELETE CASCADE,
  tag VARCHAR(100) NOT NULL,
  PRIMARY KEY (question_version_id, tag),
  CONSTRAINT ck_question_tag_text CHECK (length(trim(tag)) > 0)
);

CREATE INDEX idx_question_objective ON question(learning_objective_id);
CREATE INDEX idx_question_type ON question(question_type);
CREATE INDEX idx_question_difficulty ON question(difficulty);
CREATE INDEX idx_question_review ON question(review_status);
CREATE INDEX idx_question_status ON question(status);
CREATE INDEX idx_question_search ON question USING gin(to_tsvector('simple', question_text));
CREATE INDEX idx_question_version_question ON question_version(question_id, version_number DESC);
CREATE INDEX idx_question_option_version ON question_option(question_version_id, display_order);
CREATE INDEX idx_question_fact_fact_version ON question_knowledge_fact(knowledge_fact_version_id);
CREATE INDEX idx_question_tag_tag ON question_tag(tag);
