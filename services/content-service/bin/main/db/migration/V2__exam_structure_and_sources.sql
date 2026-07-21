CREATE TABLE exam (
  id UUID PRIMARY KEY, code VARCHAR(100) NOT NULL, name VARCHAR(300) NOT NULL,
  country_code VARCHAR(2) NOT NULL, status VARCHAR(20) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_exam_code UNIQUE (code), CONSTRAINT ck_exam_status CHECK (status IN ('DRAFT','ACTIVE','ARCHIVED'))
);
CREATE INDEX idx_exam_status ON exam(status);
CREATE INDEX idx_exam_search ON exam(lower(name));

CREATE TABLE exam_version (
  id UUID PRIMARY KEY, exam_id UUID NOT NULL REFERENCES exam(id), version_code VARCHAR(100) NOT NULL,
  display_name VARCHAR(300) NOT NULL, status VARCHAR(20) NOT NULL, valid_from DATE, valid_to DATE,
  created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_exam_version_code UNIQUE (exam_id, version_code),
  CONSTRAINT ck_exam_version_status CHECK (status IN ('DRAFT','ACTIVE','ARCHIVED')),
  CONSTRAINT ck_exam_version_dates CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to >= valid_from)
);
CREATE INDEX idx_exam_version_exam ON exam_version(exam_id);
CREATE INDEX idx_exam_version_status ON exam_version(status);

CREATE TABLE subject (
  id UUID PRIMARY KEY, exam_version_id UUID NOT NULL REFERENCES exam_version(id), code VARCHAR(100) NOT NULL,
  name VARCHAR(300) NOT NULL, description TEXT, sort_order INTEGER NOT NULL,
  status VARCHAR(20) NOT NULL, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_subject_code UNIQUE (exam_version_id, code), CONSTRAINT uq_subject_order UNIQUE (exam_version_id, sort_order),
  CONSTRAINT ck_subject_status CHECK (status IN ('DRAFT','ACTIVE','ARCHIVED')), CONSTRAINT ck_subject_order CHECK (sort_order >= 0)
);
CREATE INDEX idx_subject_version_order ON subject(exam_version_id, sort_order);

CREATE TABLE topic (
  id UUID PRIMARY KEY, subject_id UUID NOT NULL REFERENCES subject(id), code VARCHAR(100) NOT NULL,
  name VARCHAR(300) NOT NULL, description TEXT, sort_order INTEGER NOT NULL,
  status VARCHAR(20) NOT NULL, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_topic_code UNIQUE (subject_id, code), CONSTRAINT uq_topic_order UNIQUE (subject_id, sort_order),
  CONSTRAINT ck_topic_status CHECK (status IN ('DRAFT','ACTIVE','ARCHIVED')), CONSTRAINT ck_topic_order CHECK (sort_order >= 0)
);
CREATE INDEX idx_topic_subject_order ON topic(subject_id, sort_order);

CREATE TABLE source_reference (
  id UUID PRIMARY KEY, publisher VARCHAR(300) NOT NULL, title VARCHAR(500) NOT NULL, url TEXT,
  source_type VARCHAR(40) NOT NULL, document_version VARCHAR(200), publication_date DATE, accessed_at DATE NOT NULL,
  copyright_notes TEXT, internal_notes TEXT, review_status VARCHAR(30) NOT NULL, status VARCHAR(20) NOT NULL,
  replacement_source_id UUID REFERENCES source_reference(id), created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ck_source_type CHECK (source_type IN ('GOVERNMENT_WEBPAGE','GOVERNMENT_DOCUMENT','LEGISLATION','PUBLIC_AUTHORITY_GUIDANCE','LICENSED_MATERIAL','INTERNAL_RESEARCH','OTHER')),
  CONSTRAINT ck_source_review CHECK (review_status IN ('UNREVIEWED','REVIEWED','REQUIRES_UPDATE')),
  CONSTRAINT ck_source_status CHECK (status IN ('ACTIVE','RETIRED')),
  CONSTRAINT ck_source_replacement CHECK (replacement_source_id IS NULL OR replacement_source_id <> id),
  CONSTRAINT ck_retired_replacement CHECK (replacement_source_id IS NULL OR status = 'RETIRED')
);
CREATE INDEX idx_source_publisher ON source_reference(publisher);
CREATE INDEX idx_source_status ON source_reference(status);
CREATE INDEX idx_source_review ON source_reference(review_status);
CREATE INDEX idx_source_type ON source_reference(source_type);
CREATE INDEX idx_source_title_search ON source_reference(lower(title));
