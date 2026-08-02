#!/usr/bin/env python3
"""Create and persist the immutable full-corpus Lesson Regeneration v2 plan."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import uuid
from pathlib import Path

try:
    from scripts.lesson_depth_expansion import checksum, persistence_sql, reading_seconds, words
except ModuleNotFoundError:
    from lesson_depth_expansion import checksum, persistence_sql, reading_seconds, words


ACTOR = "codex-lesson-regeneration-v2"
CORPUS = "sverige-i-fokus-v1"
SOURCE_REVISION = "sverige-i-fokus-source-v2"


def evidence_quotes(fact: dict) -> list[str]:
    result = []
    for value in fact.get("sourceEvidence") or []:
        quote = value.get("quote") if isinstance(value, dict) else value
        if quote and str(quote).strip():
            result.append(re.sub(r"\s+", " ", str(quote)).strip())
    return result


def evidence_order(fact: dict, sections: dict[str, dict]) -> tuple:
    section = sections[fact["sourceSectionId"]]
    positions = [section["exactText"].find(quote) for quote in evidence_quotes(fact)]
    valid = [value for value in positions if value >= 0]
    return section["id"], min(valid, default=10**9), fact["versionId"]


def target_pages(fact_count: int, impact_ceiling: int) -> int:
    if fact_count < 1:
        raise ValueError("A Lesson cannot be planned without an approved Fact")
    return min(6, impact_ceiling, fact_count)


def balanced_groups(values: list[dict], count: int) -> list[list[dict]]:
    quotient, remainder = divmod(len(values), count)
    result, offset = [], 0
    for index in range(count):
        size = quotient + (1 if index < remainder else 0)
        result.append(values[offset : offset + size])
        offset += size
    return result


def page_kind(index: int, total: int, statements: str) -> str:
    if index == 0:
        return "INTRODUCTION"
    if index == total - 1:
        return "SUMMARY"
    low = statements.casefold()
    if re.search(r"\b(ansvar|ansvarar|myndighet|kommun|region|riksdag|regering)\b", low):
        return "RESPONSIBILITY"
    if re.search(r"\b(år \d{4}|först|sedan|därefter|under \d{4}|blev)\b", low):
        return "CHRONOLOGY"
    if re.search(r"\b(betyder|innebär|kallas|är en)\b", low):
        return "KEY_TERMS"
    return "CORE_CONCEPT"


def title(kind: str, topic: str, facts: list[dict], index: int) -> str:
    if kind == "INTRODUCTION":
        return f"Introduktion: {topic}"
    if kind == "SUMMARY":
        return f"Kom ihåg: {topic}"
    lead = " ".join(words(facts[0]["statement"])[:7]).rstrip(".,:;")
    prefix = {"RESPONSIBILITY": "Ansvar", "CHRONOLOGY": "Utveckling", "KEY_TERMS": "Begrepp"}.get(kind, "Fördjupning")
    return f"{prefix}: {lead[:1].upper() + lead[1:]}"


def learner_question(kind: str, topic: str, facts: list[dict]) -> str:
    statement = facts[0]["statement"].rstrip(".?!")
    if kind == "INTRODUCTION":
        return f"Vad innebär det att {statement[:1].lower() + statement[1:]}?"
    if kind == "SUMMARY":
        return f"Vad ska du komma ihåg om att {statement[:1].lower() + statement[1:]}?"
    if kind == "RESPONSIBILITY":
        return f"Vem ansvarar och hur ser ansvaret ut inom {topic.lower()}?"
    if kind == "CHRONOLOGY":
        return f"Hur utvecklades detta inom {topic.lower()}?"
    if kind == "KEY_TERMS":
        return f"Vad betyder det centrala begreppet inom {topic.lower()}?"
    return f"Hur kan du förklara att {statement[:1].lower() + statement[1:]}?"


def build(snapshot: list[dict], impact: dict) -> dict:
    impact_by_topic = {value["topic"]: value for value in impact["topics"]}
    topics = []
    for row in snapshot:
        sections = {value["id"]: value for value in row["sourceSections"]}
        facts = sorted(row["facts"], key=lambda value: evidence_order(value, sections))
        expected = impact_by_topic[row["topicTitle"]]["finalApprovedFactCount"]
        if len(facts) != expected:
            raise RuntimeError(f"{row['topicTitle']}: expected {expected} immutable Facts, found {len(facts)}")
        page_count = target_pages(len(facts), impact_by_topic[row["topicTitle"]]["maximumSafelySupportableLessonPages"])
        groups = balanced_groups(facts, page_count)
        plans = []
        for index, assigned in enumerate(groups):
            statement_text = " ".join(value["statement"] for value in assigned)
            kind = page_kind(index, page_count, statement_text)
            page_title = title(kind, row["topicTitle"], assigned, index)
            question = learner_question(kind, row["topicTitle"], assigned)
            quotes = [quote for fact in assigned for quote in evidence_quotes(fact)]
            section_id = assigned[0]["sourceSectionId"]
            if any(value["sourceSectionId"] != section_id for value in assigned):
                raise RuntimeError(f"{row['topicTitle']}: one page cannot cross immutable Source Sections")
            immutable = {
                "pageType": kind, "title": page_title, "pageOrder": index,
                "learnerQuestion": question,
                "pagePurpose": "Teach exactly one coherent learner concept using only the assigned Facts and exact Source evidence.",
                "knowledgeFactVersionIds": [value["versionId"] for value in assigned],
                "assignedFacts": assigned,
                "sourceSections": [{"id": section_id, "checksum": sections[section_id]["checksum"]}],
                "exactSupportingEvidence": quotes,
                "allowedConcepts": sorted({word for word in words(statement_text) if len(word) >= 6}, key=str.casefold)[:16],
                "forbiddenConcepts": ["outside knowledge", "unstated causality", "invented example", "generic importance claim", "second teaching target"],
                "relationshipToExistingPages": "Fully replaces the previous page set in Lesson Regeneration v2.",
                "estimatedWordRange": {"minimum": 70, "maximum": 160},
                "estimatedReadingSecondsRange": {"minimum": 30, "maximum": 69},
            }
            immutable["planChecksum"] = checksum(immutable)
            plans.append(immutable)
        for index, plan in enumerate(plans):
            transition = (f"Nästa sida förklarar: {plans[index + 1]['title']}." if index + 1 < len(plans)
                          else f"Du har nu gått igenom {row['topicTitle']}.")
            plan["expectedTransition"] = transition
            plan["pagePurpose"] = (
                "Teach exactly one coherent learner concept using only the assigned Facts and exact Source evidence. "
                f"Begin with 'Fråga: {plan['learnerQuestion']}'. Include 'Kom ihåg:' and one to three grounded bullets. "
                f"End exactly with '{transition}'"
            )
            plan["planChecksum"] = checksum({key: value for key, value in plan.items() if key != "planChecksum"})
        old_words = sum(len(words(page["explanation"])) for page in row["pages"])
        topic = {
            "chapter": row["chapter"], "topicId": row["topicId"], "topicTitle": row["topicTitle"],
            "objectiveId": row["objectiveId"], "objectiveText": row["objectiveText"],
            "currentLessonId": row["lessonId"], "currentLessonVersion": row["lessonVersion"],
            "currentPageCount": len(row["pages"]), "currentPageIds": [page["id"] for page in row["pages"]],
            "currentPages": row["pages"], "currentTitles": [page["title"] for page in row["pages"]],
            "currentPagePurposes": [page["title"] for page in row["pages"]],
            "assignedFactVersionIds": [value["versionId"] for value in facts],
            "assignedSourceSectionIds": sorted(sections), "sourceSections": row["sourceSections"],
            "sourceRevision": SOURCE_REVISION, "normalizedSourceLength": sum(len(words(value["normalizedText"])) for value in row["sourceSections"]),
            "currentWordCount": old_words, "estimatedReadingSeconds": reading_seconds(" ".join(page["explanation"] for page in row["pages"])),
            "factCoverage": {"assigned": len(facts), "approved": len(facts)}, "repeatedClaims": [],
            "missingTeachableConcepts": [], "maximumSafelySupportablePageCount": page_count,
            "classification": "LESSON_REGENERATION_V2",
            "pageActions": [{"pageId": page["id"], "action": "SUPERSEDE_WITH_DEEPER_PAGE", "targetOrder": index}
                            for index, page in enumerate(row["pages"])],
            "candidatePagePlans": plans, "plannedPageCount": page_count,
            "boundedSourceException": page_count < 4,
        }
        topic["auditChecksum"] = checksum(topic)
        topics.append(topic)
    body = {"corpusId": CORPUS, "sourceRevision": SOURCE_REVISION, "planVersion": "lesson-regeneration-v2.2",
            "factCorpusChecksum": hashlib.sha256("\n".join(sorted(value for topic in topics for value in topic["assignedFactVersionIds"])).encode()).hexdigest(),
            "readingWordsPerMinute": 140, "topics": topics}
    body["auditId"] = str(uuid.uuid5(uuid.NAMESPACE_URL, "lesson-regeneration-v2:" + checksum(body)))
    body["definitionChecksum"] = checksum(body)
    return body


def snapshot() -> list[dict]:
    sql = Path(__file__).with_name("lesson_regeneration_v2_snapshot.sql").read_text()
    output = subprocess.run(["docker", "compose", "exec", "-T", "content-database", "psql", "-U", "content", "-d", "content", "-Atc", sql], check=True, capture_output=True, text=True).stdout
    return json.loads(output)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--impact", type=Path, default=Path("content/sverige-i-fokus/fact-density-impact-preview-v1.json"))
    parser.add_argument("--output", type=Path, default=Path("content/sverige-i-fokus/lesson-regeneration-v2-plan.json"))
    parser.add_argument("--persist", action="store_true")
    args = parser.parse_args()
    audit = build(snapshot(), json.loads(args.impact.read_text()))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(audit, ensure_ascii=False, indent=2) + "\n")
    if args.persist:
        subprocess.run(["docker", "compose", "exec", "-T", "ai-database", "psql", "-v", "ON_ERROR_STOP=1", "-U", "ai", "-d", "ai"], input=persistence_sql(audit), text=True, check=True)
    print(json.dumps({"auditId": audit["auditId"], "topics": len(audit["topics"]),
                      "facts": sum(len(value["assignedFactVersionIds"]) for value in audit["topics"]),
                      "oldPages": sum(value["currentPageCount"] for value in audit["topics"]),
                      "newPages": sum(value["plannedPageCount"] for value in audit["topics"]),
                      "belowFour": sum(value["plannedPageCount"] < 4 for value in audit["topics"])}, sort_keys=True))


if __name__ == "__main__":
    main()
