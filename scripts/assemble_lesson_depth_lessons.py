#!/usr/bin/env python3
"""Assemble reviewed versioned lessons from retained and validated depth pages."""

from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
import uuid
import re
from pathlib import Path


ACTOR = "codex-lesson-depth-expansion"


def sql_literal(value):
    if value is None:
        return "NULL"
    if isinstance(value, (dict, list)):
        value = json.dumps(value, ensure_ascii=False, sort_keys=True)
    return "'" + str(value).replace("'", "''") + "'"


def query_json(container: str, database: str, user: str, sql: str):
    output = subprocess.run(["docker", "exec", container, "psql", "-U", user, "-d", database, "-Atc", sql],
                            check=True, capture_output=True, text=True).stdout.strip()
    return json.loads(output)


def execute(container: str, database: str, user: str, sql: str):
    subprocess.run(["docker", "exec", "-i", container, "psql", "-v", "ON_ERROR_STOP=1",
                    "-U", user, "-d", database], input=sql, text=True, check=True)


def latest_validated(group: dict) -> dict[int, dict]:
    latest = {}
    for revision in group["inspection"]["revisions"]:
        index = revision["pageIndex"]
        if revision["status"] == "VALIDATED" and (index not in latest or revision["revisionNumber"] > latest[index]["revisionNumber"]):
            latest[index] = revision
    return latest


def current_lessons() -> dict[str, dict]:
    sql = """
    WITH latest AS (SELECT DISTINCT ON(topic_id) * FROM lesson_draft WHERE review_status='REVIEWED' ORDER BY topic_id,version_number DESC)
    SELECT jsonb_object_agg(l.topic_id::text,jsonb_build_object('id',l.id,'version',l.version_number,'title',l.title,
      'introduction',l.introduction,'summary',l.summary,'importantPoints',l.important_points,'sourceChecksum',l.source_checksum,
      'pages',(SELECT jsonb_agg(jsonb_build_object('id',s.id,'logicalSectionId',s.logical_section_id,'sourceSectionId',s.source_section_id,
        'title',s.title,'explanation',s.explanation,'keyTerms',s.key_terms,'supportedExamples',s.supported_examples,
        'displayOrder',s.display_order,'factVersionIds',coalesce((SELECT jsonb_agg(f.knowledge_fact_version_id ORDER BY f.knowledge_fact_version_id)
          FROM lesson_draft_section_fact f WHERE f.lesson_draft_section_id=s.id),'[]'::jsonb)) ORDER BY s.display_order)
        FROM lesson_draft_section s WHERE s.lesson_draft_id=l.id)))::text FROM latest l;
    """
    return query_json("exam-platform-content-database-1", "content", "content", sql)


def generated_pages(topic: dict, topic_state: dict) -> list[dict]:
    by_checksum = {plan["planChecksum"]: plan for plan in topic["candidatePagePlans"]}
    result = []
    for group in topic_state.get("groups", []):
        if group["status"] != "VALIDATED":
            continue
        revisions = latest_validated(group)
        for index, plan_checksum in enumerate(group["planChecksums"]):
            plan = by_checksum[plan_checksum]
            revision = revisions[index]
            result.append({"plan": plan, "revision": revision, "page": revision["page"]})
    return sorted(result, key=lambda item: item["plan"]["pageOrder"])


def material_overlap(body: str, accumulated: str) -> float:
    tokens = {token for token in re.findall(r"[0-9A-Za-zÅÄÖåäö]+", body.casefold()) if len(token) > 3}
    prior = {token for token in re.findall(r"[0-9A-Za-zÅÄÖåäö]+", accumulated.casefold()) if len(token) > 3}
    return 0.0 if not tokens else len(tokens & prior) / len(tokens)


