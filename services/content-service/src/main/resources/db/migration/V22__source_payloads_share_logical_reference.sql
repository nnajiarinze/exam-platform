ALTER TABLE source_payload_revision
  DROP CONSTRAINT source_payload_revision_materialized_source_reference_id_key;
ALTER TABLE source_payload_revision ADD COLUMN content_text text;
ALTER TABLE source_payload_revision ALTER COLUMN content_text SET NOT NULL;

DROP VIEW canonical_source_payload;
CREATE VIEW canonical_source_payload AS
SELECT p.logical_source_key,p.source_revision_id,p.id payload_id,
       p.materialized_source_reference_id source_reference_id,p.content_text,p.content_checksum,
       p.document_checksum,p.parser_version,p.extraction_version,p.page_start,p.page_end
FROM source_payload_revision p
WHERE p.payload_role='CANONICAL' AND p.status='ACTIVE';
