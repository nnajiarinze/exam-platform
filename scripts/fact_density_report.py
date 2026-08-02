#!/usr/bin/env python3
"""Build the deterministic post-generation sufficiency and impact preview."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

try:
    from scripts.fact_density_audit import build, lesson_readiness, query_snapshot
except ModuleNotFoundError:
    from fact_density_audit import build, lesson_readiness, query_snapshot


WORDS_PER_MINUTE = 140


def page_ceiling(facts: int) -> int:
    return 6 if facts >= 5 else 5 if facts == 4 else 4 if facts == 3 else 3 if facts == 2 else 2


def learner_question(fact: str) -> str:
    statement = fact.rstrip(".?!")
    return f"Hur skulle du förklara att {statement[:1].lower() + statement[1:]}?"


def report(before: dict, after: dict) -> dict:
    current = {section["id"]: section for section in after["sections"]}
    topics = []
    for old in before["sections"]:
        new = current[old["id"]]
        previous_count, final_count = old["approvedFactCount"], new["approvedFactCount"]
        remaining = len(new["remainingUnusedClaims"])
        ceiling = page_ceiling(final_count)
        added = [fact for fact in new["approvedFacts"] if fact["factId"] not in {value["factId"] for value in old["approvedFacts"]}]
        topics.append({
            "chapter": old["chapter"], "topic": old["topic"], "objective": old["objective"],
            "previousApprovedFactCount": previous_count, "newApprovedFactCount": final_count - previous_count,
            "finalApprovedFactCount": final_count, "remainingUncoveredSourceConcepts": remaining,
            "maximumSafelySupportableLessonPages": ceiling,
            "estimatedUsefulReadingTimeMinutes": [round(ceiling * 55 / WORDS_PER_MINUTE, 1), round(ceiling * 90 / WORDS_PER_MINUTE, 1)],
            "sufficiency": lesson_readiness(final_count, remaining),
            "potentialNewPages": max(0, ceiling - 4),
            "proposedLearnerQuestions": [learner_question(value["text"]) for value in added],
            "existingLessonPagesMayRemain": True,
        })
    potential_pages = sum(value["potentialNewPages"] for value in topics)
    return {
        "policy": {"wordsPerMinute": WORDS_PER_MINUTE, "potentialPageWordRange": [55, 90]},
        "previousFactTotal": sum(value["previousApprovedFactCount"] for value in topics),
        "finalFactTotal": sum(value["finalApprovedFactCount"] for value in topics),
        "newApprovedFactTotal": sum(value["newApprovedFactCount"] for value in topics),
        "topicsReadyForDeeperLesson": sum(value["sufficiency"] == "READY_FOR_DEEPER_LESSON" for value in topics),
        "topicsLimitedButUsable": sum(value["sufficiency"] == "LIMITED_BUT_USABLE" for value in topics),
        "topicsRequiringSourceExpansion": sum(value["sufficiency"] == "SOURCE_EXPANSION_REQUIRED" for value in topics),
        "projectedAdditionalPages": potential_pages,
        "projectedCorpusWordCount": [7312 + potential_pages * 55, 7312 + potential_pages * 90],
        "projectedReadingTimeMinutes": [round((7312 + potential_pages * 55) / WORDS_PER_MINUTE, 1), round((7312 + potential_pages * 90) / WORDS_PER_MINUTE, 1)],
        "topics": topics,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--before", type=Path, default=Path("content/sverige-i-fokus/fact-density-audit-v1.json"))
    parser.add_argument("--output", type=Path, default=Path("content/sverige-i-fokus/fact-density-impact-preview-v1.json"))
    args = parser.parse_args()
    result = report(json.loads(args.before.read_text()), build(query_snapshot()))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({key: result[key] for key in ("previousFactTotal", "finalFactTotal", "newApprovedFactTotal", "topicsReadyForDeeperLesson", "topicsLimitedButUsable", "topicsRequiringSourceExpansion", "projectedAdditionalPages", "projectedReadingTimeMinutes")}, sort_keys=True))


if __name__ == "__main__":
    main()
