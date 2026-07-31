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
        }
        for topic in subject["topics"]:
            chapter["lessonDrafts"] += int(scalar(url, f"SELECT count(*) FROM lesson_draft WHERE topic_id='{topic['id']}'"))
            chapter["reviewedLessons"] += int(scalar(url, f"SELECT count(*) FROM lesson_draft WHERE topic_id='{topic['id']}' AND review_status='REVIEWED'"))
            for objective in topic["learningObjectives"]:
                objective_id = objective["id"]
                chapter["objectives"] += 1
                chapter["sourceSections"] += len(objective["sourceSectionIds"])
                mapped = int(scalar(url, f"SELECT count(*) FROM learning_objective_source_section WHERE learning_objective_id='{objective_id}'"))
                chapter["mappedObjectives"] += int(mapped > 0)
                proposals = int(scalar(ai_url, f"SELECT count(*) FROM ai_knowledge_fact_proposal WHERE learning_objective_id='{objective_id}'"))
                approved = int(scalar(url, f"SELECT count(*) FROM knowledge_fact WHERE learning_objective_id='{objective_id}' AND review_status='APPROVED'"))
                questions = int(scalar(url, f"SELECT count(*) FROM question WHERE learning_objective_id='{objective_id}'"))
                chapter["factProposals"] += proposals
                chapter["approvedFacts"] += approved
                chapter["canonicalQuestions"] += questions
                if not mapped or not approved or not questions:
                    chapter["uncoveredObjectives"].append(objective["code"])
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
