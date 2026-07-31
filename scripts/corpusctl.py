#!/usr/bin/env python3
"""Guarded corpus backup, inspection, reset, import, and coverage utility."""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
import stat
import subprocess
import sys
import tempfile
from pathlib import Path
from urllib.parse import urlparse

CORPUS = "sverige-i-fokus-v1"
ROLES = ("content", "learning", "ai")
URL_ENV = {role: f"CORPUS_{role.upper()}_DATABASE_URL" for role in ROLES}
PG_BIN = Path("/opt/homebrew/opt/postgresql@18/bin")
FACT_GATE_POLICY_VERSION = "coverage-safety-v1"


def topic_lesson_sufficiency(topic: dict) -> dict:
    approved = int(topic.get("approvedFacts", 0))
    distinct = int(topic.get("distinctApprovedFacts", 0))
    attempted = int(topic.get("sourceSectionsAttempted", 0))
    sections = int(topic.get("sourceSections", 0))
    source_characters = int(topic.get("sourceCharacters", 0))
    testable = int(topic.get("reasonablyTestableFacts", 0))
    reasons = []
    if approved == 0:
        reasons.append("NO_APPROVED_ACTIVE_FACT")
    if distinct < approved:
        reasons.append("DUPLICATE_APPROVED_FACTS")
    if attempted < sections:
        reasons.append("SOURCE_SECTIONS_NOT_ATTEMPTED")
    if testable == 0:
        reasons.append("NO_REASONABLY_TESTABLE_FACT")
    if approved and distinct == approved and attempted == sections and testable:
        if distinct >= 2:
            status = "SUFFICIENT"
        elif source_characters >= 500:
            status = "LIMITED_BUT_USABLE"
            reasons.append("SINGLE_FACT_WITH_SUBSTANTIAL_GROUNDED_SOURCE")
        else:
            status = "INSUFFICIENT"
            reasons.append("TOO_LITTLE_DISTINCT_LESSON_MATERIAL")
    else:
        status = "INSUFFICIENT"
    return {"status": status, "reasons": reasons}


def fact_efficiency(total: int, accepted: int, rejected: int, pending: int,
                    classifications: dict, failed_sections: int, source_sections: int,
                    approved_grounding_failures: int) -> dict:
    duplicates = int(classifications.get("DUPLICATE", 0))
    novel_denominator = max(0, total - duplicates)
    return {
        "acceptedCandidates": accepted, "rejectedCandidates": rejected, "pendingCandidates": pending,
        "rawApprovalRate": accepted / total if total else None,
        "novelContentApprovalRate": accepted / novel_denominator if novel_denominator else None,
        "rejectionRate": rejected / total if total else None,
        "duplicateRate": duplicates / total if total else None,
        "needsSplitRate": int(classifications.get("NEEDS_SPLIT", 0)) / total if total else None,
        "needsRewriteRate": int(classifications.get("NEEDS_REWRITE", 0)) / total if total else None,
        "ambiguityRate": int(classifications.get("AMBIGUOUS", 0)) / total if total else None,
        "groundingFailureRate": approved_grounding_failures / accepted if accepted else None,
        "providerFailureRate": failed_sections / source_sections if source_sections else None,
        "classifications": classifications,
    }


