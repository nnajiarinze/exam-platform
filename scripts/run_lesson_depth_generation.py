#!/usr/bin/env python3
"""Generate and claim-validate only ADD/REPLACE lesson-depth pages."""

from __future__ import annotations

import argparse
import json
import subprocess
import time
import urllib.error
import urllib.request
import uuid
from collections import defaultdict
from pathlib import Path


def internal_key(container: str) -> str:
    raw = subprocess.run(["docker", "inspect", container, "--format", "{{json .Config.Env}}"],
                         check=True, capture_output=True, text=True).stdout
    for item in json.loads(raw):
        if item.startswith("AI_INTERNAL_API_KEY="):
            return item.split("=", 1)[1]
    raise RuntimeError("AI_INTERNAL_API_KEY is not configured")


def request(base: str, key: str, method: str, path: str, payload: dict | None = None) -> dict | list:
    data = None if payload is None else json.dumps(payload, ensure_ascii=False).encode()
    req = urllib.request.Request(base + path, data=data, method=method,
                                 headers={"Content-Type": "application/json", "X-Internal-Api-Key": key})
    try:
        with urllib.request.urlopen(req, timeout=130) as response:
            return json.loads(response.read())
    except urllib.error.HTTPError as error:
        body = error.read().decode(errors="replace")
        raise RuntimeError(f"{method} {path} failed with HTTP {error.code}: {body[:1000]}") from error


def source_for(topic: dict, section_id: str) -> dict:
    return next(section for section in topic["sourceSections"] if section["id"] == section_id)


def generation_groups(topic: dict) -> list[tuple[str, list[dict]]]:
    groups: dict[str, list[dict]] = defaultdict(list)
    for plan in topic["candidatePagePlans"]:
        groups[plan["sourceSections"][0]["id"]].append(plan)
    return sorted(groups.items())


def create_job(base: str, key: str, audit: dict, topic: dict, section_id: str, plans: list[dict], retry: int) -> dict:
    section = source_for(topic, section_id)
    facts = {}
    for plan in plans:
        for fact in plan["assignedFacts"]:
            facts[fact["versionId"]] = fact
    neighbouring = topic["currentTitles"] + [plan["title"] for plan in plans]
    payload = {
        "topicId": topic["topicId"], "topicTitle": topic["topicTitle"],
        "learningObjectiveId": topic["objectiveId"], "learningObjectiveTitle": topic["objectiveText"],
        "sourceSectionId": section_id, "sourceSectionTitle": section["title"],
        "sourceSectionChecksum": section["checksum"], "exactSourceText": section["exactText"],
        "facts": [{"id": fact["id"], "versionId": fact["versionId"], "text": fact["statement"],
                   "sourceSectionId": section_id} for fact in facts.values()],
        "plan": [{"pageType": plan["pageType"], "title": plan["title"],
                  "knowledgeFactVersionIds": plan["knowledgeFactVersionIds"],
                  "learnerQuestion": plan["learnerQuestion"], "pagePurpose": plan["pagePurpose"],
                  "exactSupportingEvidence": plan["exactSupportingEvidence"],
                  "allowedConcepts": plan["allowedConcepts"], "forbiddenConcepts": plan["forbiddenConcepts"],
                  "neighbouringPageTitles": neighbouring} for plan in plans],
        "language": "sv", "requestedBy": "codex-lesson-depth-expansion",
        "idempotencyKey": f"{audit['definitionChecksum']}:{topic['topicId']}:{section_id}:attempt:{retry + 1}",
        "generationMode": "DEPTH_PAGE_SET",
        "depthTopicPlanId": str(uuid.uuid5(uuid.UUID(audit["auditId"]), "topic:" + topic["topicId"])),
    }
    return request(base, key, "POST", "/internal/v1/lesson-generation/jobs", payload)


