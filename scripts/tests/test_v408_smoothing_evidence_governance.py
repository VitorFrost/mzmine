#!/usr/bin/env python3

from __future__ import annotations

import json
import unittest
from pathlib import Path

REPOSITORY = Path(__file__).resolve().parents[2]
EVIDENCE_PATH = REPOSITORY / "datasets/parity/v408_smoothing_accepted_evidence.json"
SMOOTHING_INVENTORY_PATH = REPOSITORY / "datasets/parity/v408_smoothing_inventory.json"
GLOBAL_INVENTORY_PATH = REPOSITORY / "datasets/parity/mzmine_v408_lcms_core_inventory.json"
PARITY_DOC_PATH = REPOSITORY / "docs/public_validation/V408_SMOOTHING_PARITY.md"

RUN_ID = 31814860815
ARTIFACT_ID = 9224679187
ARTIFACT_ZIP_SHA = "676191a22491899832d7426eef2ed2ca1b4ae29fd03c88c38a8a7d7b05fe98c9"
CANDIDATE_COMMIT = "56393a7571681bd1fcf5397eb48a1634398ff9c7"
ORACLE_COMMIT = "8029f930d28c0447f0acf2bcabef0a79865ad434"
ADAP_SHA = "a6275eb15ce967e999374818803cc8b41c77a3e916d9f5196572caaf6e7d57fa"
SMOOTHING_SHA = "1c8fb4b82356facdbff4b88174990dcdf307b665df5ddddd30363cde471ec971"


class V408SmoothingEvidenceGovernanceTest(unittest.TestCase):

  @classmethod
  def setUpClass(cls) -> None:
    cls.evidence = json.loads(EVIDENCE_PATH.read_text(encoding="utf-8"))
    cls.smoothing_inventory = json.loads(
        SMOOTHING_INVENTORY_PATH.read_text(encoding="utf-8")
    )
    cls.global_inventory = json.loads(
        GLOBAL_INVENTORY_PATH.read_text(encoding="utf-8")
    )
    cls.parity_doc = PARITY_DOC_PATH.read_text(encoding="utf-8")

  @staticmethod
  def module_by_id(inventory: dict, module_id: str) -> dict:
    return next(module for module in inventory["modules"] if module["id"] == module_id)

  def test_accepted_artifact_record_is_frozen(self) -> None:
    evidence = self.evidence
    self.assertEqual(RUN_ID, evidence["workflow_run_id"])
    self.assertEqual(ARTIFACT_ID, evidence["artifact"]["id"])
    self.assertEqual(ARTIFACT_ZIP_SHA, evidence["artifact"]["zip_sha256"])
    self.assertEqual(CANDIDATE_COMMIT, evidence["candidate_commit"])
    self.assertEqual(ORACLE_COMMIT, evidence["oracle_commit"])
    self.assertEqual(517, evidence["adap_input_feature_count"])
    self.assertEqual(ADAP_SHA, evidence["adap_input_records_sha256"])
    self.assertEqual(517, evidence["candidate_output_feature_count"])
    self.assertEqual(517, evidence["oracle_output_feature_count"])
    self.assertEqual(SMOOTHING_SHA, evidence["candidate_records_sha256"])
    self.assertEqual(SMOOTHING_SHA, evidence["oracle_records_sha256"])
    self.assertTrue(evidence["records_equal"])
    self.assertIsNone(evidence["first_mismatch"])
    self.assertFalse(evidence["numerical_tolerance_applied"])

  def test_specific_inventory_matches_artifact_record(self) -> None:
    inventory = self.smoothing_inventory
    self.assertEqual("Equivalent", inventory["classification"])
    self.assertEqual(1, inventory["parameter_set_version"])
    self.assertEqual("complete", inventory["direct_gate"]["status"])
    self.assertEqual(
        "datasets/parity/v408_smoothing_accepted_evidence.json",
        inventory["direct_gate"]["accepted_evidence"],
    )
    direct = inventory["direct_evidence"]
    self.assertEqual(RUN_ID, direct["workflow_run_id"])
    self.assertEqual(ARTIFACT_ID, direct["artifact_id"])
    self.assertEqual(ARTIFACT_ZIP_SHA, direct["artifact_zip_sha256"])
    self.assertEqual(CANDIDATE_COMMIT, direct["candidate_commit"])
    self.assertEqual(ADAP_SHA, direct["adap_input_records_sha256"])
    self.assertEqual(SMOOTHING_SHA, direct["candidate_records_sha256"])
    self.assertEqual(SMOOTHING_SHA, direct["oracle_records_sha256"])
    self.assertTrue(direct["records_equal"])
    self.assertIsNone(direct["first_mismatch"])
    self.assertFalse(direct["numerical_tolerance_applied"])

  def test_global_inventory_promotes_only_smoothing_boundary(self) -> None:
    smoothing = self.module_by_id(self.global_inventory, "smoothing")
    resolver = self.module_by_id(self.global_inventory, "minimum-search-resolver")
    self.assertEqual("Equivalent", smoothing["classification"])
    self.assertEqual(1, smoothing["parameter_versions"]["oracle_408"])
    self.assertEqual("verified", smoothing["parameter_mapping_status"])
    self.assertTrue(smoothing["evidence"]["direct_differential_complete"])
    self.assertEqual("complete", smoothing["gate"]["status"])
    self.assertEqual("Adapted", resolver["classification"])
    self.assertFalse(resolver["evidence"]["direct_differential_complete"])

  def test_human_parity_record_points_back_to_machine_evidence(self) -> None:
    doc = self.parity_doc
    self.assertIn("datasets/parity/v408_smoothing_accepted_evidence.json", doc)
    self.assertIn(f"`{RUN_ID}`", doc)
    self.assertIn(f"`{ARTIFACT_ID}`", doc)
    self.assertIn(ARTIFACT_ZIP_SHA, doc)
    self.assertIn(SMOOTHING_SHA, doc)
    self.assertIn("F-046", doc)
    self.assertIn("machine-readable", doc)


if __name__ == "__main__":
  unittest.main()
