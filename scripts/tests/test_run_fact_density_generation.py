import json
import unittest

from scripts.run_fact_density_generation import instruction


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


if __name__ == "__main__":
    unittest.main()
