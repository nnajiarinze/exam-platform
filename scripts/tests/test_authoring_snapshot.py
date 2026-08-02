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

def replace_content_schema(root:Path, tables:list[dict]):
    schema={"tables":tables,"foreignKeys":[]}
    (root/"content"/"schema.json").write_text(json.dumps(schema))
    manifest=json.loads((root/"manifest.json").read_text())
    path=root/"content"/"schema.json"
    manifest["files"]["content/schema.json"]={"sha256":snapshot.file_sha(path),"bytes":path.stat().st_size}
    for data_path in (root/"content"/"tables").glob("*.ndjson"):
        relative=str(data_path.relative_to(root))
        manifest["files"][relative]={"sha256":snapshot.file_sha(data_path),"bytes":data_path.stat().st_size}
    rewrite_manifest(root,manifest)

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

    def test_runtime_normalization_is_exact_and_preserves_unknown_cost(self):
        row={"lifecycle_state":"RESERVED","actual_cost_usd":None,"unknown_exposure":True,"worker_id":"old"}
        normalized=snapshot.runtime_normalized_row("ai","ai_provider_attempt",row)
        self.assertEqual(normalized["lifecycle_state"],"RECOVERED_STALE")
        self.assertIsNone(normalized["actual_cost_usd"])
        self.assertTrue(normalized["unknown_exposure"])
        self.assertEqual(row["lifecycle_state"],"RESERVED")

    def test_import_omits_ephemeral_columns_so_database_defaults_apply(self):
        table={"columns":[{"name":"id"},{"name":"heartbeat_at"},{"name":"lease_expires_at"},{"name":"owner_worker_id"},{"name":"process_instance_id"},{"name":"actual_cost_usd"}]}
        statement=snapshot.import_insert_statement("ai","ai_paid_request_accounting",table)
        self.assertIn('("id","actual_cost_usd")',statement)
        self.assertNotIn('"heartbeat_at"',statement)
        self.assertNotIn('"lease_expires_at"',statement)
        self.assertNotIn('"owner_worker_id"',statement)
        self.assertNotIn('"process_instance_id"',statement)

    def test_planner_treats_approved_runtime_normalization_as_idempotent_reuse(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory); source=root/"source"; target=root/"target"; minimal_snapshot(source); minimal_snapshot(target)
            table={"table":"ai_provider_attempt","columns":[],"primaryKey":["id"],"uniqueKeys":[]}
            (source/"ai"/"tables"/"ai_provider_attempt.ndjson").write_text(json.dumps({"id":"attempt","lifecycle_state":"RESERVED","actual_cost_usd":None})+"\n")
            (target/"ai"/"tables"/"ai_provider_attempt.ndjson").write_text(json.dumps({"id":"attempt","lifecycle_state":"RECOVERED_STALE","actual_cost_usd":None})+"\n")
            for snapshot_root in (source,target): replace_content_schema(snapshot_root,json.loads((snapshot_root/"content"/"schema.json").read_text())["tables"])
            for snapshot_root in (source,target):
                (snapshot_root/"ai"/"schema.json").write_text(json.dumps({"tables":[table],"foreignKeys":[]})); manifest=json.loads((snapshot_root/"manifest.json").read_text()); path=snapshot_root/"ai"/"schema.json"; data=snapshot_root/"ai"/"tables"/"ai_provider_attempt.ndjson"; manifest["files"]["ai/schema.json"]={"sha256":snapshot.file_sha(path),"bytes":path.stat().st_size}; manifest["files"]["ai/tables/ai_provider_attempt.ndjson"]={"sha256":snapshot.file_sha(data),"bytes":data.stat().st_size}; rewrite_manifest(snapshot_root,manifest)
            report=snapshot.plan(source,target,root/"plan.json")
            self.assertEqual(report["classifications"]["ai"]["ai_provider_attempt"]["REUSE_IDENTICAL"],1)
            self.assertEqual(report["conflicts"],[])

    def test_source_payload_diagnostic_distinguishes_normalizations(self):
        source={"content_text":"Rätts-\nväsendet  är öppet.","content_checksum":"a","file_checksum":"pdf"}
        target={"content_text":"Rättsväsendet är öppet.","content_checksum":"b","file_checksum":"pdf"}
        report=snapshot.source_payload_diagnostic(source,target)
        self.assertFalse(report["equalities"]["raw"])
        self.assertTrue(report["equalities"]["pdfLineBreak"])
        self.assertTrue(report["differenceTypes"]["lineBreakHyphenationOnly"])
        self.assertEqual(report["sourceDocumentChecksum"],report["targetDocumentChecksum"])

    def test_reconciled_divergent_source_uuid_reuses_only_exact_recorded_alias(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory); source=root/"source"; target=root/"target"
            minimal_snapshot(source); minimal_snapshot(target)
            source_ref={"id":"shared","content_text":"source-v2","content_checksum":"a"*64}
            target_ref={"id":"shared","content_text":"source-v1","content_checksum":"b"*64}
            reconciliation={"id":"reconciliation","historical_shared_id":"shared","local_payload_checksum":"a"*64,"hosted_payload_checksum":"b"*64}
            payload={"id":"payload","materialized_source_reference_id":"shared","payload_role":"CANONICAL"}
            tables=[
                {"table":"source_reference","columns":[],"primaryKey":["id"],"uniqueKeys":[]},
                {"table":"source_payload_revision","columns":[],"primaryKey":["id"],"uniqueKeys":[]},
                {"table":"source_payload_identity_reconciliation","columns":[],"primaryKey":["id"],"uniqueKeys":[]},
            ]
            for snapshot_root,ref in ((source,source_ref),(target,target_ref)):
                (snapshot_root/"content"/"tables"/"source_reference.ndjson").write_text(json.dumps(ref)+"\n")
                (snapshot_root/"content"/"tables"/"source_payload_revision.ndjson").write_text((json.dumps(payload)+"\n") if snapshot_root==source else "")
                (snapshot_root/"content"/"tables"/"source_payload_identity_reconciliation.ndjson").write_text((json.dumps(reconciliation)+"\n") if snapshot_root==source else "")
                replace_content_schema(snapshot_root,tables)
            report=snapshot.plan(source,target,root/"plan.json")
            self.assertEqual(report["classifications"]["content"]["source_reference"]["REUSE_RECONCILED_ALIAS"],1)
            self.assertEqual(report["conflicts"],[])

    def test_unreconciled_divergent_source_uuid_remains_immutable_conflict(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory); source=root/"source"; target=root/"target"
            minimal_snapshot(source); minimal_snapshot(target)
            table={"table":"source_reference","columns":[],"primaryKey":["id"],"uniqueKeys":[]}
            for snapshot_root,text in ((source,"source-v2"),(target,"source-v1")):
                (snapshot_root/"content"/"tables"/"source_reference.ndjson").write_text(json.dumps({"id":"shared","content_text":text,"content_checksum":text})+"\n")
                replace_content_schema(snapshot_root,[table])
            report=snapshot.plan(source,target,root/"plan.json")
            self.assertEqual(report["classifications"]["content"]["source_reference"]["CONFLICT_IMMUTABLE"],1)
            self.assertTrue(report["blocking"])

if __name__=="__main__": unittest.main()