def evaluate_fact_gate(chapter: dict) -> dict:
    coverage = chapter["coverage"]
    safety = chapter["safety"]
    efficiency = chapter["efficiency"]
    systematic = chapter["systematicFailures"]
    topics = chapter["topicSufficiency"]
    reasons = []
    warnings = []
    safety_failures = {
        "UNSUPPORTED_APPROVED_FACT": safety.get("unsupportedApprovedFacts", 0),
        "APPROVED_FACT_GROUNDING_FAILURE": safety.get("approvedGroundingFailures", 0),
        "APPROVED_FACT_EVIDENCE_FAILURE": safety.get("approvedEvidenceFailures", 0),
        "APPROVED_FACT_SOURCE_SECTION_MISMATCH": safety.get("sourceSectionMismatches", 0),
        "APPROVED_FACT_CHECKSUM_MISMATCH": safety.get("checksumMismatches", 0),
        "INVALID_TOPIC_MAPPING": safety.get("topicMappingFailures", 0),
        "INVALID_OBJECTIVE_MAPPING": safety.get("objectiveMappingFailures", 0),
        "MISSING_APPROVED_FACT_PROVENANCE": safety.get("missingProvenance", 0),
        "LINEAGE_OR_AUDIT_DEFECT": safety.get("lineageOrAuditFailures", 0),
        "FREE_ONLY_NOT_CONFIRMED": 0 if safety.get("freeOnlyConfirmed", True) else 1,
    }
    reasons.extend(code for code, count in safety_failures.items() if count)
    reasons.extend(systematic.get("reasons", []))
    if reasons:
        status = "BLOCKED"
    else:
        if coverage["topicsCovered"] < coverage["topicsTotal"]:
            reasons.append("UNCOVERED_TOPIC")
        if coverage["objectivesCovered"] < coverage["objectivesTotal"]:
            reasons.append("UNCOVERED_OBJECTIVE")
        if coverage["sourceSectionsAttempted"] < coverage["sourceSectionsTotal"]:
            reasons.append("UNATTEMPTED_SOURCE_SECTION")
        insufficient = [item["topicCode"] for item in topics if item["status"] == "INSUFFICIENT"]
        if insufficient:
            reasons.append("INSUFFICIENT_LESSON_MATERIAL:" + ",".join(insufficient))
        if reasons:
            status = "PARTIAL"
        else:
            approval_rate = efficiency.get("novelContentApprovalRate")
            if approval_rate is not None and approval_rate < 0.5:
                warnings.append("LOW_GENERATION_EFFICIENCY")
            elif approval_rate is not None and approval_rate < 0.8:
                warnings.append("REVIEW_RECOMMENDED")
            if any(item["status"] == "LIMITED_BUT_USABLE" for item in topics):
                warnings.append("LIMITED_BUT_USABLE_TOPIC")
            if efficiency.get("rejectedCandidates", 0):
                warnings.append("ISOLATED_REJECTED_ALTERNATIVES")
            status = "PASS_WITH_WARNING" if warnings else "PASS"
    if status == "BLOCKED":
        warning_level = "PIPELINE_DEFECT_SUSPECTED"
    elif "LOW_GENERATION_EFFICIENCY" in warnings:
        warning_level = "LOW_GENERATION_EFFICIENCY"
    elif warnings or status == "PARTIAL":
        warning_level = "REVIEW_RECOMMENDED"
    else:
        warning_level = "HEALTHY"
    return {
        "policyVersion": FACT_GATE_POLICY_VERSION,
        "status": status,
        "warningLevel": warning_level,
        "progressionAllowed": status in {"PASS", "PASS_WITH_WARNING"},
        "reasons": reasons,
        "warnings": list(dict.fromkeys(warnings)),
        "resumePoint": "CHAPTER_LESSON_PLANNING" if status in {"PASS", "PASS_WITH_WARNING"} else "CHAPTER_FACT_CHECKPOINT",
    }


def log(event: str, **fields: object) -> None:
    print(json.dumps({"timestamp": dt.datetime.now(dt.UTC).isoformat(), "event": event, **fields}, sort_keys=True))


def tool(name: str) -> str:
    candidate = PG_BIN / name
    if candidate.exists():
        return str(candidate)
    raise SystemExit(f"PostgreSQL 18 tool is required: {candidate}")


def normalized_url(value: str) -> str:
    return value.removeprefix("jdbc:")


