import hashlib
import json
import os
import subprocess
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))

import corpusctl
import sverige_i_fokus_corpus as corpus


class SverigeIFokusCorpusTest(unittest.TestCase):
    def test_pdf_identity_and_deterministic_artifacts(self):
        pdf = ROOT / "docs/sverige-i-fokus.pdf"
        self.assertTrue(pdf.is_file())
        self.assertEqual(corpus.EXPECTED_SHA256, hashlib.sha256(pdf.read_bytes()).hexdigest())
        first = corpus.extract_sections(pdf)
        second = corpus.extract_sections(pdf)
        self.assertEqual(first, second)
        self.assertEqual(38, len(first))
        self.assertEqual(list(range(1, 39)), [item["order"] for item in first])
        self.assertTrue(all(item["startPage"] <= item["endPage"] <= 48 for item in first))
        self.assertTrue(all(item["sourceRevisionVersion"] == 2 for item in first))

    def test_all_sections_stop_before_a_later_chapter_heading(self):
        sections = corpus.extract_sections(ROOT / "docs/sverige-i-fokus.pdf")
        chapter_numbers = {chapter.title: index for index, chapter in enumerate(corpus.CHAPTERS, 1)}
        for section in sections:
            own = chapter_numbers[section["chapter"]]
            for later in range(own + 1, len(corpus.CHAPTERS) + 1):
                self.assertNotRegex(section["normalizedText"].casefold(), rf"kapitel\s+{later}\s*[–-]")

    def test_chapter_eight_section_ends_before_chapter_nine(self):
        sections = corpus.extract_sections(ROOT / "docs/sverige-i-fokus.pdf")
        section = next(item for item in sections if item["subsection"] == "Privatekonomi i Sverige")
        self.assertEqual(29, section["endPage"])
        self.assertEqual("NEXT_CHAPTER", section["boundaryReason"])
        self.assertEqual(1120, len(section["normalizedText"]))
        self.assertTrue(section["normalizedText"].endswith("deklarera sin inkomst till Skatteverket."))
        self.assertNotIn("Välfärdssamhället", section["normalizedText"])

    def test_exactly_twelve_v2_sections_change_content(self):
        old = json.loads(subprocess.run(
            ["git", "show", "HEAD:content/sverige-i-fokus/source-sections.json"],
            cwd=ROOT, check=True, capture_output=True, text=True,
        ).stdout)
        new = corpus.extract_sections(ROOT / "docs/sverige-i-fokus.pdf")
        old_by_logical = {item["id"]: item for item in old}
        changed = [item for item in new if item["exactText"] != old_by_logical[item["logicalSectionId"]]["exactText"]]
        self.assertEqual([5, 7, 9, 11, 13, 16, 21, 25, 27, 32, 35, 37], [item["order"] for item in changed])

    def test_manifest_maps_every_topic_and_objective_to_evidence(self):
        manifest = json.loads((ROOT / "content/sverige-i-fokus/curriculum-manifest.yaml").read_text())
        self.assertEqual("sverige-i-fokus-v1", manifest["corpusId"])
        self.assertEqual(13, len(manifest["subjects"]))
        topics = [topic for subject in manifest["subjects"] for topic in subject["topics"]]
        objectives = [objective for topic in topics for objective in topic["learningObjectives"]]
        self.assertEqual(38, len(topics))
        self.assertEqual(38, len(objectives))
        self.assertTrue(all(objective["sourceSectionIds"] for objective in objectives))

    def test_target_safety_rejects_identity_and_us_east(self):
        with patch.dict(os.environ, {"CORPUS_CONTENT_DATABASE_URL": "postgresql://x@us-east.example/content"}, clear=False):
            with self.assertRaises(SystemExit):
                corpusctl.target("content", "hosted")
        with patch.dict(os.environ, {"CORPUS_CONTENT_DATABASE_URL": "postgresql://x@localhost/identity"}, clear=False):
            with self.assertRaises(SystemExit):
                corpusctl.target("content", "local")

    def test_dry_run_flag_is_available_and_requires_backup_manifest(self):
        parser = corpusctl.parser()
        parsed = parser.parse_args(["reset", "--environment", "local", "--verified-backup", "backup.json", "--require-verified-backup", "--dry-run"])
        self.assertTrue(parsed.dry_run)
        self.assertEqual("reset", parsed.command)

    def chapter(self, *, topics_covered=2, objectives_covered=2, approval_rate=2 / 3,
                safety=None, sufficiency=None, attempted=2, systematic=None):
        topic_results = sufficiency or [
            {"topicCode": "T-1", "status": "SUFFICIENT"},
            {"topicCode": "T-2", "status": "LIMITED_BUT_USABLE"},
        ]
        return {
            "coverage": {"topicsCovered": topics_covered, "topicsTotal": 2,
                         "objectivesCovered": objectives_covered, "objectivesTotal": 2,
                         "sourceSectionsAttempted": attempted, "sourceSectionsTotal": 2},
            "safety": {"unsupportedApprovedFacts": 0, "approvedGroundingFailures": 0,
                       "approvedEvidenceFailures": 0, "sourceSectionMismatches": 0,
                       "checksumMismatches": 0, "topicMappingFailures": 0,
                       "objectiveMappingFailures": 0, "missingProvenance": 0,
                       "lineageOrAuditFailures": 0, "freeOnlyConfirmed": True, **(safety or {})},
            "efficiency": {"novelContentApprovalRate": approval_rate,
                           "rejectedCandidates": 2 if approval_rate < 1 else 0},
            "systematicFailures": systematic or {"detected": False, "reasons": []},
            "topicSufficiency": topic_results,
        }

    def test_coverage_gate_passes_with_warning_at_sixty_six_percent(self):
        result = corpusctl.evaluate_fact_gate(self.chapter())
        self.assertEqual("PASS_WITH_WARNING", result["status"])
        self.assertTrue(result["progressionAllowed"])
        self.assertEqual("REVIEW_RECOMMENDED", result["warningLevel"])
        self.assertIn("REVIEW_RECOMMENDED", result["warnings"])
        self.assertEqual("CHAPTER_LESSON_PLANNING", result["resumePoint"])

    def test_low_approval_rate_is_reporting_signal_when_content_is_sufficient(self):
        result = corpusctl.evaluate_fact_gate(self.chapter(approval_rate=.5))
        self.assertEqual("PASS_WITH_WARNING", result["status"])
        self.assertNotIn("LOW_GENERATION_EFFICIENCY", result["reasons"])

    def test_duplicate_candidates_are_excluded_only_from_novel_denominator(self):
        metrics = corpusctl.fact_efficiency(6, 4, 2, 0, {"GOOD": 4, "DUPLICATE": 1,
                                                          "NEEDS_SPLIT": 1}, 0, 2, 0)
        self.assertAlmostEqual(4 / 6, metrics["rawApprovalRate"])
        self.assertAlmostEqual(4 / 5, metrics["novelContentApprovalRate"])
        self.assertAlmostEqual(1 / 6, metrics["needsSplitRate"])

    def test_high_approval_rate_cannot_hide_coverage_gaps(self):
        self.assertEqual("PARTIAL", corpusctl.evaluate_fact_gate(
            self.chapter(topics_covered=1, approval_rate=.95))["status"])
        self.assertEqual("PARTIAL", corpusctl.evaluate_fact_gate(
            self.chapter(objectives_covered=1, approval_rate=.95))["status"])

    def test_high_approval_rate_cannot_hide_safety_failures(self):
        cases = [
            {"unsupportedApprovedFacts": 1}, {"approvedGroundingFailures": 1},
            {"approvedEvidenceFailures": 1}, {"sourceSectionMismatches": 1},
            {"checksumMismatches": 1}, {"objectiveMappingFailures": 1},
        ]
        for safety in cases:
            with self.subTest(safety=safety):
                self.assertEqual("BLOCKED", corpusctl.evaluate_fact_gate(
                    self.chapter(approval_rate=.95, safety=safety))["status"])

    def test_insufficient_topic_is_partial_even_with_approved_facts(self):
        sufficiency = [{"topicCode": "T-1", "status": "SUFFICIENT"},
                       {"topicCode": "T-2", "status": "INSUFFICIENT"}]
        result = corpusctl.evaluate_fact_gate(self.chapter(approval_rate=.95, sufficiency=sufficiency))
        self.assertEqual("PARTIAL", result["status"])
        self.assertIn("INSUFFICIENT_LESSON_MATERIAL:T-2", result["reasons"])

    def test_systematic_pipeline_failure_blocks(self):
        systematic = {"detected": True, "reasons": ["SYSTEMATIC_PROVIDER_FAILURE"]}
        result = corpusctl.evaluate_fact_gate(self.chapter(approval_rate=.95, systematic=systematic))
        self.assertEqual("BLOCKED", result["status"])
        self.assertEqual("PIPELINE_DEFECT_SUSPECTED", result["warningLevel"])

    def test_topic_sufficiency_uses_distinct_facts_and_grounded_source_depth(self):
        strong = {"approvedFacts": 2, "distinctApprovedFacts": 2, "reasonablyTestableFacts": 2,
                  "sourceSections": 1, "sourceSectionsAttempted": 1, "sourceCharacters": 200}
        limited = {"approvedFacts": 1, "distinctApprovedFacts": 1, "reasonablyTestableFacts": 1,
                   "sourceSections": 1, "sourceSectionsAttempted": 1, "sourceCharacters": 800}
        shallow = {**limited, "sourceCharacters": 200}
        duplicate = {**strong, "distinctApprovedFacts": 1}
        self.assertEqual("SUFFICIENT", corpusctl.topic_lesson_sufficiency(strong)["status"])
        self.assertEqual("LIMITED_BUT_USABLE", corpusctl.topic_lesson_sufficiency(limited)["status"])
        self.assertEqual("INSUFFICIENT", corpusctl.topic_lesson_sufficiency(shallow)["status"])
        self.assertEqual("INSUFFICIENT", corpusctl.topic_lesson_sufficiency(duplicate)["status"])


if __name__ == "__main__":
    unittest.main()
