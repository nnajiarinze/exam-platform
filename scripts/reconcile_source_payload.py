#!/usr/bin/env python3
"""Append-only reconciliation for the Sverige i fokus source payload identity split."""
from __future__ import annotations
import hashlib, json, subprocess, uuid

SHARED="46ba681d-eb48-5e1b-8335-3e5f46ece535"
PDF_SHA="39a93261cc64af0122e186b7d67f57dffad573576570956a4754d22ce776aada"
LOCAL_SHA="9e8b701409ede7b84face0adaaa68cff0e4bbbfa76089662bb1ebc6ae1259023"
HOSTED_SHA="9e11e19df8c74812b491a2b27cc2cda6e9bc46eed972a9899bbb1f4f93dffc1a"
LOGICAL="sverige-i-fokus-v1|39a93261cc64af0122e186b7d67f57dffad573576570956a4754d22ce776aada|document"
CANONICAL_PAYLOAD="28b348c0-c154-58d4-a082-cf796b09752a"; HISTORICAL_PAYLOAD="8cbd4b7b-4486-5177-86db-63ea9d7ea269"
RECONCILIATION="39b9ab7f-e738-58a2-b0f6-2db5d75559c9"; ACTOR="source-payload-reconciliation-v1"
ORIGIN_COMMIT="d602fe578f66a03bea9090011e8b736e058db562"

def sql(value):
    if value is None:return "NULL"
    if isinstance(value,bool):return "TRUE" if value else "FALSE"
    if isinstance(value,int):return str(value)
    return "'"+str(value).replace("'","''")+"'"

def historical_text()->str:
    raw=subprocess.check_output(["git","show",f"{ORIGIN_COMMIT}:content/sverige-i-fokus/source-sections.json"])
    return "\n\n".join(row["exactText"] for row in json.loads(raw))