def target(role: str, environment: str) -> tuple[str, str]:
    value = os.environ.get(URL_ENV[role], "")
    if not value:
        raise SystemExit(f"Missing {URL_ENV[role]}")
    parsed = urlparse(normalized_url(value))
    database = parsed.path.lstrip("/").split("?", 1)[0]
    if database != role:
        raise SystemExit(f"Refusing {role} operation: database name is {database!r}")
    host = (parsed.hostname or "").lower()
    if not host:
        raise SystemExit("Database host is missing")
    forbidden = ("us-east", "useast", "render.com")
    if any(marker in host for marker in forbidden):
        raise SystemExit(f"Refusing forbidden or retired host for {role}")
    if environment == "hosted":
        expected = os.environ.get("CORPUS_EXPECTED_HOSTED_HOST_SHA256", "")
        actual = hashlib.sha256(host.encode()).hexdigest()
        if not expected or actual != expected:
            raise SystemExit(f"Hosted target fingerprint verification failed for {role}")
        if host in {"localhost", "127.0.0.1"}:
            raise SystemExit("Hosted operation cannot target localhost")
    elif host not in {"localhost", "127.0.0.1"}:
        raise SystemExit(f"Local operation cannot target remote host {host!r}")
    return normalized_url(value), hashlib.sha256(f"{host}/{database}".encode()).hexdigest()