def assemble(audit: dict, state: dict, existing: dict[str, dict]) -> tuple[str, dict]:
    lines = ["BEGIN;", "SELECT pg_advisory_xact_lock(hashtext('sverige-i-fokus-lesson-depth-v1'));" ]
    report = {"auditId": audit["auditId"], "lessonsCreated": [], "unchangedLessons": [], "pages": 0,
              "retainedPages": 0, "generatedPages": 0, "topics": []}
    for topic in audit["topics"]:
        old = existing[topic["topicId"]]
        action_by_id = {item["pageId"]: item["action"] for item in topic["pageActions"]}
        retained = [page for page in old["pages"] if action_by_id[page["id"]] == "REUSE_UNCHANGED"]
        generated_candidates = generated_pages(topic, state["topics"].get(topic["topicId"], {}))
        generated=[];coherence_rejected=[];accumulated=" ".join(page["explanation"] for page in retained)
        for item in generated_candidates:
            score=material_overlap(item["page"]["body"],accumulated)
            if score>=0.60:
                coherence_rejected.append({"planChecksum":item["plan"]["planChecksum"],"reason":"DUPLICATES_SIBLING","overlap":round(score,3)})
            else:
                generated.append(item);accumulated += " " + item["page"]["body"]
        replaced_all = [item["pageId"] for item in topic["pageActions"] if item["action"] != "REUSE_UNCHANGED"]
        accepted_replacements=min(len(replaced_all),len(generated))
        fallback_ids=set(replaced_all[accepted_replacements:])
        retained.extend(page for page in old["pages"] if page["id"] in fallback_ids)
        retained.sort(key=lambda page: page["displayOrder"])
        if len(retained) + len(generated) > topic["maximumSafelySupportablePageCount"]:
            raise RuntimeError(f"{topic['topicTitle']}: coherence fallback exceeds immutable page ceiling")
        if len(retained) == len(old["pages"]) and not generated:
            report["unchangedLessons"].append(old["id"])
            report["pages"] += len(retained)
            report["retainedPages"] += len(retained)
            report["topics"].append({"topicId": topic["topicId"], "status": "UNCHANGED_SUFFICIENT",
                                     "oldPages": len(old["pages"]), "newPages": len(old["pages"])})
            continue
        lesson_id = str(uuid.uuid5(uuid.UUID(audit["auditId"]), "lesson:" + topic["topicId"]))
        topic_plan_id = str(uuid.uuid5(uuid.UUID(audit["auditId"]), "topic:" + topic["topicId"]))
        next_version = old["version"] + 1
        lines.append("INSERT INTO lesson_draft(id,topic_id,version_number,title,introduction,summary,important_points,review_status,source_checksum,created_by,reviewed_by,review_note,created_at,updated_at,reviewed_at,version,supersedes_lesson_draft_id,generation_plan_id,revision_reason,human_verified) VALUES(" + ",".join([
            sql_literal(lesson_id),sql_literal(topic["topicId"]),str(next_version),sql_literal(old["title"]),
            sql_literal(old["introduction"]),sql_literal(old["summary"]),sql_literal(old["importantPoints"]),
            sql_literal("REVIEWED"),sql_literal(old["sourceChecksum"]),sql_literal(ACTOR),sql_literal(ACTOR),
            sql_literal("Automatic strict lesson-depth gate passed; human_verified=false."),"now()","now()","now()","0",
            sql_literal(old["id"]),sql_literal(topic_plan_id),sql_literal("LESSON_DEPTH_EXPANSION"),"false",
        ]) + ") ON CONFLICT(id) DO NOTHING;")
        final_pages = []
        replaced = replaced_all[:accepted_replacements]
        for page in retained:
            final_pages.append({"title": page["title"], "body": page["explanation"], "sourceSectionId": page["sourceSectionId"],
                                "facts": page["factVersionIds"], "keyTerms": page["keyTerms"], "examples": page["supportedExamples"],
                                "predecessor": page["id"], "logical": page["logicalSectionId"], "mapping": "RETAINED"})
        for index,item in enumerate(generated):
            page=item["page"];plan=item["plan"];predecessor=replaced[index] if index<len(replaced) else None
            final_pages.append({"title": page["title"], "body": page["body"],
                                "sourceSectionId": plan["sourceSections"][0]["id"],
                                "facts": page["knowledgeFactVersionIds"], "keyTerms": page.get("keyTerms", []), "examples": [],
                                "predecessor": predecessor, "logical": None,
                                "mapping": "SUPERSEDED" if predecessor else "ADDED", "revision": item["revision"]})
        covered = {fact for page in final_pages for fact in page["facts"]}
        expected = set(topic["assignedFactVersionIds"])
        if covered != expected:
            raise RuntimeError(f"{topic['topicTitle']}: Fact coverage changed ({covered ^ expected})")
        normalized_bodies = [" ".join(page["body"].casefold().split()) for page in final_pages]
        if len(normalized_bodies) != len(set(normalized_bodies)):
            raise RuntimeError(f"{topic['topicTitle']}: duplicate page entered coherence candidate")
        for order,page in enumerate(final_pages):
            section_id = str(uuid.uuid5(uuid.UUID(lesson_id), f"section:{order}:{page['title']}"))
            logical = page["logical"] or (query_json("exam-platform-content-database-1", "content", "content",
                "SELECT to_jsonb(logical_section_id)::text FROM lesson_draft_section WHERE id="+sql_literal(page["predecessor"]) ) if page["predecessor"] else section_id)
            section_checksum = hashlib.sha256((page["title"].strip()+"\n"+page["body"].strip()).encode()).hexdigest()
            lines.append("INSERT INTO lesson_draft_section(id,lesson_draft_id,source_section_id,title,explanation,key_terms,supported_examples,display_order,section_checksum,logical_section_id) VALUES(" + ",".join([
                sql_literal(section_id),sql_literal(lesson_id),sql_literal(page["sourceSectionId"]),sql_literal(page["title"]),
                sql_literal(page["body"]),sql_literal(page["keyTerms"]),sql_literal(page["examples"]),str(order),
                sql_literal(section_checksum),sql_literal(logical)]) + ") ON CONFLICT(id) DO NOTHING;")
            for fact in page["facts"]:
                lines.append("INSERT INTO lesson_draft_section_fact(lesson_draft_section_id,knowledge_fact_version_id) VALUES("+sql_literal(section_id)+","+sql_literal(fact)+") ON CONFLICT DO NOTHING;")
            mapping_id = str(uuid.uuid5(uuid.UUID(lesson_id), f"mapping:{order}:{page['predecessor']}"))
            lines.append("INSERT INTO lesson_draft_supersession_mapping(id,predecessor_lesson_id,successor_lesson_id,predecessor_section_id,successor_section_id,mapping_type,audit_snapshot,created_by,created_at) VALUES("+",".join([
                sql_literal(mapping_id),sql_literal(old["id"]),sql_literal(lesson_id),sql_literal(page["predecessor"]),
                sql_literal(section_id),sql_literal(page["mapping"]),sql_literal({"auditId":audit["auditId"],"topicPlanId":topic_plan_id}),sql_literal(ACTOR),"now()"])+") ON CONFLICT DO NOTHING;")
        report["lessonsCreated"].append(lesson_id);report["pages"] += len(final_pages)
        report["retainedPages"] += len(retained);report["generatedPages"] += len(generated)
        report["topics"].append({"topicId": topic["topicId"], "status": "PASS" if len(final_pages)>=4 and not coherence_rejected else "PASS_WITH_WARNING",
                                 "oldPages": len(old["pages"]), "newPages": len(final_pages), "lessonId":lesson_id,
                                 "coherenceRejected":coherence_rejected})
    lines.extend(["COMMIT;", ""])
    return "\n".join(lines),report


def main():
    parser=argparse.ArgumentParser();parser.add_argument("--audit",type=Path,required=True);parser.add_argument("--state",type=Path,required=True);parser.add_argument("--report",type=Path,required=True);parser.add_argument("--apply",action="store_true");args=parser.parse_args()
    audit=json.loads(args.audit.read_text());state=json.loads(args.state.read_text());sql,report=assemble(audit,state,current_lessons())
    if args.apply: execute("exam-platform-content-database-1","content","content",sql)
    args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
    print(json.dumps({k:report[k] for k in ("pages","retainedPages","generatedPages")}|{"lessonsCreated":len(report["lessonsCreated"]),"unchangedLessons":len(report["unchangedLessons"])},sort_keys=True))


if __name__=="__main__":main()
