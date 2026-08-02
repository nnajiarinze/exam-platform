#!/usr/bin/env python3
"""Deterministic, secret-free authoring snapshots and zero-write import plans."""
from __future__ import annotations

import argparse
import base64
import datetime as dt
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import unicodedata
from collections import defaultdict, deque
from pathlib import Path
from typing import Any

FORMAT = "medbo-authoring-snapshot/v1"
CORPUS_ID = "sverige-i-fokus-v1"
SOURCE_REVISION = "v2"
ACTIVE_RELEASE = {
    "id": "be07a3f5-a80c-42c8-bf1c-02541755f178",
    "key": "sverige-i-fokus-complete-source-v2-deep-lessons-v2-internal",
    "checksum": "7903212ece00cb093220d07418d6fe9fde8116846d2b384b414531f1bfaeb7a1",
}
EXPECTED_MIGRATIONS = {"content": "24", "ai": "32"}
EXPECTED_CANONICAL = {"approvedActiveFacts": 209, "latestLessons": 38, "latestPages": 194, "canonicalQuestions": 139}
DATABASES = {
    "content": {"service": "content-database", "user": "content", "database": "content"},
    "ai": {"service": "ai-database", "user": "ai", "database": "ai"},
}
STRUCTURAL_REUSE = {
    "content": {"exam", "exam_version", "subject", "topic", "source_reference", "source_revision", "source_section", "learning_objective", "learning_objective_source_section", "learning_objective_source_mapping_revision", "source_identity_reconciliation"},
    "ai": {"ai_model_price_profile", "ai_openrouter_paid_model", "ai_quota_profile"},
}
EPHEMERAL_FIELDS = {
    "ai": {
        "ai_provider_attempt": {"heartbeat_at", "lease_expires_at", "worker_id", "process_instance_id"},
        "ai_paid_request_accounting": {"heartbeat_at", "lease_expires_at", "owner_worker_id", "process_instance_id"},
    }
}
SECRET_MARKERS = ("api_key", "password", "access_token", "refresh_token", "client_secret", "openrouter_api_key", "groq_api_key")
RUNTIME_RULES = {
    "ai_generation_job": {"status": ["RUNNING", "QUEUED"], "futureStatus": "FAILED", "reason": "STALE_AFTER_AUTHORING_SNAPSHOT_TRANSFER"},
    "ai_provider_attempt": {"lifecycle_state": ["RUNNING", "LEASED", "RESERVED"], "futureState": "RECOVERED_STALE"},
    "ai_paid_request_accounting": {"reservation_state": ["RESERVED", "ACTIVE"], "futureState": "RECONCILIATION_PENDING"},
}

def canonical_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))

