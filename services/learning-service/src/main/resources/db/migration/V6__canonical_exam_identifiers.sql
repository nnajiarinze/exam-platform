CREATE FUNCTION canonical_exam_identifier(value TEXT) RETURNS TEXT
LANGUAGE SQL IMMUTABLE STRICT PARALLEL SAFE AS $$
  SELECT trim(BOTH '-' FROM lower(regexp_replace(trim(value), '[^a-zA-Z0-9]+', '-', 'g')))
$$;

DROP INDEX uq_active_release_per_exam;

-- When legacy representations both have an active release, the most recently
-- explicitly activated release wins. Published/imported time provides a stable
-- fallback for releases created before explicit activation existed.
WITH ranked AS (
  SELECT id, row_number() OVER (
    PARTITION BY canonical_exam_identifier(exam_id)
    ORDER BY activated_at DESC NULLS LAST, published_at DESC, imported_at DESC, id DESC
  ) AS position
  FROM imported_content_release
  WHERE status = 'ACTIVE'
)
UPDATE imported_content_release release
SET status = 'IMPORTED', version = version + 1
FROM ranked
WHERE release.id = ranked.id AND ranked.position > 1;

UPDATE imported_content_release
SET exam_id = canonical_exam_identifier(exam_id);
UPDATE practice_session
SET exam_id = canonical_exam_identifier(exam_id);

DROP INDEX uq_active_mock_blueprint_per_exam;
WITH ranked AS (
  SELECT id, row_number() OVER (
    PARTITION BY canonical_exam_identifier(exam_id)
    ORDER BY updated_at DESC, created_at DESC, id DESC
  ) AS position
  FROM mock_exam_blueprint
  WHERE active
)
UPDATE mock_exam_blueprint blueprint
SET active = FALSE
FROM ranked
WHERE blueprint.id = ranked.id AND ranked.position > 1;
UPDATE mock_exam_blueprint
SET exam_id = canonical_exam_identifier(exam_id);

ALTER TABLE imported_content_release ADD CONSTRAINT ck_imported_release_exam_id_canonical
  CHECK (exam_id ~ '^[a-z0-9]+(-[a-z0-9]+)*$');
ALTER TABLE practice_session ADD CONSTRAINT ck_practice_session_exam_id_canonical
  CHECK (exam_id ~ '^[a-z0-9]+(-[a-z0-9]+)*$');
ALTER TABLE mock_exam_blueprint ADD CONSTRAINT ck_mock_blueprint_exam_id_canonical
  CHECK (exam_id ~ '^[a-z0-9]+(-[a-z0-9]+)*$');

CREATE UNIQUE INDEX uq_active_release_per_exam
  ON imported_content_release(exam_id) WHERE status='ACTIVE';
CREATE UNIQUE INDEX uq_active_mock_blueprint_per_exam
  ON mock_exam_blueprint(exam_id) WHERE active;
