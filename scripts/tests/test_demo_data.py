import argparse
import importlib.util
import pathlib
import sys
import unittest

MODULE_PATH = pathlib.Path(__file__).parents[1] / "demo_data.py"
SPEC = importlib.util.spec_from_file_location("demo_data", MODULE_PATH)
demo_data = importlib.util.module_from_spec(SPEC)
assert SPEC.loader
sys.modules[SPEC.name] = demo_data
SPEC.loader.exec_module(demo_data)


class DemoDataTest(unittest.TestCase):
    def args(self, confirmed=True, content="content-database", learning="learning-database"):
        return argparse.Namespace(confirm_reset=confirmed, content_db_host=content, learning_db_host=learning)

    def test_reset_is_rejected_in_production(self):
        with self.assertRaisesRegex(RuntimeError, "non-production"):
            demo_data.assert_safe(self.args(), {"ALLOW_DESTRUCTIVE_DEV_RESET": "true", "DEMO_DATA_ENVIRONMENT": "production"})

    def test_reset_requires_explicit_confirmation_and_guard(self):
        with self.assertRaisesRegex(RuntimeError, "ALLOW_DESTRUCTIVE"):
            demo_data.assert_safe(self.args(), {"DEMO_DATA_ENVIRONMENT": "local"})
        with self.assertRaisesRegex(RuntimeError, "confirm-reset"):
            demo_data.assert_safe(self.args(False), {"ALLOW_DESTRUCTIVE_DEV_RESET": "true", "DEMO_DATA_ENVIRONMENT": "local"})

    def test_reset_rejects_protected_or_unknown_hosts(self):
        env = {"ALLOW_DESTRUCTIVE_DEV_RESET": "true", "DEMO_DATA_ENVIRONMENT": "development"}
        with self.assertRaisesRegex(RuntimeError, "protected"):
            demo_data.assert_safe(self.args(content="content.production.amazonaws.com"), env)
        with self.assertRaisesRegex(RuntimeError, "not an approved"):
            demo_data.assert_safe(self.args(content="database.internal"), env)

    def test_local_compose_reset_is_accepted(self):
        demo_data.assert_safe(self.args(), {"ALLOW_DESTRUCTIVE_DEV_RESET": "true", "DEMO_DATA_ENVIRONMENT": "local"})

    def test_dataset_hierarchy_and_question_rules(self):
        dataset = demo_data.build_dataset()
        self.assertEqual(5, len(dataset["subjects"]))
        self.assertEqual(20, sum(len(subject["topics"]) for subject in dataset["subjects"]))
        self.assertEqual(40, len(dataset["objectives"]))
        self.assertEqual(100, len(dataset["facts"]))
        self.assertEqual(100, len(dataset["questions"]))
        objective_ids = {item["id"] for item in dataset["objectives"]}
        fact_versions = {item["versionId"] for item in dataset["facts"]}
        for fact in dataset["facts"]:
            self.assertIn(fact["objectiveId"], objective_ids)
            self.assertTrue(fact["sourceId"])
        for question in dataset["questions"]:
            self.assertIn(question["objectiveId"], objective_ids)
            self.assertIn(question["factVersionId"], fact_versions)
            self.assertTrue(question["explanation"])
            correct = [option for option in question["options"] if option["correct"]]
            if question["type"] in ("SINGLE_CHOICE", "TRUE_FALSE"):
                self.assertEqual(1, len(correct))
            else:
                self.assertGreaterEqual(len(correct), 1)
            self.assertEqual(list(range(len(question["options"]))), [option["sortOrder"] for option in question["options"]])

    def test_snapshot_is_deterministic_and_contains_all_question_types(self):
        first, first_checksum = demo_data.snapshot(demo_data.build_dataset())
        second, second_checksum = demo_data.snapshot(demo_data.build_dataset())
        self.assertEqual(first, second)
        self.assertEqual(first_checksum, second_checksum)
        questions = [question for subject in first["subjects"] for topic in subject["topics"] for question in topic["questions"]]
        self.assertEqual(96, len(questions))
        self.assertEqual({"SINGLE_CHOICE", "TRUE_FALSE", "MULTIPLE_CHOICE"}, {question["questionType"] for question in questions})


if __name__ == "__main__":
    unittest.main()
