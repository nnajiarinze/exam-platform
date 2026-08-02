#!/usr/bin/env python3
"""Approve automated GOOD fact proposals through the normal Content workflow."""

from __future__ import annotations

import argparse
import json
import os
import urllib.error
import urllib.request

MANDATORY_GATES = {
    "exactEvidenceValidated",
    "groundingPassed",
    "atomic",
    "duplicateDetectionPassed",
    "ambiguityChecksPassed",
    "topicMappingPassed",
    "learningObjectiveMappingPassed",
    "schemaValidationPassed",
}


def gates_pass(proposal: dict) -> bool:
    gates = proposal.get("validationGates") or {}
    return (
        proposal.get("automatedClassification") == "GOOD"
        and MANDATORY_GATES.issubset(gates)
        and all(gates[name] is True for name in MANDATORY_GATES)
    )


def approval_eligible(proposal: dict) -> bool:
    return proposal.get("status") == "PROPOSED" and gates_pass(proposal)


def request_json(
    method: str, url: str, body: dict | None = None, headers: dict | None = None
) -> dict | list:
    data = None if body is None else json.dumps(body).encode()
    request = urllib.request.Request(
        url,
        data=data,
        method=method,
        headers={"Content-Type": "application/json", **(headers or {})},
    )
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        detail = error.read().decode(errors="replace")
        raise RuntimeError(f"{method} {url} failed with HTTP {error.code}: {detail}") from error


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--job-id", action="append", required=True)
    parser.add_argument("--ai-url", default="http://localhost:8083")
    parser.add_argument("--content-url", default="http://localhost:8082")
    parser.add_argument("--author", default="automated-fact-author")
    parser.add_argument("--reviewer", default="automated-fact-reviewer")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    ai_key = os.environ.get("AI_INTERNAL_API_KEY", "")
    if not ai_key:
        raise SystemExit("AI_INTERNAL_API_KEY is required")
    ai_headers = {"X-Internal-Api-Key": ai_key}
    author_headers = {"X-Admin-Identity": args.author, "X-Admin-Roles": "ADMIN"}
    reviewer_headers = {
        "X-Admin-Identity": args.reviewer,
        "X-Admin-Roles": "CONTENT_REVIEWER",
    }

    results: list[dict] = []
    for job_id in args.job_id:
        proposals = request_json(
            "GET",
            f"{args.ai_url}/internal/v1/knowledge-fact-generation/jobs/{job_id}/proposals",
            headers=ai_headers,
        )
        for proposal in proposals:
            resumable = (
                proposal.get("status") == "ACCEPTED"
                and proposal.get("resultingKnowledgeFactId")
                and gates_pass(proposal)
            )
            if not approval_eligible(proposal) and not resumable:
                results.append(
                    {
                        "proposalId": proposal["id"],
                        "classification": proposal.get("automatedClassification"),
                        "result": "LEFT_IN_PROPOSAL_OR_REJECTED",
                    }
                )
                continue
            if args.dry_run:
                results.append(
                    {
                        "proposalId": proposal["id"],
                        "classification": "GOOD",
                        "result": "WOULD_APPROVE",
                    }
                )
                continue
            if resumable:
                fact = request_json(
                    "GET",
                    f"{args.content_url}/api/v1/admin/knowledge-facts/{proposal['resultingKnowledgeFactId']}",
                    headers=author_headers,
                )
            else:
                fact = request_json(
                    "POST",
                    f"{args.content_url}/api/v1/admin/ai/knowledge-fact-proposals/{proposal['id']}/accept",
                    {"version": proposal["version"]},
                    author_headers,
                )
            if fact["reviewStatus"] in {"UNREVIEWED", "REJECTED", "REQUIRES_UPDATE"}:
                try:
                    submitted = request_json(
                        "POST",
                        f"{args.content_url}/api/v1/admin/knowledge-facts/{fact['id']}/submit",
                        {"version": fact["version"], "reason": "Automated mandatory gates passed"},
                        author_headers,
                    )
                except RuntimeError as error:
                    results.append(
                        {
                            "proposalId": proposal["id"],
                            "classification": "GOOD",
                            "result": "CONTENT_VALIDATION_PENDING",
                            "knowledgeFactId": fact["id"],
                            "diagnostic": str(error),
                        }
                    )
                    continue
                fact = submitted
            if fact["reviewStatus"] == "UNDER_REVIEW":
                try:
                    fact = request_json(
                        "POST",
                        f"{args.content_url}/api/v1/admin/knowledge-facts/{fact['id']}/approve",
                        {
                            "version": fact["version"],
                            "reason": "All automated Knowledge Fact validation gates passed",
                        },
                        reviewer_headers,
                    )
                except RuntimeError as error:
                    results.append(
                        {
                            "proposalId": proposal["id"],
                            "classification": "GOOD",
                            "result": "CONTENT_VALIDATION_PENDING",
                            "knowledgeFactId": fact["id"],
                            "diagnostic": str(error),
                        }
                    )
                    continue
            results.append(
                {
                    "proposalId": proposal["id"],
                    "classification": "GOOD",
                    "result": "APPROVED",
                    "knowledgeFactId": fact["id"],
                }
            )
    print(json.dumps(results, ensure_ascii=False, sort_keys=True))


if __name__ == "__main__":
    main()
