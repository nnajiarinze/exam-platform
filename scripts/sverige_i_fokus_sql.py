#!/usr/bin/env python3
"""Emit idempotent structural-import SQL for the Sverige i fokus corpus."""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import subprocess
from pathlib import Path


def sql(value: object) -> str:
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "TRUE" if value else "FALSE"
    if isinstance(value, int):
        return str(value)
    return "'" + str(value).replace("'", "''") + "'"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, default=Path("content/sverige-i-fokus/curriculum-manifest.yaml"))
    parser.add_argument("--sections", type=Path, default=Path("content/sverige-i-fokus/source-sections.json"))
    parser.add_argument("--pdf", type=Path, default=Path("docs/sverige-i-fokus.pdf"))
    args = parser.parse_args()
    manifest = json.loads(args.manifest.read_text())
    sections = json.loads(args.sections.read_text())
    source = manifest["source"]
    revision = manifest["sourceRevision"]
    pdf_checksum = hashlib.sha256(args.pdf.read_bytes()).hexdigest()
    if pdf_checksum != source["sha256"]:
        raise SystemExit("PDF and manifest checksums differ")
    extracted = (
        subprocess.run(["pdftotext", "-layout", str(args.pdf), "-"], check=True, capture_output=True, text=True).stdout
        if shutil.which("pdftotext")
        else "\n\n".join(section["exactText"] for section in sections)
    )
    content_checksum = hashlib.sha256(extracted.encode()).hexdigest()
    lines = [
        "BEGIN;",
        "SELECT set_config('app.actor_id','sverige-i-fokus-import',true);",
        "SELECT set_config('app.actor_name','Sverige i fokus deterministic importer',true);",
        "SELECT set_config('app.actor_roles','SYSTEM',true);",
        (
            "INSERT INTO source_reference(id,publisher,title,source_type,document_version,publication_date,accessed_at,"
            "copyright_notes,internal_notes,review_status,status,created_at,updated_at,content_text,content_checksum,"
            "original_filename,file_checksum,language_code,official_study_material,attribution,licensing_review_status,imported_at)"
            f" VALUES({sql(source['id'])},{sql(source['publisher'])},{sql(source['title'])},'GOVERNMENT_DOCUMENT',"
            f"{sql(source['edition'] + ', 2026')},NULL,CURRENT_DATE,"
            "'Rights and public-reuse scope require editorial/legal review before public reproduction.',"
            "'Authoritative internal corpus source. Independent practice material; no endorsement.',"
            f"'UNREVIEWED','ACTIVE',now(),now(),{sql(extracted)},{sql(content_checksum)},{sql(source['originalFilename'])},"
            f"{sql(source['sha256'])},{sql(source['language'])},TRUE,{sql(source['attribution'])},'PENDING',now())"
            " ON CONFLICT DO NOTHING;"
        ),
        (
            "INSERT INTO source_revision(id,source_reference_id,revision_number,parent_revision_id,pdf_checksum,"
            "parser_version,correction_reason,review_status,reviewer_actor,created_at,status)"
            f" VALUES({sql(revision['id'])},{sql(source['id'])},{revision['version']},{sql(revision['parentRevisionId'])},"
            f"{sql(source['sha256'])},{sql(revision['parserVersion'])},{sql(revision['correctionReason'])},"
            f"{sql(revision['reviewStatus'])},{sql(revision['reviewerActor'])},{sql(revision['createdAt'])},'ACTIVE')"
            " ON CONFLICT(id) DO NOTHING;"
        ),
        "UPDATE source_revision SET status='SUPERSEDED' WHERE source_reference_id=" + sql(source["id"])
        + " AND id<>" + sql(revision["id"]) + " AND status='ACTIVE';",
    ]
    for section in sections:
        section_code = f"SEC-{section['order']:03d}-R{revision['version']}"
        lines.append(
            "INSERT INTO source_section(id,source_reference_id,logical_section_id,source_revision_id,code,chapter_title,subsection_title,structural_path,"
            "page_start,page_end,display_order,exact_text,normalized_text,section_checksum,extraction_start,extraction_end,boundary_reason,created_at,updated_at)"
            f" VALUES({sql(section['id'])},{sql(source['id'])},{sql(section['logicalSectionId'])},{sql(revision['id'])},{sql(section_code)},"
            f"{sql(section['chapter'])},{sql(section['subsection'])},{sql(section['structuralPath'])},"
            f"{section['startPage']},{section['endPage']},{section['order']},{sql(section['exactText'])},"
            f"{sql(section['normalizedText'])},{sql(section['checksum'])},CAST({sql(json.dumps(section['extractionStart']))} AS jsonb),"
            f"CAST({sql(json.dumps(section['extractionEnd']))} AS jsonb),{sql(section['boundaryReason'])},now(),now())"
            " ON CONFLICT(source_revision_id,logical_section_id) DO NOTHING;"
        )
    lines.extend([
        "INSERT INTO source_revision_revalidation(id,entity_type,entity_id,old_source_section_id,new_source_section_id,classification,old_checksum,new_checksum,validator_version,details,reviewed_by,reviewed_at) "
        "SELECT gen_random_uuid(),'KNOWLEDGE_FACT',k.id,old.id,new.id,CASE WHEN NOT EXISTS (SELECT 1 FROM jsonb_array_elements(p.source_evidence) evidence WHERE position(regexp_replace(replace(evidence->>'quote',chr(173),''),'\\s+',' ','g') in new.normalized_text)=0) THEN 'SOURCE_REVISION_UPDATED' ELSE 'AFFECTED_REQUIRES_REPAIR' END,old.section_checksum,new.section_checksum,'source-boundary-impact-v1',jsonb_build_object('originalProvenancePreserved',true),'sverige-i-fokus-boundary-correction',now() FROM knowledge_fact k JOIN knowledge_fact_ai_provenance p ON p.knowledge_fact_version_id=k.current_version_id JOIN source_section old ON old.id=p.source_section_id JOIN source_section new ON new.logical_section_id=old.logical_section_id AND new.source_revision_id=" + sql(revision["id"]) + " WHERE old.source_revision_id='sverige-i-fokus-source-v1' AND old.section_checksum<>new.section_checksum ON CONFLICT(entity_type,entity_id,new_source_section_id) DO NOTHING;",
        "INSERT INTO source_revision_revalidation(id,entity_type,entity_id,old_source_section_id,new_source_section_id,classification,old_checksum,new_checksum,validator_version,details,reviewed_by,reviewed_at) "
        "SELECT gen_random_uuid(),'LESSON',l.id,old.id,new.id,CASE WHEN EXISTS (SELECT 1 FROM lesson_draft_section_fact lsf JOIN knowledge_fact_version kv ON kv.id=lsf.knowledge_fact_version_id JOIN source_revision_revalidation r ON r.entity_type='KNOWLEDGE_FACT' AND r.entity_id=kv.knowledge_fact_id AND r.new_source_section_id=new.id WHERE lsf.lesson_draft_section_id=lds.id AND r.classification IN ('AFFECTED_REQUIRES_REPAIR','INVALID_AFTER_BOUNDARY_CORRECTION')) THEN 'AFFECTED_REQUIRES_REPAIR' ELSE 'SOURCE_REVISION_UPDATED' END,old.section_checksum,new.section_checksum,'source-boundary-impact-v1',jsonb_build_object('semanticContentChanged',false),'sverige-i-fokus-boundary-correction',now() FROM lesson_draft l JOIN lesson_draft_section lds ON lds.lesson_draft_id=l.id JOIN source_section old ON old.id=lds.source_section_id JOIN source_section new ON new.logical_section_id=old.logical_section_id AND new.source_revision_id=" + sql(revision["id"]) + " WHERE old.source_revision_id='sverige-i-fokus-source-v1' AND old.section_checksum<>new.section_checksum ON CONFLICT(entity_type,entity_id,new_source_section_id) DO NOTHING;",
        "INSERT INTO source_revision_revalidation(id,entity_type,entity_id,old_source_section_id,new_source_section_id,classification,old_checksum,new_checksum,validator_version,details,reviewed_by,reviewed_at) "
        "SELECT gen_random_uuid(),'QUESTION',q.id,old.id,new.id,CASE WHEN fr.classification IN ('AFFECTED_REQUIRES_REPAIR','INVALID_AFTER_BOUNDARY_CORRECTION') THEN 'AFFECTED_REQUIRES_REPAIR' ELSE 'SOURCE_REVISION_UPDATED' END,old.section_checksum,new.section_checksum,'source-boundary-impact-v1',jsonb_build_object('stableOptionIdsPreserved',true),'sverige-i-fokus-boundary-correction',now() FROM question q JOIN question_ai_provenance qp ON qp.question_id=q.id JOIN knowledge_fact_ai_provenance fp ON fp.knowledge_fact_version_id=qp.knowledge_fact_version_id JOIN source_section old ON old.id=fp.source_section_id JOIN source_section new ON new.logical_section_id=old.logical_section_id AND new.source_revision_id=" + sql(revision["id"]) + " JOIN source_revision_revalidation fr ON fr.entity_type='KNOWLEDGE_FACT' AND fr.entity_id=qp.knowledge_fact_id AND fr.new_source_section_id=new.id WHERE old.source_revision_id='sverige-i-fokus-source-v1' AND old.section_checksum<>new.section_checksum ON CONFLICT(entity_type,entity_id,new_source_section_id) DO NOTHING;",
    ])
    exam = manifest["exam"]
    lines.extend(
        [
            (
                "INSERT INTO exam(id,code,name,country_code,status,created_at,updated_at)"
                f" VALUES({sql(exam['id'])},{sql(exam['code'])},{sql(exam['name'])},'SE','DRAFT',now(),now())"
                " ON CONFLICT(code) DO NOTHING;"
            ),
            (
                "INSERT INTO exam_version(id,exam_id,version_code,display_name,status,created_at,updated_at)"
                f" VALUES({sql(exam['versionId'])},{sql(exam['id'])},{sql(exam['versionCode'])},"
                "'Sverige i fokus v1 – intern granskningsversion','DRAFT',now(),now())"
                " ON CONFLICT(exam_id,version_code) DO NOTHING;"
            ),
        ]
    )
    for subject in manifest["subjects"]:
        lines.append(
            "INSERT INTO subject(id,exam_version_id,code,name,description,sort_order,status,created_at,updated_at)"
            f" VALUES({sql(subject['id'])},{sql(exam['versionId'])},{sql(subject['code'])},{sql(subject['name'])},"
            "'Chapter-derived subject from Sverige i fokus.',"
            f"{subject['displayOrder']},'DRAFT',now(),now()) ON CONFLICT(exam_version_id,code) DO NOTHING;"
        )
        for topic in subject["topics"]:
            lines.append(
                "INSERT INTO topic(id,subject_id,code,name,description,sort_order,status,created_at,updated_at)"
                f" VALUES({sql(topic['id'])},{sql(subject['id'])},{sql(topic['code'])},{sql(topic['name'])},"
                "'Subsection-derived topic from Sverige i fokus.',"
                f"{topic['displayOrder']},'DRAFT',now(),now()) ON CONFLICT(subject_id,code) DO NOTHING;"
            )
            for objective in topic["learningObjectives"]:
                lines.append(
                    "INSERT INTO learning_objective(id,topic_id,code,title,description,status,created_at,updated_at)"
                    f" VALUES({sql(objective['id'])},{sql(topic['id'])},{sql(objective['code'])},{sql(objective['title'])},"
                    "'Grounded exclusively in the mapped source section.','DRAFT',now(),now())"
                    " ON CONFLICT(topic_id,code) DO NOTHING;"
                )
                for section_id in objective["sourceSectionIds"]:
                    lines.append(
                        "DELETE FROM learning_objective_source_section WHERE learning_objective_id=" + sql(objective["id"]) + ";"
                    )
                    lines.append(
                        "INSERT INTO learning_objective_source_section(learning_objective_id,source_section_id)"
                        f" VALUES({sql(objective['id'])},{sql(section_id)}) ON CONFLICT DO NOTHING;"
                    )
    lines.extend(
        [
            "DO $$ BEGIN IF (SELECT count(*) FROM source_section WHERE source_revision_id="
            + sql(revision["id"])
            + ") <> 38 THEN RAISE EXCEPTION 'Expected 38 imported source sections'; END IF; END $$;",
            "COMMIT;",
        ]
    )
    print("\n".join(lines))


if __name__ == "__main__":
    main()