def main():
    historical=historical_text()
    if hashlib.sha256(historical.encode()).hexdigest()!=HOSTED_SHA or len(historical)!=79087: raise SystemExit("Historical source-v1 payload guard failed")
    evidence=json.dumps({"canonicality":"BOTH_VALID_DIFFERENT_REVISIONS","pdfChecksum":PDF_SHA,"localExtraction":"pdftotext-layout/26.07.0","hostedExtraction":"bounded-section-concat-v1","hostedOriginCommit":ORIGIN_COMMIT,"boundaryChecks":38},sort_keys=True)
    compatibility=json.dumps({"historicalSharedId":SHARED,"futureImport":"REUSE_RECONCILED_ALIAS","contentTargetMigration":"20","requiredSchemaMigration":"21"},sort_keys=True)
    current_compatibility=json.dumps({"historicalSharedId":SHARED,"futureImport":"REUSE_RECONCILED_ALIAS","contentTargetMigration":"20","requiredSchemaMigration":"24"},sort_keys=True)
    statements=["BEGIN;","SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;",f"SELECT set_config('app.actor_id',{sql(ACTOR)},true);",f"DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM source_reference WHERE id={sql(SHARED)} AND content_checksum={sql(LOCAL_SHA)} AND file_checksum={sql(PDF_SHA)}) THEN RAISE EXCEPTION 'Local canonical source guard failed'; END IF; END $$;",
      f"INSERT INTO source_payload_revision(id,logical_source_key,historical_shared_id,materialized_source_reference_id,source_revision_id,payload_role,document_checksum,content_checksum,parser_version,extraction_version,page_start,page_end,extraction_start,extraction_end,normalized_length,origin,status,created_by,created_at,content_text) SELECT {sql(CANONICAL_PAYLOAD)},{sql(LOGICAL)},{sql(SHARED)},{sql(SHARED)},'sverige-i-fokus-source-v2','CANONICAL',{sql(PDF_SHA)},{sql(LOCAL_SHA)},'chapter-boundary-v2','pdftotext-layout-26.07.0',1,48,'{{\"page\":1,\"offset\":0}}','{{\"page\":48,\"offset\":-1}}',90381,'LOCAL_AUTHORITATIVE_PDF_EXTRACTION','ACTIVE',{sql(ACTOR)},'2026-08-02T12:00:00Z',content_text FROM source_reference WHERE id={sql(SHARED)} ON CONFLICT(id) DO NOTHING;",
      f"INSERT INTO source_payload_revision(id,logical_source_key,historical_shared_id,materialized_source_reference_id,source_revision_id,payload_role,document_checksum,content_checksum,parser_version,extraction_version,page_start,page_end,extraction_start,extraction_end,normalized_length,origin,status,created_by,created_at,content_text) VALUES({sql(HISTORICAL_PAYLOAD)},{sql(LOGICAL)},{sql(SHARED)},{sql(SHARED)},'sverige-i-fokus-source-v1','HISTORICAL',{sql(PDF_SHA)},{sql(HOSTED_SHA)},'page-prefix-v1','bounded-section-concat-v1',5,47,'{{\"page\":5,\"offset\":212}}','{{\"page\":47,\"offset\":-1}}',78348,'HOSTED_INITIAL_STRUCTURE_IMPORT','SUPERSEDED',{sql(ACTOR)},'2026-08-02T12:00:00Z',{sql(historical)}) ON CONFLICT(id) DO NOTHING;",
      f"INSERT INTO source_payload_identity_reconciliation VALUES({sql(RECONCILIATION)},'source-reference/{SHARED}',{sql(SHARED)},{sql(LOCAL_SHA)},{sql(HOSTED_SHA)},{sql(CANONICAL_PAYLOAD)},{sql(HISTORICAL_PAYLOAD)},{sql(LOGICAL)},'DUPLICATE_IDENTITY_SPLIT','A PDF-derived UUID omitted revision and extraction identity; preserve both payloads and select source-v2 for future authoring.',{sql(ACTOR)},false,{sql(evidence)}::jsonb,{sql(compatibility)}::jsonb,'2026-08-02T12:00:00Z') ON CONFLICT(id) DO NOTHING;",
      f"INSERT INTO source_payload_reconciliation_revision(id,reconciliation_id,revision_number,previous_revision_id,compatibility_metadata,reason,created_by,created_at) VALUES('2abac7d3-a190-50b6-bf1b-017d1a378c44',{sql(RECONCILIATION)},2,NULL,{sql(current_compatibility)}::jsonb,'Record the completed additive schema prerequisite without mutating the original reconciliation.',{sql(ACTOR)},'2026-08-02T12:00:00Z') ON CONFLICT(id) DO NOTHING;",
    ]
    dependency_queries=[
      ("KNOWLEDGE_FACT","v.knowledge_fact_id","k.knowledge_fact_version_id","knowledge_fact_source k JOIN knowledge_fact_version v ON v.id=k.knowledge_fact_version_id","k.source_reference_id"),
      ("FACT_PROVENANCE","p.knowledge_fact_version_id","p.knowledge_fact_version_id","knowledge_fact_ai_provenance p","p.source_reference_id"),
      ("SOURCE_SECTION","s.id","NULL","source_section s","s.source_reference_id"),
      ("QUESTION","q.id","r.question_version_id","question_source_reference r JOIN question q ON q.current_version_id=r.question_version_id","r.source_reference_id"),
    ]
    for entity,entity_id,version_id,from_sql,source_field in dependency_queries:
      statements.append(f"INSERT INTO source_dependency_reconciliation(id,reconciliation_id,entity_type,entity_id,entity_version_id,original_source_reference_id,resolved_source_reference_id,resolved_payload_id,classification,original_provenance_preserved,created_by,created_at,status) SELECT gen_random_uuid(),{sql(RECONCILIATION)},{sql(entity)},{entity_id},{version_id},{sql(SHARED)},{sql(SHARED)},{sql(CANONICAL_PAYLOAD)},'STALE_REFERENCE_REQUIRES_VERSIONED_REBIND',true,{sql(ACTOR)},'2026-08-02T12:00:00Z','ACTIVE' FROM {from_sql} WHERE {source_field}={sql(SHARED)} AND NOT EXISTS(SELECT 1 FROM source_dependency_reconciliation existing WHERE existing.reconciliation_id={sql(RECONCILIATION)} AND existing.entity_type={sql(entity)} AND existing.entity_id={entity_id} AND existing.entity_version_id IS NOT DISTINCT FROM {version_id} AND existing.status='ACTIVE') ON CONFLICT DO NOTHING;")
    statements += [f"INSERT INTO audit_event(id,occurred_at,actor_id,actor_name,actor_role,action,entity_type,entity_id,entity_version,previous_state,new_state,reason,metadata,request_id) VALUES('9f732925-d94e-5d71-a3ed-69460a722c74','2026-08-02T12:00:00Z',{sql(ACTOR)},'Deterministic source payload reconciler','SYSTEM','CONFIG_CHANGE','SOURCE_REFERENCE',{sql(SHARED)},NULL,NULL,jsonb_build_object('canonicalPayloadId',{sql(CANONICAL_PAYLOAD)},'historicalPayloadId',{sql(HISTORICAL_PAYLOAD)}),'Duplicate immutable identity split',jsonb_build_object('reconciliationId',{sql(RECONCILIATION)},'event','source.payload.identity.reconciled','humanVerified',false),'source-payload-reconciliation-v1') ON CONFLICT(id) DO NOTHING;","COMMIT;"]
    print("\n".join(statements))

if __name__=="__main__":main()
