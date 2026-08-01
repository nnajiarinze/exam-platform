import importlib.util
import sys
import unittest
from pathlib import Path

SPEC = importlib.util.spec_from_file_location("question_bank_expansion", Path(__file__).parents[1] / "question_bank_expansion.py")
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


def row(text, questions=None):
    return {"fact_id":"00000000-0000-0000-0000-000000000001","fact_version_id":"00000000-0000-0000-0000-000000000002",
            "fact_version":1,"fact_text":text,"chapter":"Kapitel","topic_id":"00000000-0000-0000-0000-000000000003",
            "topic_name":"Ämne","objective_id":"00000000-0000-0000-0000-000000000004","objective":"Mål",
            "source_section_id":"00000000-0000-0000-0000-000000000005","source_section":"Avsnitt",
            "section_checksum":"a"*64,"exact_evidence":text,"questions":questions or []}


class QuestionBankExpansionTest(unittest.TestCase):
    def test_narrow_fact_is_not_mechanically_expanded(self):
        audit = MODULE.audit_fact(row("Kebnekaise är Sveriges högsta berg.", [{"id":"q1","text":"Vilket berg?","type":"SINGLE_CHOICE"}]))
        self.assertEqual("ONE_QUESTION_ONLY", audit["density_classification"])
        self.assertEqual([], audit["targets"])

    def test_pdf_line_break_hyphen_is_normalized_before_grounding_snapshot(self):
        source = row("År 2024 hade Sverige 290 kommuner.", [{"id":"q1","text":"Hur många?","type":"SINGLE_CHOICE"}])
        source["exact_evidence"] = "År 2024 hade Sverige 290 kom-\nmuner."
        audit = MODULE.audit_fact(source)
        self.assertEqual("År 2024 hade Sverige 290 kom- muner.", audit["targets"][0]["exact_evidence"])

    def test_rich_fact_gets_distinct_targets_but_never_more_than_four_total(self):
        audit = MODULE.audit_fact(row("År 1938 slöts ett avtal mellan arbetsgivare och fackförbund i Saltsjöbaden.", [{"id":"q1","text":"Vad hände?","type":"SINGLE_CHOICE"}]))
        kinds = [target["target_type"] for target in audit["targets"]]
        self.assertEqual(kinds, list(dict.fromkeys(kinds)))
        self.assertLessEqual(audit["safe_total_question_count"], 4)
        self.assertIn("CHRONOLOGY", kinds)
        self.assertIn("LOCATION", kinds)

    def test_plan_is_deterministic_and_preserves_existing_question_snapshots(self):
        source = row("Demokrati betyder folkstyre.", [{"id":"q1","text":"Vad betyder demokrati?","type":"SINGLE_CHOICE"}])
        first = MODULE.build_plan([source])
        second = MODULE.build_plan([source])
        self.assertEqual(first, second)
        self.assertEqual(source["questions"], first["facts"][0]["existing_questions"])
        self.assertTrue(all(len(fact["targets"]) == len({target["target_checksum"] for target in fact["targets"]}) for fact in first["facts"]))
        sql = MODULE.persistence_sql(first)
        self.assertIn("BEGIN;", sql)
        self.assertIn("COMMIT;", sql)
        self.assertIn(source["fact_text"], sql)


if __name__ == "__main__":
    unittest.main()