def run(command: list[str], *, input_text: str | None = None, capture: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(command, input=input_text, text=True, capture_output=capture, check=True)


def scalar(url: str, query: str) -> str:
    return run([tool("psql"), url, "-XAt", "-v", "ON_ERROR_STOP=1", "-c", query]).stdout.strip()


def counts(role: str, url: str) -> dict[str, int]:
    query = """SELECT json_object_agg(tablename,n)::text FROM (
      SELECT tablename,(xpath('/row/c/text()',query_to_xml(format('SELECT count(*) c FROM %I',tablename),false,true,'')))[1]::text::bigint n
      FROM pg_tables WHERE schemaname='public' AND tablename<>'flyway_schema_history' ORDER BY tablename
    ) x;"""
    raw = scalar(url, query)
    return {key: int(value) for key, value in json.loads(raw or "{}").items()}


def inspect(args: argparse.Namespace) -> None:
    result = {}
    for role in ROLES:
        url, fingerprint = target(role, args.environment)
        result[role] = {"targetFingerprint": fingerprint, "counts": counts(role, url)}
    log("inspection_complete", environment=args.environment, corpus=CORPUS, databases=result)


def backup(args: argparse.Namespace) -> None:
    output = args.backup_dir.resolve()
    output.mkdir(parents=True, exist_ok=True, mode=0o700)
    os.chmod(output, 0o700)
    recipient = os.environ.get("CORPUS_AGE_RECIPIENT", "")
    identity = os.environ.get("CORPUS_AGE_IDENTITY_FILE", "")
    if not recipient or not identity or not Path(identity).is_file():
        raise SystemExit("CORPUS_AGE_RECIPIENT and a valid CORPUS_AGE_IDENTITY_FILE are required")
    manifest = {"environment": args.environment, "corpus": CORPUS, "createdAt": dt.datetime.now(dt.UTC).isoformat(), "archives": {}}
    for role in ROLES:
        url, fingerprint = target(role, args.environment)
        archive = output / f"{args.environment}-{role}-{dt.datetime.now(dt.UTC).strftime('%Y%m%dT%H%M%SZ')}.dump.age"
        dump = subprocess.Popen([tool("pg_dump"), "--format=custom", "--no-owner", "--no-acl", url], stdout=subprocess.PIPE)
        encrypted = subprocess.run(["age", "-r", recipient, "-o", str(archive)], stdin=dump.stdout, capture_output=True, text=False)
        assert dump.stdout
        dump.stdout.close()
        dump_status = dump.wait()
        if dump_status or encrypted.returncode:
            archive.unlink(missing_ok=True)
            raise SystemExit(f"Backup failed for {role}")
        os.chmod(archive, 0o600)
        with tempfile.NamedTemporaryFile(prefix=f"{role}-", suffix=".dump") as clear:
            decrypted = subprocess.run(["age", "-d", "-i", identity, str(archive)], stdout=clear, stderr=subprocess.PIPE)
            if decrypted.returncode:
                raise SystemExit(f"Backup decryption validation failed for {role}")
            clear.flush()
            run([tool("pg_restore"), "--list", clear.name])
        digest = hashlib.sha256(archive.read_bytes()).hexdigest()
        manifest["archives"][role] = {
            "path": str(archive),
            "sha256": digest,
            "bytes": archive.stat().st_size,
            "mode": stat.filemode(archive.stat().st_mode),
            "targetFingerprint": fingerprint,
            "counts": counts(role, url),
            "validatedWithPgRestore18": True,
        }
        log("backup_validated", environment=args.environment, database=role, path=str(archive), sha256=digest, bytes=archive.stat().st_size)
    manifest_path = output / f"{args.environment}-verified-backup.json"
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n")
    os.chmod(manifest_path, 0o600)
    log("backup_set_complete", manifest=str(manifest_path))


PRESERVE = {
    "content": {"flyway_schema_history", "audit_event"},
    "learning": {"flyway_schema_history", "learner_profile", "learner_settings"},
    "ai": {"flyway_schema_history", "ai_audit_event", "ai_quota_profile", "ai_model_price_profile", "ai_provider_circuit"},
}


def verify_backup(args: argparse.Namespace) -> dict:
    manifest = json.loads(args.verified_backup.read_text())
    created = dt.datetime.fromisoformat(manifest["createdAt"])
    if dt.datetime.now(dt.UTC) - created > dt.timedelta(hours=24):
        raise SystemExit("Verified backup is older than 24 hours")
    if manifest["environment"] != args.environment or manifest["corpus"] != CORPUS:
        raise SystemExit("Verified backup does not match target environment/corpus")
    identity = os.environ.get("CORPUS_AGE_IDENTITY_FILE", "")
    for role in ROLES:
        url, fingerprint = target(role, args.environment)
        record = manifest["archives"].get(role, {})
        path = Path(record.get("path", ""))
        if record.get("targetFingerprint") != fingerprint or not path.is_file():
            raise SystemExit(f"Verified backup target/path mismatch for {role}")
        if hashlib.sha256(path.read_bytes()).hexdigest() != record.get("sha256"):
            raise SystemExit(f"Verified backup checksum mismatch for {role}")
        with tempfile.NamedTemporaryFile(prefix=f"verify-{role}-", suffix=".dump") as clear:
            decrypted = subprocess.run(["age", "-d", "-i", identity, str(path)], stdout=clear, stderr=subprocess.PIPE)
            if decrypted.returncode:
                raise SystemExit(f"Verified backup cannot be decrypted for {role}")
            clear.flush()
            run([tool("pg_restore"), "--list", clear.name])
    return manifest


def reset(args: argparse.Namespace) -> None:
    verify_backup(args)
    if args.dry_run:
        inspect(args)
        log("reset_dry_run_complete", environment=args.environment)
        return
    for role in ROLES:
        url, _ = target(role, args.environment)
        preserve = ",".join("'" + name + "'" for name in sorted(PRESERVE[role]))
        sql_text = f"""DO $$
        DECLARE names text;
        BEGIN
          SELECT string_agg(format('%I.%I',schemaname,tablename),',') INTO names
          FROM pg_tables WHERE schemaname='public' AND tablename NOT IN ({preserve});
          IF names IS NOT NULL THEN EXECUTE 'TRUNCATE TABLE ' || names || ' CASCADE'; END IF;
        END $$;"""
        run([tool("psql"), url, "-X", "-v", "ON_ERROR_STOP=1", "-c", sql_text])
        if scalar(url, "SELECT count(*) FROM flyway_schema_history") == "0":
            raise SystemExit(f"Flyway history was unexpectedly cleared in {role}")
        log("database_reset", environment=args.environment, database=role, preserved=sorted(PRESERVE[role]))


def import_corpus(args: argparse.Namespace) -> None:
    url, _ = target("content", args.environment)
    generated = run([sys.executable, "scripts/sverige_i_fokus_sql.py"]).stdout
    if args.dry_run:
        log("import_dry_run_complete", environment=args.environment, sqlBytes=len(generated.encode()))
        return
    run([tool("psql"), url, "-X", "-v", "ON_ERROR_STOP=1"], input_text=generated)
    result = {
        "sources": scalar(url, "SELECT count(*) FROM source_reference WHERE file_checksum='39a93261cc64af0122e186b7d67f57dffad573576570956a4754d22ce776aada'"),
        "sections": scalar(url, "SELECT count(*) FROM source_section"),
        "subjects": scalar(url, "SELECT count(*) FROM subject"),
        "topics": scalar(url, "SELECT count(*) FROM topic"),
        "objectives": scalar(url, "SELECT count(*) FROM learning_objective"),
    }
    log("corpus_import_complete", environment=args.environment, **result)


def coverage(args: argparse.Namespace) -> None:
    url, _ = target("content", args.environment)
    query = """SELECT json_build_object(
      'sources',(SELECT count(*) FROM source_reference),
      'sections',(SELECT count(*) FROM source_section),
      'subjects',(SELECT count(*) FROM subject),
      'topics',(SELECT count(*) FROM topic),
      'objectives',(SELECT count(*) FROM learning_objective),
      'mappedObjectives',(SELECT count(DISTINCT learning_objective_id) FROM learning_objective_source_section),
      'approvedFacts',(SELECT count(*) FROM knowledge_fact WHERE review_status='APPROVED'),
      'lessonDrafts',(SELECT count(*) FROM lesson_draft),
      'reviewedLessons',(SELECT count(*) FROM lesson_draft WHERE review_status='REVIEWED'),
      'canonicalQuestions',(SELECT count(*) FROM question)
    )::text;"""
    result = json.loads(scalar(url, query))
    ai_url, _ = target("ai", args.environment)
    result.update(
        {
            "factProposals": int(scalar(ai_url, "SELECT count(*) FROM ai_knowledge_fact_proposal")),
            "factGenerationFailures": int(scalar(ai_url, "SELECT count(*) FROM ai_generation_job WHERE job_type='KNOWLEDGE_FACT' AND status='FAILED'")),
            "inputTokens": int(scalar(ai_url, "SELECT coalesce(sum(input_tokens),0) FROM ai_generation_job")),
            "outputTokens": int(scalar(ai_url, "SELECT coalesce(sum(output_tokens),0) FROM ai_generation_job")),
        }
    )
    manifest = json.loads(Path("content/sverige-i-fokus/curriculum-manifest.yaml").read_text())
    chapters = []
    for subject in manifest["subjects"]:
        chapter = {
            "chapterCode": subject["code"],
            "chapter": subject["name"],
            "sourceSections": 0,
            "topics": len(subject["topics"]),
            "objectives": 0,
            "mappedObjectives": 0,
            "factProposals": 0,
            "approvedFacts": 0,
            "lessonDrafts": 0,
            "reviewedLessons": 0,
            "canonicalQuestions": 0,
            "uncoveredObjectives": [],
            "uncoveredFactObjectives": [],
            "uncoveredQuestionObjectives": [],
            "topicsCovered": 0,
            "objectivesCovered": 0,
            "sourceSectionsAttempted": 0,
            "topicSufficiency": [],
        }
        for topic in subject["topics"]:
            topic_approved = 0
            topic_distinct = 0
            topic_testable = 0
            topic_sections = set()
            topic_attempted = set()
            topic_source_characters = 0
            chapter["lessonDrafts"] += int(scalar(url, f"SELECT count(*) FROM lesson_draft WHERE topic_id='{topic['id']}'"))
            chapter["reviewedLessons"] += int(scalar(url, f"SELECT count(*) FROM lesson_draft WHERE topic_id='{topic['id']}' AND review_status='REVIEWED'"))
            for objective in topic["learningObjectives"]:
                objective_id = objective["id"]
                section_ids = set(objective["sourceSectionIds"])
                topic_sections.update(section_ids)
                chapter["objectives"] += 1
                chapter["sourceSections"] += len(section_ids)
                mapped = int(scalar(url, f"SELECT count(*) FROM learning_objective_source_section WHERE learning_objective_id='{objective_id}'"))
                chapter["mappedObjectives"] += int(mapped > 0)
                proposals = int(scalar(ai_url, f"SELECT count(*) FROM ai_knowledge_fact_proposal WHERE learning_objective_id='{objective_id}'"))
                approved = int(scalar(url, f"SELECT count(*) FROM knowledge_fact WHERE learning_objective_id='{objective_id}' AND review_status='APPROVED' AND status='ACTIVE'"))
                distinct = int(scalar(url, f"SELECT count(DISTINCT lower(trim(canonical_statement))) FROM knowledge_fact WHERE learning_objective_id='{objective_id}' AND review_status='APPROVED' AND status='ACTIVE'"))
                testable = int(scalar(url, f"SELECT count(*) FROM knowledge_fact WHERE learning_objective_id='{objective_id}' AND review_status='APPROVED' AND status='ACTIVE' AND length(trim(canonical_statement))>=20 AND array_length(regexp_split_to_array(trim(canonical_statement),'\\s+'),1)>=3"))
                questions = int(scalar(url, f"SELECT count(*) FROM question WHERE learning_objective_id='{objective_id}'"))
                attempted_ids = set(filter(None, scalar(ai_url, f"SELECT coalesce(string_agg(DISTINCT source_section_id::text,','),'') FROM ai_generation_job WHERE job_type='KNOWLEDGE_FACT' AND learning_objective_id='{objective_id}'").split(",")))
                topic_attempted.update(section_ids & attempted_ids)
                if section_ids:
                    ids_sql = ",".join(f"'{value}'" for value in sorted(section_ids))
                    topic_source_characters += int(scalar(url, f"SELECT coalesce(sum(length(exact_text)),0) FROM source_section WHERE id IN ({ids_sql})"))
                chapter["factProposals"] += proposals
                chapter["approvedFacts"] += approved
                chapter["canonicalQuestions"] += questions
                topic_approved += approved
                topic_distinct += distinct
                topic_testable += testable
                if approved:
                    chapter["objectivesCovered"] += 1
                else:
                    chapter["uncoveredFactObjectives"].append(objective["code"])
                if not questions:
                    chapter["uncoveredQuestionObjectives"].append(objective["code"])
                if not mapped or not approved or not questions:
                    chapter["uncoveredObjectives"].append(objective["code"])
            if topic_approved:
                chapter["topicsCovered"] += 1
            chapter["sourceSectionsAttempted"] += len(topic_attempted)
            sufficiency_input = {
                "approvedFacts": topic_approved,
                "distinctApprovedFacts": topic_distinct,
                "reasonablyTestableFacts": topic_testable,
                "sourceSections": len(topic_sections),
                "sourceSectionsAttempted": len(topic_attempted),
                "sourceCharacters": topic_source_characters,
            }
            chapter["topicSufficiency"].append({
                "topicCode": topic["code"],
                "topicName": topic["name"],
                **sufficiency_input,
                **topic_lesson_sufficiency(sufficiency_input),
            })

        objective_ids = [objective["id"] for topic in subject["topics"] for objective in topic["learningObjectives"]]
        ids_sql = ",".join(f"'{value}'" for value in objective_ids)
        classification_rows = json.loads(scalar(ai_url, f"""SELECT coalesce(json_object_agg(classification,n),'{{}}'::json)::text FROM (
          SELECT coalesce(automated_classification,'UNCLASSIFIED') classification,count(*) n
          FROM ai_knowledge_fact_proposal WHERE learning_objective_id IN ({ids_sql}) GROUP BY automated_classification
        ) x""") or "{}")
        accepted = int(scalar(ai_url, f"SELECT count(*) FROM ai_knowledge_fact_proposal WHERE learning_objective_id IN ({ids_sql}) AND status='ACCEPTED'"))
        rejected = int(scalar(ai_url, f"SELECT count(*) FROM ai_knowledge_fact_proposal WHERE learning_objective_id IN ({ids_sql}) AND status IN ('REJECTED','DISCARDED')"))
        pending = int(scalar(ai_url, f"SELECT count(*) FROM ai_knowledge_fact_proposal WHERE learning_objective_id IN ({ids_sql}) AND status IN ('PROPOSED','EDITED')"))
        failed_sections = int(scalar(ai_url, f"""SELECT count(*) FROM (
          SELECT source_section_id FROM ai_generation_job WHERE job_type='KNOWLEDGE_FACT' AND learning_objective_id IN ({ids_sql})
          GROUP BY source_section_id HAVING bool_or(status='FAILED') AND NOT bool_or(status IN ('COMPLETED','PARTIALLY_COMPLETED'))
        ) x"""))
        schema_failed_sections = int(scalar(ai_url, f"""SELECT count(DISTINCT source_section_id) FROM ai_generation_job
          WHERE job_type='KNOWLEDGE_FACT' AND learning_objective_id IN ({ids_sql}) AND status='FAILED'
          AND error_code IN ('AI_PROVIDER_RESPONSE_INVALID','AI_PROVIDER_SCHEMA_INVALID')"""))
        free_only_violations = int(scalar(ai_url, f"""SELECT
          (SELECT count(*) FROM ai_generation_job WHERE job_type='KNOWLEDGE_FACT' AND learning_objective_id IN ({ids_sql}) AND coalesce(reported_cost,0)>0)
          + (SELECT count(*) FROM ai_provider_attempt a JOIN ai_generation_job j ON j.id=a.job_id
             WHERE j.job_type='KNOWLEDGE_FACT' AND j.learning_objective_id IN ({ids_sql}) AND NOT a.confirmed_free)
          + (SELECT count(*) FROM ai_provider_routing_decision r JOIN ai_generation_job j ON j.id=r.job_id
             WHERE j.job_type='KNOWLEDGE_FACT' AND j.learning_objective_id IN ({ids_sql}) AND r.billing_policy<>'FREE_ONLY')"""))
        approved_ids = scalar(url, f"SELECT coalesce(string_agg(id::text,','),'') FROM knowledge_fact WHERE learning_objective_id IN ({ids_sql}) AND review_status='APPROVED' AND status='ACTIVE'").split(",")
        approved_ids = [value for value in approved_ids if value]
        approved_ids_sql = ",".join(f"'{value}'" for value in approved_ids) or "NULL"
        missing_provenance = int(scalar(url, f"""SELECT count(*) FROM knowledge_fact k LEFT JOIN knowledge_fact_ai_provenance p ON p.knowledge_fact_version_id=k.current_version_id
          WHERE k.id IN ({approved_ids_sql}) AND p.knowledge_fact_version_id IS NULL"""))
        evidence_failures = int(scalar(url, f"""SELECT count(*) FROM knowledge_fact k JOIN knowledge_fact_ai_provenance p ON p.knowledge_fact_version_id=k.current_version_id
          WHERE k.id IN ({approved_ids_sql}) AND jsonb_array_length(p.source_evidence)=0"""))
        source_mismatches = int(scalar(url, f"""SELECT count(*) FROM knowledge_fact k JOIN knowledge_fact_ai_provenance p ON p.knowledge_fact_version_id=k.current_version_id
          WHERE k.id IN ({approved_ids_sql}) AND (p.source_section_id IS NULL OR NOT EXISTS (
            SELECT 1 FROM learning_objective_source_section m WHERE m.learning_objective_id=k.learning_objective_id AND m.source_section_id=p.source_section_id))"""))
        checksum_mismatches = int(scalar(url, f"""SELECT count(*) FROM knowledge_fact k JOIN knowledge_fact_ai_provenance p ON p.knowledge_fact_version_id=k.current_version_id JOIN source_section ss ON ss.id=p.source_section_id
          WHERE k.id IN ({approved_ids_sql}) AND p.source_content_checksum<>ss.section_checksum"""))
        unsupported = int(scalar(ai_url, f"""SELECT count(*) FROM ai_knowledge_fact_proposal WHERE resulting_knowledge_fact_id IN ({approved_ids_sql})
          AND (automated_classification='UNSUPPORTED' OR coalesce((validation_gates->>'groundingPassed')::boolean,false)=false)"""))
        chapter["coverage"] = {
            "topicsCovered": chapter["topicsCovered"], "topicsTotal": chapter["topics"],
            "objectivesCovered": chapter["objectivesCovered"], "objectivesTotal": chapter["objectives"],
            "sourceSectionsAttempted": chapter["sourceSectionsAttempted"], "sourceSectionsTotal": chapter["sourceSections"],
        }
        chapter["safety"] = {
            "unsupportedApprovedFacts": unsupported,
            "approvedGroundingFailures": unsupported,
            "approvedEvidenceFailures": evidence_failures,
            "sourceSectionMismatches": source_mismatches,
            "checksumMismatches": checksum_mismatches,
            "topicMappingFailures": 0,
            "objectiveMappingFailures": max(0, chapter["objectives"] - chapter["mappedObjectives"]),
            "missingProvenance": missing_provenance,
            "lineageOrAuditFailures": 0,
            "freeOnlyConfirmed": free_only_violations == 0,
        }
        chapter["efficiency"] = fact_efficiency(
            chapter["factProposals"], accepted, rejected, pending, classification_rows,
            failed_sections, chapter["sourceSections"], unsupported)
        systematic_reasons = []
        if chapter["efficiency"]["providerFailureRate"] is not None and chapter["efficiency"]["providerFailureRate"] > .2:
            systematic_reasons.append("SYSTEMATIC_PROVIDER_FAILURE")
        if chapter["sourceSections"] and schema_failed_sections / chapter["sourceSections"] > .2:
            systematic_reasons.append("SYSTEMATIC_PROVIDER_SCHEMA_FAILURE")
        chapter["systematicFailures"] = {"detected": bool(systematic_reasons), "reasons": systematic_reasons}
        chapter["factGate"] = evaluate_fact_gate(chapter)
        chapters.append(chapter)
    result["chapters"] = chapters
    log("coverage", environment=args.environment, coverage=result)


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser()
    sub = result.add_subparsers(dest="command", required=True)
    for name in ("inspect", "backup", "reset", "import", "coverage"):
        item = sub.add_parser(name)
        item.add_argument("--environment", choices=("local", "hosted"), required=True)
        if name == "backup":
            item.add_argument("--backup-dir", type=Path, required=True)
        if name == "reset":
            item.add_argument("--verified-backup", type=Path, required=True)
            item.add_argument("--require-verified-backup", action="store_true", required=True)
            item.add_argument("--dry-run", action="store_true")
        if name == "import":
            item.add_argument("--dry-run", action="store_true")
    return result


def main() -> None:
    args = parser().parse_args()
    {"inspect": inspect, "backup": backup, "reset": reset, "import": import_corpus, "coverage": coverage}[args.command](args)


if __name__ == "__main__":
    main()
