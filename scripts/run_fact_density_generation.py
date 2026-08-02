#!/usr/bin/env python3
"""Resume bounded missing-Fact generation from an immutable density audit."""

from __future__ import annotations

import argparse
import json
import os
import time
import urllib.error
import urllib.request
from pathlib import Path

try:
    from scripts.fact_density_audit import query_snapshot
except ModuleNotFoundError:  # Direct execution places scripts/ on sys.path.
    from fact_density_audit import query_snapshot


TERMINAL = {"COMPLETED", "PARTIALLY_COMPLETED", "FAILED", "CANCELLED"}


def instruction(audit: dict, section: dict, targets: list[str]) -> str:
    return json.dumps(
        {
            "mode": "MISSING_FACTS_ONLY",
            "auditId": audit["auditId"],
            "auditChecksum": audit["definitionChecksum"],
            "sourceSectionId": section["id"],
            "sourceSectionChecksum": section["checksum"],
            "existingApprovedFacts": [fact["text"] for fact in section["approvedFacts"]],
            "forbiddenDuplicatePropositions": [fact["text"] for fact in section["approvedFacts"]],
            "missingTeachingConceptTargets": targets,
            "maximumCandidateCount": len(targets),
            "rules": "One atomic independently testable Swedish proposition per target; exact evidence only; preserve qualifiers; no outside knowledge.",
        },
        ensure_ascii=False,
        separators=(",", ":"),
    )


def request_json(method: str, url: str, key: str, body: dict | None = None) -> dict:
    request = urllib.request.Request(
        url,
        data=None if body is None else json.dumps(body, ensure_ascii=False).encode(),
        method=method,
        headers={"Content-Type": "application/json", "X-Internal-Api-Key": key},
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        detail = error.read().decode(errors="replace")
        raise RuntimeError(f"{method} {url} failed with HTTP {error.code}: {detail}") from error


def write_state(path: Path, state: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(state, ensure_ascii=False, indent=2, sort_keys=True) + "\n")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--audit", type=Path, default=Path("content/sverige-i-fokus/fact-density-audit-v1.json"))
    parser.add_argument("--state", type=Path, default=Path("content/sverige-i-fokus/fact-density-generation-v1.json"))
    parser.add_argument("--ai-url", default="http://localhost:8083")
    parser.add_argument("--poll-seconds", type=float, default=2)
    parser.add_argument("--job-timeout-seconds", type=int, default=600)
    args = parser.parse_args()
    key = os.environ.get("AI_INTERNAL_API_KEY", "")
    if not key:
        raise SystemExit("AI_INTERNAL_API_KEY is required")
    audit = json.loads(args.audit.read_text())
    source = {value["id"]: value for value in query_snapshot()["sections"]}
    state = json.loads(args.state.read_text()) if args.state.exists() else {
        "auditId": audit["auditId"], "auditChecksum": audit["definitionChecksum"], "jobs": []
    }
    if state["auditId"] != audit["auditId"] or state["auditChecksum"] != audit["definitionChecksum"]:
        raise SystemExit("Generation state does not match immutable audit")
    completed_keys = {job["idempotencyKey"] for job in state["jobs"] if job.get("status") in TERMINAL}
    for section in audit["sections"]:
        targets = section["generationTargets"]
        for offset in range(0, len(targets), 3):
            batch = targets[offset : offset + 3]
            key_suffix = f"fact-density:{audit['definitionChecksum']}:{section['id']}:{offset // 3}"
            if key_suffix in completed_keys:
                continue
            current = source[section["id"]]
            payload = {
                "sourceId": section["sourceReferenceId"],
                "sourceSectionId": section["id"],
                "sourceTitle": section["title"],
                "sourceContent": current["exactText"],
                "sourceContentChecksum": section["checksum"],
                "learningObjectiveId": section["objectiveId"],
                "learningObjectiveTitle": section["objective"],
                "requestedBy": "codex-fact-density-generation",
                "requestedCount": len(batch),
                "language": "sv-SE",
                "editorialInstruction": instruction(audit, section, batch),
                "idempotencyKey": key_suffix,
            }
            try:
                job = request_json("POST", f"{args.ai_url}/internal/v1/knowledge-fact-generation/jobs", key, payload)
            except RuntimeError as error:
                if "AI_RATE_LIMIT_EXCEEDED" in str(error):
                    state["resumePoint"] = key_suffix
                    state["pauseReason"] = "AI_RATE_LIMIT_EXCEEDED"
                    write_state(args.state, state)
                    raise SystemExit("AI_RATE_LIMIT_EXCEEDED") from error
                raise
            deadline = time.monotonic() + args.job_timeout_seconds
            while job["status"] not in TERMINAL:
                if time.monotonic() >= deadline:
                    raise SystemExit(f"Job {job['id']} did not reach a terminal state within the bounded timeout")
                time.sleep(args.poll_seconds)
                job = request_json("GET", f"{args.ai_url}/internal/v1/knowledge-fact-generation/jobs/{job['id']}", key)
                if job.get("errorCode") in {"AI_ALL_FREE_PROVIDERS_UNAVAILABLE", "PAID_BUDGET_EXHAUSTED"}:
                    state["resumePoint"] = key_suffix
                    write_state(args.state, state)
                    raise SystemExit(job["errorCode"])
            state["jobs"].append({"idempotencyKey": key_suffix, "sectionId": section["id"], "targets": batch, **job})
            state["resumePoint"] = None
            state["pauseReason"] = None
            write_state(args.state, state)
            print(json.dumps({"jobId": job["id"], "section": section["topic"], "status": job["status"], "generated": job["generatedCount"]}, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    main()
