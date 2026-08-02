import json
import unittest

from scripts.run_fact_density_generation import instruction, next_attempt_key


class FactDensityGenerationTest(unittest.TestCase):
    def test_instruction_carries_bounded_audit_contract(self):
        audit = {"auditId": "audit", "definitionChecksum": "a" * 64}
        section = {
            "id": "section", "checksum": "b" * 64,
            "approvedFacts": [{"text": "En befintlig faktauppgift."}],
        }
        value = json.loads(instruction(audit, section, ["En saknad uppgift."]))
        self.assertEqual(value["mode"], "MISSING_FACTS_ONLY")
        self.assertEqual(value["maximumCandidateCount"], 1)
        self.assertEqual(value["existingApprovedFacts"], value["forbiddenDuplicatePropositions"])
        self.assertEqual(value["missingTeachingConceptTargets"], ["En saknad uppgift."])

    def test_failed_job_gets_new_recovery_key_without_rerunning_success(self):
        failed = [{"idempotencyKey": "batch", "status": "FAILED"}]
        self.assertEqual(next_attempt_key("batch", failed), "batch:recovery:1")
        completed = failed + [{"idempotencyKey": "batch:recovery:1", "status": "COMPLETED"}]
        self.assertIsNone(next_attempt_key("batch", completed))
        exhausted = failed + [{"idempotencyKey": "batch:recovery:1", "status": "FAILED"}]
        self.assertIsNone(next_attempt_key("batch", exhausted))


if __name__ == "__main__":
    unittest.main()
