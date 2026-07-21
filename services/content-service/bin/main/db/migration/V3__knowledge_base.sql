CREATE TABLE learning_objective (
  id UUID PRIMARY KEY,
  topic_id UUID NOT NULL REFERENCES topic(id),
  code VARCHAR(100) NOT NULL,
  title VARCHAR(500) NOT NULL,
  description TEXT,
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_learning_objective_code UNIQUE (topic_id, code),
  CONSTRAINT ck_learning_objective_status CHECK (status IN ('DRAFT','ACTIVE','ARCHIVED'))
);
CREATE INDEX idx_learning_objective_topic ON learning_objective(topic_id);
CREATE INDEX idx_learning_objective_status ON learning_objective(status);
CREATE INDEX idx_learning_objective_search ON learning_objective(lower(title));

CREATE TABLE knowledge_fact (
  id UUID PRIMARY KEY,
  learning_objective_id UUID NOT NULL REFERENCES learning_objective(id),
  current_version_id UUID,
  canonical_statement TEXT NOT NULL,
  review_status VARCHAR(30) NOT NULL,
  status VARCHAR(20) NOT NULL,
  valid_from DATE,
  valid_to DATE,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ck_knowledge_fact_review CHECK (review_status IN ('UNREVIEWED','UNDER_REVIEW','APPROVED','REJECTED','REQUIRES_UPDATE')),
  CONSTRAINT ck_knowledge_fact_status CHECK (status IN ('DRAFT','ACTIVE','RETIRED')),
  CONSTRAINT ck_knowledge_fact_dates CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to >= valid_from)
);
CREATE UNIQUE INDEX uq_active_knowledge_fact_statement
  ON knowledge_fact(learning_objective_id, lower(canonical_statement)) WHERE status = 'ACTIVE';
CREATE INDEX idx_knowledge_fact_objective ON knowledge_fact(learning_objective_id);
CREATE INDEX idx_knowledge_fact_review ON knowledge_fact(review_status);
CREATE INDEX idx_knowledge_fact_status ON knowledge_fact(status);
CREATE INDEX idx_knowledge_fact_validity ON knowledge_fact(valid_from, valid_to);
CREATE INDEX idx_knowledge_fact_search ON knowledge_fact USING gin(to_tsvector('simple', canonical_statement));

CREATE TABLE knowledge_fact_version (
  id UUID PRIMARY KEY,
  knowledge_fact_id UUID NOT NULL REFERENCES knowledge_fact(id),
  version_number INTEGER NOT NULL,
  canonical_statement TEXT NOT NULL,
  review_status VARCHAR(30) NOT NULL,
  valid_from DATE,
  valid_to DATE,
  author_id VARCHAR(200) NOT NULL,
  reviewer_id VARCHAR(200),
  review_note TEXT,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_knowledge_fact_version UNIQUE (knowledge_fact_id, version_number),
  CONSTRAINT ck_knowledge_fact_version_review CHECK (review_status IN ('UNREVIEWED','UNDER_REVIEW','APPROVED','REJECTED','REQUIRES_UPDATE')),
  CONSTRAINT ck_knowledge_fact_version_dates CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to >= valid_from)
);
ALTER TABLE knowledge_fact ADD CONSTRAINT fk_knowledge_fact_current_version
  FOREIGN KEY (current_version_id) REFERENCES knowledge_fact_version(id);
CREATE INDEX idx_knowledge_fact_version_fact ON knowledge_fact_version(knowledge_fact_id, version_number DESC);

CREATE TABLE knowledge_fact_source (
  knowledge_fact_version_id UUID NOT NULL REFERENCES knowledge_fact_version(id),
  source_reference_id UUID NOT NULL REFERENCES source_reference(id),
  PRIMARY KEY (knowledge_fact_version_id, source_reference_id)
);
CREATE INDEX idx_knowledge_fact_source_reference ON knowledge_fact_source(source_reference_id);
