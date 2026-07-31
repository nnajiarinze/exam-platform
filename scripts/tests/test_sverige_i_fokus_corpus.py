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


if __name__ == "__main__":
    unittest.main()