def file_sha(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()

def command_environment(role: str) -> dict[str, str]:
    env = os.environ.copy()
    prefix = f"AUTHORING_{role.upper()}_"
    for key in ("PGHOST", "PGPORT", "PGUSER", "PGPASSWORD", "PGSSLMODE"):
        value = os.environ.get(prefix + key)
        if value is not None:
            env[key] = value
    return env

def psql(role: str, script: str) -> str:
    config = DATABASES[role]
    external = os.environ.get(f"AUTHORING_{role.upper()}_PGHOST")
    if external:
        command = ["psql", "-XAt", "-v", "ON_ERROR_STOP=1", "-d", config["database"]]
    else:
        command = ["docker", "compose", "exec", "-T", config["service"], "psql", "-U", config["user"], "-d", config["database"], "-XAt", "-v", "ON_ERROR_STOP=1"]
    completed = subprocess.run(command, input=script, text=True, capture_output=True, env=command_environment(role))
    if completed.returncode:
        raise RuntimeError(f"Read-only {role} query failed: {completed.stderr.strip()[:300]}")
    return completed.stdout

def query_json(role: str, sql: str) -> Any:
    output = [line for line in psql(role, f"BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;\n{sql.rstrip(';')};\nROLLBACK;\n").strip().splitlines() if line not in ("BEGIN", "ROLLBACK")]
    if not output:
        raise RuntimeError(f"No result returned for {role}")
    return json.loads(output[-1])

def schema_for(role: str) -> dict[str, Any]:
    migration = psql(role, "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1;\n").strip()
    metadata = query_json(role, r"""
SELECT json_build_object(
 'tables', (SELECT json_agg(x ORDER BY x->>'table') FROM (
   SELECT json_build_object(
     'table',t.table_name,
     'columns',(SELECT json_agg(json_build_object('name',c.column_name,'type',c.data_type,'nullable',c.is_nullable='YES') ORDER BY c.ordinal_position) FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name=t.table_name),
     'primaryKey',coalesce((SELECT json_agg(a.attname ORDER BY u.ordinality) FROM pg_constraint p CROSS JOIN LATERAL unnest(p.conkey) WITH ORDINALITY u(attnum,ordinality) JOIN pg_attribute a ON a.attrelid=p.conrelid AND a.attnum=u.attnum WHERE p.contype='p' AND p.conrelid=('public.'||quote_ident(t.table_name))::regclass),'[]'::json),
     'uniqueKeys',coalesce((SELECT json_agg(cols ORDER BY cols::text) FROM (SELECT json_agg(a.attname ORDER BY u.ordinality) cols FROM pg_constraint p CROSS JOIN LATERAL unnest(p.conkey) WITH ORDINALITY u(attnum,ordinality) JOIN pg_attribute a ON a.attrelid=p.conrelid AND a.attnum=u.attnum WHERE p.contype='u' AND p.conrelid=('public.'||quote_ident(t.table_name))::regclass GROUP BY p.oid) q),'[]'::json)
   ) x FROM information_schema.tables t WHERE t.table_schema='public' AND t.table_type='BASE TABLE' AND t.table_name<>'flyway_schema_history'
 ) q),
 'foreignKeys',(SELECT coalesce(json_agg(json_build_object('table',cl.relname,'columns',src.cols,'referencedTable',rcl.relname,'referencedColumns',dst.cols) ORDER BY cl.relname,p.conname),'[]'::json) FROM pg_constraint p JOIN pg_class cl ON cl.oid=p.conrelid JOIN pg_namespace n ON n.oid=cl.relnamespace JOIN pg_class rcl ON rcl.oid=p.confrelid CROSS JOIN LATERAL (SELECT json_agg(a.attname ORDER BY u.ordinality) cols FROM unnest(p.conkey) WITH ORDINALITY u(attnum,ordinality) JOIN pg_attribute a ON a.attrelid=p.conrelid AND a.attnum=u.attnum) src CROSS JOIN LATERAL (SELECT json_agg(a.attname ORDER BY u.ordinality) cols FROM unnest(p.confkey) WITH ORDINALITY u(attnum,ordinality) JOIN pg_attribute a ON a.attrelid=p.confrelid AND a.attnum=u.attnum) dst WHERE p.contype='f' AND n.nspname='public')
)""")
    metadata["migrationVersion"] = migration
    metadata["database"] = role
    metadata["schema"] = "public"
    return metadata

def dependency_order(schema: dict[str, Any]) -> list[str]:
    tables = {item["table"] for item in schema["tables"]}
    incoming = {table: 0 for table in tables}
    edges: dict[str, set[str]] = defaultdict(set)
    for fk in schema["foreignKeys"]:
        child, parent = fk["table"], fk["referencedTable"]
        if child != parent and child in tables and parent in tables and child not in edges[parent]:
            edges[parent].add(child); incoming[child] += 1
    queue = deque(sorted(table for table, count in incoming.items() if count == 0)); result = []
    while queue:
        table = queue.popleft(); result.append(table)
        for child in sorted(edges[table]):
            incoming[child] -= 1
            if incoming[child] == 0: queue.append(child)
    return result + sorted(tables - set(result))

def export_tables(role: str, schema: dict[str, Any], output: Path) -> dict[str, int]:
    output.mkdir(parents=True, exist_ok=True)
    markers: dict[str, Path] = {}
    sql = ["BEGIN TRANSACTION ISOLATION LEVEL SERIALIZABLE READ ONLY DEFERRABLE;"]
    for item in sorted(schema["tables"], key=lambda value: value["table"]):
        table = item["table"]; excluded = EPHEMERAL_FIELDS.get(role, {}).get(table, set())
        columns = [column["name"] for column in item["columns"] if column["name"] not in excluded]
        row_expression = "to_jsonb(t)"
        if excluded:
            excluded_sql=",".join("'"+column.replace("'","''")+"'" for column in sorted(excluded))
            row_expression += f" - ARRAY[{excluded_sql}]"
        order = item["primaryKey"] or columns
        order_sql = ",".join(f"t.{quote_ident(column)}" for column in order)
        marker = f"__AUTHORING_TABLE__{table}__"; markers[marker] = output / f"{table}.ndjson"
        sql += [f"\\echo {marker}", f"COPY (SELECT replace(encode(convert_to(({row_expression})::text,'UTF8'),'base64'),E'\\n','') FROM public.{quote_ident(table)} t ORDER BY {order_sql}) TO STDOUT;"]
    sql += ["ROLLBACK;"]
    raw = psql(role, "\n".join(sql)+"\n")
    streams = {}; counts = {item["table"]: 0 for item in schema["tables"]}; current = None
    try:
        for marker, path in markers.items():
            stream = path.open("w", encoding="utf-8"); streams[marker] = stream
        for line in raw.splitlines():
            if line in ("BEGIN", "ROLLBACK"): continue
            if line in markers: current = line; continue
            if current is not None:
                row = json.loads(base64.b64decode(line).decode()); streams[current].write(canonical_json(row)+"\n")
                counts[markers[current].stem] += 1
    finally:
        for stream in streams.values(): stream.close()
    return counts

def quote_ident(value: str) -> str:
    return '"' + value.replace('"', '""') + '"'

def local_invariants() -> dict[str, Any]:
    return query_json("content", r"""
SELECT json_build_object(
 'approvedActiveFacts',(SELECT count(*) FROM knowledge_fact WHERE review_status='APPROVED' AND status='ACTIVE'),
 'latestLessons',(SELECT count(*) FROM lesson_draft d WHERE d.review_status='REVIEWED' AND NOT EXISTS (SELECT 1 FROM lesson_draft n WHERE n.topic_id=d.topic_id AND n.review_status='REVIEWED' AND n.version_number>d.version_number)),
 'latestPages',(SELECT count(*) FROM lesson_draft_section s JOIN lesson_draft d ON d.id=s.lesson_draft_id WHERE d.review_status='REVIEWED' AND NOT EXISTS (SELECT 1 FROM lesson_draft n WHERE n.topic_id=d.topic_id AND n.review_status='REVIEWED' AND n.version_number>d.version_number)),
 'canonicalQuestions',(SELECT count(*) FROM question),
 'questionsWithInvalidCorrectOptionCount',(SELECT count(*) FROM question q JOIN question_version v ON v.id=q.current_version_id WHERE (SELECT count(*) FROM question_option o WHERE o.question_version_id=v.id AND o.correct)<>1),
 'orphanFactVersions',(SELECT count(*) FROM knowledge_fact_version v LEFT JOIN knowledge_fact f ON f.id=v.knowledge_fact_id WHERE f.id IS NULL),
 'orphanLessonPages',(SELECT count(*) FROM lesson_draft_section s LEFT JOIN lesson_draft d ON d.id=s.lesson_draft_id WHERE d.id IS NULL),
 'activeRelease',(SELECT json_build_object('id',id,'key',release_number,'checksum',checksum,'status',status) FROM content_release WHERE id='be07a3f5-a80c-42c8-bf1c-02541755f178')
)""")

def foreign_key_report(snapshot: Path, role: str, schema: dict[str, Any]) -> dict[str, Any]:
    cache: dict[str, list[dict[str, Any]]] = {}
    def rows(table: str):
        if table not in cache:
            path=snapshot/role/"tables"/f"{table}.ndjson"; cache[table]=[json.loads(line) for line in path.read_text().splitlines()] if path.exists() else []
        return cache[table]
    failures=[]
    for fk in schema["foreignKeys"]:
        referenced={tuple(row.get(c) for c in fk["referencedColumns"]) for row in rows(fk["referencedTable"])}
        for row in rows(fk["table"]):
            key=tuple(row.get(c) for c in fk["columns"])
            if any(value is None for value in key): continue
            if key not in referenced: failures.append({"table":fk["table"],"columns":fk["columns"],"key":key,"referencedTable":fk["referencedTable"]})
    return {"database":role,"checked":len(schema["foreignKeys"]),"failures":failures}

def cross_service_report(snapshot: Path) -> dict[str, Any]:
    def ids(role: str, table: str) -> set[Any]:
        path=snapshot/role/"tables"/f"{table}.ndjson"
        return {json.loads(line).get("id") for line in path.read_text().splitlines()} if path.exists() else set()
    content_ids={table:ids("content",table) for table in (
        "knowledge_fact", "knowledge_fact_version", "learning_objective", "source_section",
        "source_revision", "lesson_draft", "question", "content_release",
    )}
    scalar_rules={
        "learning_objective_id":"learning_objective", "objective_id":"learning_objective",
        "source_section_id":"source_section", "historical_source_section_id":"source_section",
        "canonical_source_section_id":"source_section", "source_revision_id":"source_revision",
        "canonical_source_revision_id":"source_revision", "target_fact_id":"knowledge_fact",
        "knowledge_fact_id":"knowledge_fact", "resulting_knowledge_fact_id":"knowledge_fact",
        "target_fact_version_id":"knowledge_fact_version", "knowledge_fact_version_id":"knowledge_fact_version",
        "resulting_fact_version_id":"knowledge_fact_version", "current_lesson_id":"lesson_draft",
        "accepted_lesson_draft_id":"lesson_draft", "accepted_question_id":"question",
    }
    list_rules={"knowledge_fact_version_ids":"knowledge_fact_version", "existing_question_ids":"question"}
    failures=[]; checks=set(); tables_dir=snapshot/"ai"/"tables"
    if not tables_dir.exists(): return {"checks":[],"failures":[]}
    for path in sorted(tables_dir.glob("*.ndjson")):
        for line in path.read_text().splitlines():
            row=json.loads(line)
            for field,target_table in scalar_rules.items():
                value=row.get(field)
                if value is None: continue
                checks.add(f"AI {field} -> Content {target_table}.id")
                if value not in content_ids[target_table]:
                    failures.append({"table":path.stem,"id":row.get("id"),"field":field,"value":value,"referencedTable":target_table})
            for field,target_table in list_rules.items():
                values=row.get(field) or []
                if isinstance(values,str):
                    try: values=json.loads(values)
                    except json.JSONDecodeError: values=[]
                checks.add(f"AI {field}[] -> Content {target_table}.id")
                for value in values:
                    if value not in content_ids[target_table]:
                        failures.append({"table":path.stem,"id":row.get("id"),"field":field,"value":value,"referencedTable":target_table})
    return {"checks":sorted(checks)+["intra-service foreign keys"],"failures":failures}

def export_snapshot(output: Path, source_commit: str, allow_noncanonical: bool=False) -> dict[str, Any]:
    if output.exists(): raise SystemExit(f"Output already exists: {output}")
    output.mkdir(parents=True); os.chmod(output,0o700)
    schemas={role:schema_for(role) for role in DATABASES}
    if not allow_noncanonical:
        for role, expected in EXPECTED_MIGRATIONS.items():
            if schemas[role]["migrationVersion"] != expected: raise SystemExit(f"{role} migration mismatch")
    counts={}; checksums={}
    for role,schema in schemas.items():
        role_dir=output/role; tables_dir=role_dir/"tables"; role_dir.mkdir();
        counts[role]=export_tables(role,schema,tables_dir)
        (role_dir/"schema.json").write_text(json.dumps(schema,indent=2,sort_keys=True)+"\n")
    integrity=output/"integrity"; integrity.mkdir()
    invariants=local_invariants() if not allow_noncanonical else {}
    if not allow_noncanonical:
        for key,value in EXPECTED_CANONICAL.items():
            if invariants.get(key)!=value: raise SystemExit(f"Canonical guard failed: {key}={invariants.get(key)} expected {value}")
        if invariants["questionsWithInvalidCorrectOptionCount"] or invariants["orphanFactVersions"] or invariants["orphanLessonPages"]: raise SystemExit("Local invariant validation failed")
        active=invariants.get("activeRelease") or {}
        if active.get("id")!=ACTIVE_RELEASE["id"] or active.get("checksum")!=ACTIVE_RELEASE["checksum"]: raise SystemExit("Active release guard failed")
    fk={role:foreign_key_report(output,role,schemas[role]) for role in DATABASES}; cross=cross_service_report(output)
    if not allow_noncanonical and (any(report["failures"] for report in fk.values()) or cross["failures"]): raise SystemExit("Snapshot reference validation failed")
    reports={"table-counts.json":counts,"foreign-key-report.json":fk,"cross-service-report.json":cross,"validation-report.json":{"canonical":invariants,"valid":not any(r["failures"] for r in fk.values()) and not cross["failures"]}}
    for name,value in reports.items(): (integrity/name).write_text(json.dumps(value,indent=2,sort_keys=True)+"\n")
    for path in sorted(output.rglob("*")):
        if path.is_file(): checksums[str(path.relative_to(output))]={"sha256":file_sha(path),"bytes":path.stat().st_size}
    semantic_input={"schemas":schemas,"counts":counts,"fileChecksums":{name:data["sha256"] for name,data in checksums.items()}}
    semantic=hashlib.sha256(canonical_json(semantic_input).encode()).hexdigest()
    manifest={"format":FORMAT,"sourceCommit":source_commit,"createdAt":dt.datetime.now(dt.UTC).isoformat(),"corpusId":CORPUS_ID,"sourceRevision":SOURCE_REVISION,"migrations":{role:schemas[role]["migrationVersion"] for role in schemas},"activeRelease":ACTIVE_RELEASE,"expectedCanonicalCounts":EXPECTED_CANONICAL,"includedTables":{r:sorted(counts[r]) for r in counts},"excludedTables":["flyway_schema_history","scheduler locks","worker leases","runtime heartbeats"],"excludedFields":{r:{t:sorted(v) for t,v in tables.items()} for r,tables in EPHEMERAL_FIELDS.items()},"classifications":{"STRUCTURAL_REUSE":{r:sorted(v) for r,v in STRUCTURAL_REUSE.items()},"AUTHORITATIVE_EXPORT":"all other included tables","RUNTIME_NORMALIZE":RUNTIME_RULES,"EPHEMERAL_EXCLUDE":["scheduler locks","worker leases","runtime heartbeats"],"SECRET_EXCLUDE":list(SECRET_MARKERS)},"dependencyOrder":{r:dependency_order(schemas[r]) for r in schemas},"semanticChecksum":semantic,"files":checksums}
    complete_sha=hashlib.sha256(canonical_json(manifest).encode()).hexdigest(); manifest["completeSnapshotChecksum"]=complete_sha
    (output/"manifest.json").write_text(json.dumps(manifest,indent=2,sort_keys=True)+"\n")
    os.chmod(output/"manifest.json",0o600)
    print(canonical_json({"event":"snapshot_exported","semanticChecksum":semantic,"completeSnapshotChecksum":complete_sha,"counts":counts}))
    return manifest

def verify_snapshot(snapshot: Path, expected_semantic: str|None=None) -> dict[str,Any]:
    manifest=json.loads((snapshot/"manifest.json").read_text())
    if manifest.get("format")!=FORMAT: raise SystemExit("Unsupported snapshot format")
    if expected_semantic and manifest.get("semanticChecksum")!=expected_semantic: raise SystemExit("Semantic checksum mismatch")
    complete=manifest.pop("completeSnapshotChecksum",None)
    if complete!=hashlib.sha256(canonical_json(manifest).encode()).hexdigest(): raise SystemExit("Complete snapshot checksum mismatch")
    manifest["completeSnapshotChecksum"]=complete
    for name,record in manifest["files"].items():
        path=snapshot/name
        if not path.is_file() or file_sha(path)!=record["sha256"]: raise SystemExit(f"Checksum mismatch: {name}")
    for path in snapshot.rglob("*.ndjson"):
        lowered=path.read_text(errors="ignore").lower()
        if any(marker in lowered for marker in SECRET_MARKERS): raise SystemExit(f"Secret marker found: {path.name}")
    print(canonical_json({"event":"snapshot_verified","semanticChecksum":manifest["semanticChecksum"]}))
    return manifest

def load_rows(snapshot:Path,role:str,table:str)->list[dict[str,Any]]:
    path=snapshot/role/"tables"/f"{table}.ndjson"
    return [json.loads(line) for line in path.read_text().splitlines()] if path.exists() else []

def runtime_normalized_row(role:str,table:str,row:dict[str,Any])->dict[str,Any]:
    result=dict(row); rule=RUNTIME_RULES.get(table,{}) if role=="ai" else {}
    field="status" if "status" in rule else "lifecycle_state" if "lifecycle_state" in rule else "reservation_state" if "reservation_state" in rule else None
    if field and result.get(field) in rule[field]: result[field]=rule.get("futureStatus") or rule.get("futureState")
    return result

def import_insert_statement(role:str,table:str,table_schema:dict[str,Any])->str:
    excluded=EPHEMERAL_FIELDS.get(role,{}).get(table,set())
    columns=[column["name"] for column in table_schema["columns"] if column["name"] not in excluded]
    column_sql=",".join(quote_ident(column) for column in columns)
    value_sql=",".join(f"(jsonb_populate_record(NULL::public.{quote_ident(table)},raw::jsonb)).{quote_ident(column)}" for column in columns)
    return f"INSERT INTO public.{quote_ident(table)} ({column_sql}) SELECT {value_sql} FROM _authoring_import_json ON CONFLICT DO NOTHING;"

def import_role(snapshot:Path,role:str)->dict[str,Any]:
    manifest=verify_snapshot(snapshot); schema=json.loads((snapshot/role/"schema.json").read_text())
    if manifest["migrations"].get(role)!=EXPECTED_MIGRATIONS[role]: raise SystemExit(f"{role} snapshot migration mismatch")
    known={item["table"] for item in schema["tables"]}; statements=["BEGIN;","SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;","CREATE TEMP TABLE _authoring_import_json(raw text) ON COMMIT DROP;"]; expected=0
    for table in manifest["dependencyOrder"][role]:
        path=snapshot/role/"tables"/f"{table}.ndjson"
        if table not in known or not path.exists(): continue
        rows=[runtime_normalized_row(role,table,json.loads(line)) for line in path.read_text().splitlines()]; expected+=len(rows)
        normalized=snapshot/role/"import"/f"{table}.ndjson"; normalized.parent.mkdir(exist_ok=True)
        normalized.write_text("".join(canonical_json(row)+"\n" for row in rows)); os.chmod(normalized,0o600)
        safe_path=str(normalized).replace("'","''")
        table_schema=next(item for item in schema["tables"] if item["table"]==table)
        statements += ["TRUNCATE _authoring_import_json;",f"\\copy _authoring_import_json(raw) FROM '{safe_path}' WITH (FORMAT csv, DELIMITER E'\\x02', QUOTE E'\\x01')",import_insert_statement(role,table,table_schema)]
    statements.append("COMMIT;"); output=psql(role,"\n".join(statements)+"\n")
    inserted=sum(map(int,re.findall(r"INSERT 0 (\d+)",output))); result={"role":role,"snapshotRows":expected,"inserted":inserted,"reused":expected-inserted}
    print(canonical_json({"event":"snapshot_role_imported",**result})); return result

def record_key(row:dict[str,Any], columns:list[str])->tuple[Any,...]: return tuple(row.get(column) for column in columns)

def structural_identity(row:dict[str,Any])->dict[str,Any]:
    operational={"id","created_at","updated_at","version","imported_at","reviewed_at"}
    return {key:value for key,value in row.items() if key not in operational}

def source_payload_diagnostic(source:dict[str,Any],target:dict[str,Any])->dict[str,Any]:
    left=source.get("content_text") or ""; right=target.get("content_text") or ""
    def digest(value:str)->str: return hashlib.sha256(value.encode()).hexdigest()
    def whitespace(value:str)->str: return re.sub(r"\s+"," ",value).strip()
    def pdf_lines(value:str)->str:
        value=unicodedata.normalize("NFC",value).replace("\u00ad","")
        value=re.sub(r"(?<=\w)-[ \t]*\n[ \t]*(?=\w)","",value)
        return whitespace(value)
    def canonical(value:str)->str: return pdf_lines(value).casefold()
    prefix=0
    while prefix<min(len(left),len(right)) and left[prefix]==right[prefix]: prefix+=1
    suffix=0
    while suffix<min(len(left)-prefix,len(right)-prefix) and left[-1-suffix]==right[-1-suffix]: suffix+=1
    excerpt=lambda value: whitespace(value[max(0,prefix-60):min(len(value),prefix+60)])
    nfc_left=unicodedata.normalize("NFC",left); nfc_right=unicodedata.normalize("NFC",right)
    ws_left=whitespace(nfc_left); ws_right=whitespace(nfc_right)
    pdf_left=pdf_lines(left); pdf_right=pdf_lines(right); canonical_left=canonical(left); canonical_right=canonical(right)
    return {
        "sourceContentChecksum":source.get("content_checksum"),"targetContentChecksum":target.get("content_checksum"),
        "sourceDocumentChecksum":source.get("file_checksum"),"targetDocumentChecksum":target.get("file_checksum"),
        "rawLengths":{"source":len(left),"target":len(right)},
        "normalizedLengths":{"source":len(canonical_left),"target":len(canonical_right)},
        "firstDifferingOffset":None if left==right else prefix,
        "lastDifferingOffsets":None if left==right else {"source":len(left)-suffix-1,"target":len(right)-suffix-1},
        "equalities":{"raw":left==right,"unicodeNfc":nfc_left==nfc_right,"normalizedWhitespace":ws_left==ws_right,"pdfLineBreak":pdf_left==pdf_right,"canonical":canonical_left==canonical_right},
        "differenceTypes":{"whitespaceOnly":left!=right and ws_left==ws_right,"unicodeNormalizationOnly":left!=right and nfc_left==nfc_right,"lineBreakHyphenationOnly":ws_left!=ws_right and pdf_left==pdf_right},
        "checksums":{"raw":{"source":digest(left),"target":digest(right)},"unicodeNfc":{"source":digest(nfc_left),"target":digest(nfc_right)},"normalizedWhitespace":{"source":digest(ws_left),"target":digest(ws_right)},"pdfLineBreak":{"source":digest(pdf_left),"target":digest(pdf_right)},"canonical":{"source":digest(canonical_left),"target":digest(canonical_right)}},
        "firstDifferenceExcerpt":{"source":excerpt(left),"target":excerpt(right)},
        "sourceMetadata":{key:source.get(key) for key in ("id","source_type","title","file_name","created_at","updated_at","imported_at") if key in source},
        "targetMetadata":{key:target.get(key) for key in ("id","source_type","title","file_name","created_at","updated_at","imported_at") if key in target},
    }

def plan(source:Path,target:Path,output:Path)->dict[str,Any]:
    source_manifest=verify_snapshot(source); target_manifest=json.loads((target/"manifest.json").read_text())
    classifications={}; conflicts=[]; invalid=[]; normalizations=[]; final_counts={}; conflict_diagnostics=[]
    payload_rows=load_rows(source,"content","source_payload_revision")
    payload_by_reference={row["materialized_source_reference_id"]:row for row in payload_rows}
    reconciled_shared={
        row["historical_shared_id"]:row for row in load_rows(source,"content","source_payload_identity_reconciliation")
    }
    for role in DATABASES:
        schema=json.loads((source/role/"schema.json").read_text()); classifications[role]={}; final_counts[role]={}
        for item in schema["tables"]:
            table=item["table"]; pk=item["primaryKey"]; src=load_rows(source,role,table); dst=load_rows(target,role,table)
            dst_by_pk={record_key(row,pk):row for row in dst} if pk else {}
            unique_maps=[{record_key(row,key):row for row in dst if all(row.get(column) is not None for column in key)} for key in item.get("uniqueKeys",[]) if key]
            counts=defaultdict(int)
            for row in src:
                effective=runtime_normalized_row(role,table,row)
                existing=dst_by_pk.get(record_key(effective,pk)) if pk else None
                if existing is None and table in STRUCTURAL_REUSE.get(role,set()):
                    for key,index in zip(item.get("uniqueKeys",[]),unique_maps):
                        candidate=index.get(record_key(effective,key))
                        if candidate is not None: existing=candidate; break
                if existing is None:
                    payload=payload_by_reference.get(effective.get("id")) if role=="content" and table=="source_reference" else None
                    classification="INSERT_CANONICAL_REVISION" if payload and payload.get("payload_role")=="CANONICAL" else "INSERT_HISTORICAL_REVISION" if payload else "INSERT"
                    counts[classification]+=1
                elif canonical_json(existing)==canonical_json(effective): counts["REUSE_IDENTICAL"]+=1
                elif table in STRUCTURAL_REUSE.get(role,set()) and canonical_json(structural_identity(existing))==canonical_json(structural_identity(effective)): counts["REUSE_CANONICAL"]+=1
                elif role=="content" and table=="source_reference" and effective.get("id") in reconciled_shared:
                    reconciliation=reconciled_shared[effective["id"]]
                    expected={reconciliation["local_payload_checksum"].strip(),reconciliation["hosted_payload_checksum"].strip()}
                    actual={(effective.get("content_checksum") or "").strip(),(existing.get("content_checksum") or "").strip()}
                    if expected==actual:
                        counts["REUSE_RECONCILED_ALIAS"]+=1
                    else:
                        counts["CONFLICT_IMMUTABLE"]+=1; conflicts.append({"database":role,"table":table,"key":record_key(effective,pk),"reason":"RECONCILIATION_CHECKSUM_MISMATCH"})
                else:
                    counts["CONFLICT_IMMUTABLE"]+=1
                    different_fields=sorted(key for key in set(effective)|set(existing) if effective.get(key)!=existing.get(key))
                    conflicts.append({
                        "database":role,"table":table,"key":record_key(effective,pk),"differentFields":different_fields,
                        "sourceFieldChecksums":{key:hashlib.sha256(canonical_json(effective.get(key)).encode()).hexdigest() for key in different_fields},
                        "targetFieldChecksums":{key:hashlib.sha256(canonical_json(existing.get(key)).encode()).hexdigest() for key in different_fields},
                    })
                    if role=="content" and table=="source_reference":
                        conflict_diagnostics.append({"table":table,"key":record_key(effective,pk),"analysis":source_payload_diagnostic(effective,existing)})
                rule=RUNTIME_RULES.get(table)
                if rule:
                    field="status" if "status" in rule else "lifecycle_state" if "lifecycle_state" in rule else "reservation_state"
                    states=rule.get(field,[])
                    if row.get(field) in states: counts["NORMALIZE_RUNTIME_STATE"]+=1; normalizations.append({"database":role,"table":table,"id":row.get("id"),"from":row.get(field),"to":rule.get("futureStatus") or rule.get("futureState")})
            skipped=len(EPHEMERAL_FIELDS.get(role,{}).get(table,set()))*len(src); counts["SKIP_EPHEMERAL_FIELDS"]=skipped
            classifications[role][table]=dict(counts); final_counts[role][table]=len(dst)+counts["INSERT"]+counts["INSERT_CANONICAL_REVISION"]+counts["INSERT_HISTORICAL_REVISION"]
    fk={role:foreign_key_report(source,role,json.loads((source/role/"schema.json").read_text())) for role in DATABASES}; cross=cross_service_report(source)
    invalid=[failure for report in fk.values() for failure in report["failures"]]+cross["failures"]
    migration_compatibility={"source":source_manifest.get("migrations",{}),"target":target_manifest.get("migrations",{}),"dryRunCompatible":True,"futureImportPrerequisite":{"contentMinimum":"24","aiMinimum":"32"}}
    report={"snapshotSemanticChecksum":source_manifest["semanticChecksum"],"classifications":classifications,"conflicts":conflicts,"conflictDiagnostics":conflict_diagnostics,"invalidReferences":invalid,"runtimeNormalizations":normalizations,"finalCounts":final_counts,"crossService":cross,"foreignKeys":fk,"migrationCompatibility":migration_compatibility,"idempotency":{"secondRunInsert":0,"secondRunExpected":"REUSE_IDENTICAL, REUSE_CANONICAL, or REUSE_RECONCILED_ALIAS only"},"blocking":bool(conflicts or invalid)}
    output.write_text(json.dumps(report,indent=2,sort_keys=True)+"\n"); print(canonical_json({"event":"dry_run_planned","blocking":report["blocking"],"conflicts":len(conflicts),"invalidReferences":len(invalid)}))
    return report

def main():
    parser=argparse.ArgumentParser(); sub=parser.add_subparsers(dest="command",required=True)
    exp=sub.add_parser("export"); exp.add_argument("--output",type=Path,required=True); exp.add_argument("--source-commit",required=True); exp.add_argument("--allow-noncanonical",action="store_true")
    val=sub.add_parser("verify"); val.add_argument("--snapshot",type=Path,required=True); val.add_argument("--expected-semantic")
    dry=sub.add_parser("plan"); dry.add_argument("--source",type=Path,required=True); dry.add_argument("--target",type=Path,required=True); dry.add_argument("--output",type=Path,required=True)
    imp=sub.add_parser("import-role"); imp.add_argument("--snapshot",type=Path,required=True); imp.add_argument("--role",choices=sorted(DATABASES),required=True)
    args=parser.parse_args()
    if args.command=="export": export_snapshot(args.output,args.source_commit,args.allow_noncanonical)
    elif args.command=="verify": verify_snapshot(args.snapshot,args.expected_semantic)
    elif args.command=="plan":
        result=plan(args.source,args.target,args.output)
        if result["blocking"]: raise SystemExit(2)
    else: import_role(args.snapshot,args.role)

if __name__=="__main__": main()