def wait_job(base: str, key: str, job_id: str) -> dict:
    deadline = time.monotonic() + 900
    while time.monotonic() < deadline:
        job = request(base, key, "GET", f"/internal/v1/lesson-generation/jobs/{job_id}")
        if job["status"] in {"COMPLETED", "FAILED"}:
            return job
        time.sleep(2)
    raise RuntimeError(f"Lesson generation job {job_id} exceeded bounded wait")


def latest_revisions(inspect: dict) -> dict[int, dict]:
    latest: dict[int, dict] = {}
    for revision in inspect["revisions"]:
        index = revision["pageIndex"]
        if index not in latest or revision["revisionNumber"] > latest[index]["revisionNumber"]:
            latest[index] = revision
    return latest


def validate_with_repairs(base: str, key: str, proposal_id: str, count: int) -> dict:
    inspect = None
    for index in range(count):
        inspect = request(base, key, "POST", f"/internal/v1/lesson-generation/proposals/{proposal_id}/pages/{index}/validate",
                          {"actor": "codex-lesson-depth-expansion"})
        for attempt in range(2):
            revision = latest_revisions(inspect)[index]
            if revision["status"] == "VALIDATED":
                break
            inspect = request(base, key, "POST", f"/internal/v1/lesson-generation/proposals/{proposal_id}/pages/{index}/repair",
                              {"actor": "codex-lesson-depth-expansion",
                               "reason": "Answer the immutable learner question with distinct grounded teaching value in 70-160 words.",
                               "idempotencyKey": f"lesson-depth:{proposal_id}:{index}:{attempt + 1}"})
        if latest_revisions(inspect)[index]["status"] != "VALIDATED":
            raise RuntimeError(f"Page {index} in proposal {proposal_id} exhausted bounded repairs")
    return inspect


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--audit", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--base-url", default="http://localhost:8083")
    args = parser.parse_args()
    audit = json.loads(args.audit.read_text())
    state = json.loads(args.output.read_text()) if args.output.exists() else {"auditId": audit["auditId"], "topics": {}}
    if state["auditId"] != audit["auditId"]:
        raise RuntimeError("Existing generation state belongs to a different immutable audit")
    key = internal_key("exam-platform-ai-service-1")
    for topic in audit["topics"]:
        topic_state = state["topics"].setdefault(topic["topicId"], {"groups": []})
        completed = {(group["sourceSectionId"], tuple(group["planChecksums"])) for group in topic_state["groups"]
                     if group.get("status") == "VALIDATED"}
        for section_id, plans in generation_groups(topic):
            identity = (section_id, tuple(plan["planChecksum"] for plan in plans))
            if identity in completed:
                continue
            prior_failures = sum(1 for group in topic_state["groups"]
                                 if group["sourceSectionId"] == section_id
                                 and tuple(group["planChecksums"]) == identity[1]
                                 and group.get("status") == "FAILED")
            if prior_failures >= 2:
                raise RuntimeError(f"Page set {identity} exhausted bounded generation retries")
            job = create_job(args.base_url, key, audit, topic, section_id, plans, prior_failures)
            job = wait_job(args.base_url, key, str(job["id"]))
            if job["status"] != "COMPLETED":
                topic_state["groups"].append({"sourceSectionId": section_id, "planChecksums": list(identity[1]),
                                               "job": job, "status": "FAILED"})
                args.output.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n")
                raise RuntimeError(f"Lesson generation stopped safely: {job.get('errorCode')}")
            proposals = request(args.base_url, key, "GET", f"/internal/v1/lesson-generation/jobs/{job['id']}/proposals")
            proposal = proposals[0]
            inspected = validate_with_repairs(args.base_url, key, proposal["id"], len(plans))
            topic_state["groups"].append({"sourceSectionId": section_id, "planChecksums": list(identity[1]),
                                           "jobId": job["id"], "proposalId": proposal["id"],
                                           "inspection": inspected, "status": "VALIDATED"})
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n")
            print(json.dumps({"topic": topic["topicTitle"], "sourceSectionId": section_id,
                              "pages": len(plans), "status": "VALIDATED"}, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    main()
