import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

MODULE=Path(__file__).parents[1]/"authoring_snapshot.py"
spec=importlib.util.spec_from_file_location("authoring_snapshot",MODULE)
snapshot=importlib.util.module_from_spec(spec); spec.loader.exec_module(snapshot)

def minimal_snapshot(root:Path, value:str="safe"):
    for role in ("content","ai"):
        (root/role/"tables").mkdir(parents=True)
        (root/role/"tables"/"entity.ndjson").write_text(json.dumps({"id":"1","value":value})+"\n")
        (root/role/"schema.json").write_text(json.dumps({"tables":[{"table":"entity","columns":[{"name":"id"},{"name":"value"}],"primaryKey":["id"],"uniqueKeys":[]}],"foreignKeys":[]}))
    files={}
    for path in root.rglob("*"):
        if path.is_file(): files[str(path.relative_to(root))]={"sha256":snapshot.file_sha(path),"bytes":path.stat().st_size}
    manifest={"format":snapshot.FORMAT,"semanticChecksum":"a"*64,"files":files}
    manifest["completeSnapshotChecksum"]=snapshot.hashlib.sha256(snapshot.canonical_json(manifest).encode()).hexdigest()
    (root/"manifest.json").write_text(json.dumps(manifest))

def rewrite_manifest(root:Path, manifest:dict):
    manifest.pop("completeSnapshotChecksum",None)
    manifest["completeSnapshotChecksum"]=snapshot.hashlib.sha256(snapshot.canonical_json(manifest).encode()).hexdigest()
    (root/"manifest.json").write_text(json.dumps(manifest))

class AuthoringSnapshotTests(unittest.TestCase):
    def test_verify_accepts_checksums_and_rejects_secrets(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory); minimal_snapshot(root); snapshot.verify_snapshot(root,"a"*64)
            path=root/"ai"/"tables"/"entity.ndjson"; path.write_text('{"api_key":"forbidden"}\n')
            manifest=json.loads((root/"manifest.json").read_text()); manifest["files"]["ai/tables/entity.ndjson"]["sha256"]=snapshot.file_sha(path); rewrite_manifest(root,manifest)
            with self.assertRaises(SystemExit): snapshot.verify_snapshot(root)

    def test_dependency_order_places_parent_before_child(self):
        schema={"tables":[{"table":"child"},{"table":"parent"}],"foreignKeys":[{"table":"child","referencedTable":"parent"}]}
        self.assertEqual(snapshot.dependency_order(schema),["parent","child"])

    def test_plan_classifies_reuse_conflict_and_idempotency(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory); source=root/"source"; target=root/"target"; minimal_snapshot(source); minimal_snapshot(target)
            (target/"ai"/"tables"/"entity.ndjson").write_text(json.dumps({"id":"1","value":"different"})+"\n")
            report=snapshot.plan(source,target,root/"plan.json")
            self.assertEqual(report["classifications"]["content"]["entity"]["REUSE_IDENTICAL"],1)
            self.assertEqual(report["classifications"]["ai"]["entity"]["CONFLICT_IMMUTABLE"],1)
            self.assertEqual(report["idempotency"]["secondRunInsert"],0)

    def test_runtime_rules_preserve_unknown_exposure(self):
        self.assertEqual(snapshot.RUNTIME_RULES["ai_paid_request_accounting"]["futureState"],"RECONCILIATION_PENDING")
        self.assertNotIn("actual_cost_usd",snapshot.EPHEMERAL_FIELDS["ai"]["ai_paid_request_accounting"])
        self.assertIn("heartbeat_at",snapshot.EPHEMERAL_FIELDS["ai"]["ai_paid_request_accounting"])

    def test_source_payload_diagnostic_distinguishes_normalizations(self):
        source={"content_text":"Rätts-\nväsendet  är öppet.","content_checksum":"a","file_checksum":"pdf"}
        target={"content_text":"Rättsväsendet är öppet.","content_checksum":"b","file_checksum":"pdf"}
        report=snapshot.source_payload_diagnostic(source,target)
        self.assertFalse(report["equalities"]["raw"])
        self.assertTrue(report["equalities"]["pdfLineBreak"])
        self.assertTrue(report["differenceTypes"]["lineBreakHyphenationOnly"])
        self.assertEqual(report["sourceDocumentChecksum"],report["targetDocumentChecksum"])

if __name__=="__main__": unittest.main()
